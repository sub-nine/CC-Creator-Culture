import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 알림함에서 전체 읽음 처리 버튼을 눌러 안 읽은 알림을 한 번에 읽음 처리하는 흐름
 * 엔드포인트: PATCH /api/v1/notifications/read-all (인증 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 13 req/s
 * 목표 P95: 400ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 20 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~16 req/s)보다 여유 있게 목표를 잡음. 반복 호출해도 안전(idempotent)해 별도 데이터 준비 없이 반복 실행함
 */
export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '40s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<400'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=13'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-readall-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6readall${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);
  return { token };
}

export default function (data) {
  const res = http.patch(`${config.baseUrl}/api/v1/notifications/read-all`, null, authHeaders(data.token));
  checkStatus(res, 200);
  sleep(1);
}
