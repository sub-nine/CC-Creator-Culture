import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import config from '../../../config/index.js';

// 201 발급 성공과 409 품절 응답을 k6의 정상 HTTP 응답으로 처리한다.
http.setResponseCallback(http.expectedStatuses(201, 409));

/**
 * 선착순 쿠폰 동기 발급 스파이크 테스트
 *
 * 쿠폰 100개에 서로 다른 사용자 500명이 5초 동안 한 번씩 요청한다.
 *
 * 예상 결과:
 * - 발급 성공: 100건
 * - 품절: 400건
 * - 예상하지 않은 오류: 0건
 */

// 1. 환경변수와 기본 테스트 설정
// 실행할 때 값을 따로 전달하지 않으면 오른쪽의 기본값을 사용한다.
const COUPON_ID = __ENV.COUPON_ID || '01920000-0000-7000-8000-000000000001';
const REQUEST_COUNT = Number(__ENV.REQUEST_COUNT || '500');
const REQUEST_RATE = Number(__ENV.REQUEST_RATE || '100');
const DURATION = __ENV.DURATION || '5s';
const EXPECTED_SUCCESS = Number(__ENV.EXPECTED_SUCCESS || '100');
const EXPECTED_SOLD_OUT = Number(__ENV.EXPECTED_SOLD_OUT || '400');

// 2. 성공·품절·실패 건수와 각각의 응답시간을 따로 기록한다.
// 품절 응답은 보통 빠르기 때문에 성공 응답과 합치면 실제 발급 성능을 알기 어렵다.
const issueSuccess = new Counter('coupon_issue_success');
const issueSoldOut = new Counter('coupon_issue_sold_out');
const issueUnexpectedFailure = new Counter('coupon_issue_unexpected_failure');
const successDuration = new Trend('coupon_issue_success_duration', true);
const soldOutDuration = new Trend('coupon_issue_sold_out_duration', true);

// 3. CSV에서 사용자별 JWT를 한 번만 읽어 모든 가상 사용자가 함께 사용한다.
// 각 요청에는 서로 다른 사용자의 JWT가 배정된다.
const users = new SharedArray('coupon issue users', () => {
  const csv = open('../../../data/coupon-user.csv').trim();
  if (!csv) {
    return [];
  }

  return csv
    .split(/\r?\n/)
    .slice(1)
    .filter((line) => line.trim().length > 0)
    .map((line) => {
      const separator = line.indexOf(',');
      return {
        userId: line.slice(0, separator).trim(),
        token: line.slice(separator + 1).trim(),
      };
    });
});

// constant-arrival-rate: 사용자 수가 아니라 초당 요청 수를 기준으로 부하를 발생시킨다.
// preAllocatedVUs: 테스트 시작 전에 미리 준비해 두는 가상 사용자 수다.
// maxVUs: 요청 속도를 유지하기 위해 k6가 추가할 수 있는 최대 가상 사용자 수다.

export const options = {
  scenarios: {
    coupon_issue_spike: {
      executor: 'constant-arrival-rate',
      rate: REQUEST_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 500,
      gracefulStop: '10s',
    },
  },
  // 4. 테스트 통과 기준
  // 성공 요청의 95%는 500ms, 99%는 1초 안에 응답해야 한다.
  // 품절 요청의 95%는 200ms 안에 응답하고, 실행하지 못한 요청은 없어야 한다.
  thresholds: {
    coupon_issue_success: [`count==${EXPECTED_SUCCESS}`],
    coupon_issue_sold_out: [`count==${EXPECTED_SOLD_OUT}`],
    coupon_issue_unexpected_failure: ['count==0'],
    coupon_issue_success_duration: ['p(95)<500', 'p(99)<1000'],
    coupon_issue_sold_out_duration: ['p(95)<200'],
    dropped_iterations: ['count==0'],
  },
};

// 5. 요청을 보내기 전에 설정값과 사용자 토큰 개수가 충분한지 확인한다.
export function setup() {
  if (!Number.isInteger(REQUEST_COUNT) || REQUEST_COUNT <= 0) {
    throw new Error(`REQUEST_COUNT는 양의 정수여야 합니다: ${REQUEST_COUNT}`);
  }
  if (users.length < REQUEST_COUNT) {
    throw new Error(
      `사용자 토큰이 부족합니다: 필요=${REQUEST_COUNT}, 실제=${users.length}. ` +
      'generate-test-tokens.py를 먼저 실행하세요.',
    );
  }
}

// 서버 오류 응답에서 COUPON_0003과 같은 업무 오류 코드를 꺼낸다.
function readErrorCode(response) {
  try {
    return response.json('errorCode');
  } catch (_) {
    return undefined;
  }
}

// 6. 사용자 한 명이 쿠폰 발급을 한 번 요청한다.
export default function () {
  // 전체 테스트의 요청 순번으로 사용자를 선택하여 같은 JWT가 재사용되지 않게 한다.
  const index = exec.scenario.iterationInTest;
  if (index >= REQUEST_COUNT) {
    return;
  }

  const user = users[index];
  const response = http.post(
    `${config.baseUrl}/api/v1/coupons/${COUPON_ID}/issue`,
    null,
    {
      headers: {
        Authorization: `Bearer ${user.token}`,
        'Content-Type': 'application/json',
      },
      tags: { endpoint: 'coupon-issue' },
    },
  );

  const errorCode = readErrorCode(response);
  const success = response.status === 201;

  // 쿠폰 100개가 소진된 뒤 발생하는 품절 400건은 정상 결과다.
  // 409 응답 중 COUPON_0003만 정상 품절로 인정한다.
  // 중복 발급을 포함한 다른 4xx·5xx 응답은 예상하지 않은 실패로 기록한다.
  const soldOut = response.status === 409 && errorCode === 'COUPON_0003';

  if (success) {
    issueSuccess.add(1);
    successDuration.add(response.timings.duration);
  } else if (soldOut) {
    issueSoldOut.add(1);
    soldOutDuration.add(response.timings.duration);
  } else {
    issueUnexpectedFailure.add(1);
    console.error(
      `예상하지 않은 응답: userId=${user.userId} status=${response.status} ` +
      `errorCode=${errorCode} body=${response.body}`,
    );
  }

  check(response, {
    '발급 성공 또는 정상 품절': () => success || soldOut,
  });
}
