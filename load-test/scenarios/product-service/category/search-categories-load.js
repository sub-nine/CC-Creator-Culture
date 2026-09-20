import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { loginAsMaster } from '../../../lib/creator.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 카테고리 탐색 화면에서 키워드로 카테고리를 검색하는 흐름. setup()에서
 *   키워드에 매칭되는 카테고리 20개 + 매칭 안 되는 노이즈 카테고리 30개(총 50개)를 미리
 *   만들어둬(시드 데이터), 검색 결과가 1건뿐인 비현실적인 조건이 아니라 여러 건 중
 *   필터링/페이징하는 실제 서비스에 가까운 조건을 재현한다.
 * 엔드포인트: GET /api/v1/categories?keyword= (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 20 req/s
 * 목표 P95: 250ms
 * 목표 P99: 500ms (P95의 2배 수준, 드문 꼬리 지연 허용 범위 확인용)
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유:
 *   - VUser 30명: DAU 5,000명 가정 시 Little's Law 기반 피크 동시접속자 추정치(~208명)
 *       - 카테고리 탐색 비중 15%로 산출
 *   - 목표 TPS 20: sleep(1)이라 VU당 최대 처리량이 초당 1건
 *       - 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~24 req/s)보다 여유 있게 설정
 *   - Ramp-up/down 30초/10초
 *       - 실제 시간축 아님, 반복 테스트를 위한 완만한 증감 패턴만 압축 재현
 */
export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '10s', target: 0 },
  ],
  // setup()의 시드 생성 요청도 http_req_duration 등에 합산되므로, endpoint:search 태그로
  // 부하 구간의 검색 요청만 걸러서 threshold를 검증한다
  thresholds: {
    'http_req_duration{endpoint:search}': ['p(95)<250', 'p(99)<500'],
    'http_req_failed{endpoint:search}': ['rate<0.01'],
    'http_reqs{endpoint:search}': ['rate>=20'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-categories-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6categories${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);

  const masterToken = loginAsMaster(config.baseUrl);
  const keyword = `k6search${unique.slice(-6)}`;

  // 검색 결과가 1건뿐이면 실제 서비스처럼 여러 건 중 필터링/페이징하는 부하를 재현할 수 없어
  // 키워드에 매칭되는 카테고리와 매칭 안 되는 노이즈 카테고리를 함께 심어 테이블 크기를 키운다
  const MATCHING_COUNT = 20;
  const NOISE_COUNT = 30;

  for (let i = 0; i < MATCHING_COUNT; i++) {
    seedCategory(masterToken, `${keyword}-${i}`);
  }
  for (let i = 0; i < NOISE_COUNT; i++) {
    seedCategory(masterToken, `k6noise${unique.slice(-6)}-${i}`);
  }

  return { token, keyword };
}

function seedCategory(masterToken, name) {
  const res = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name, description: 'k6 카테고리 검색 부하 테스트용 시드 카테고리' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (res.status !== 201) {
    throw new Error(`setup 실패 - 시드 카테고리 생성 status=${res.status} body=${res.body}`);
  }
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/categories?keyword=${encodeURIComponent(data.keyword)}`,
    { ...authHeaders(data.token), tags: { endpoint: 'search' } },
  );
  checkStatus(res, 200);
  sleep(1);
}
