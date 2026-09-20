import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { loginAsMaster } from '../../../lib/creator.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 카테고리 검색 결과에서 하나를 선택해 상세 정보를 확인하는 흐름. setup()에서
 *   조회 대상 카테고리를 직접 만들어둬(시드 데이터), DB에 카테고리가 미리 있어야 한다는
 *   외부 의존성 없이 항상 실행 가능하게 한다.
 * 엔드포인트: GET /api/v1/categories/{categoryId} (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 20 req/s
 * 목표 P95: 250ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 30 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~24 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<250'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=20'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-categorydetail-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6catdetail${unique}`,
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
  const categoryName = `k6catdetail${unique.slice(-6)}`;
  const categoryRes = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name: categoryName, description: 'k6 카테고리 상세 조회 부하 테스트용 시드 카테고리' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (categoryRes.status !== 201) {
    throw new Error(`setup 실패 - 시드 카테고리 생성 status=${categoryRes.status} body=${categoryRes.body}`);
  }

  return { token, categoryId: categoryRes.json().data };
}

export default function (data) {
  const res = http.get(`${config.baseUrl}/api/v1/categories/${data.categoryId}`, authHeaders(data.token));
  checkStatus(res, 200);
  sleep(1);
}
