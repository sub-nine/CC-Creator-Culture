import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config';
import { login, authHeaders } from '../../../lib/auth.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 카테고리 상세 화면에서 소속 해시태그 목록을 훑어보는 흐름
 * 엔드포인트: GET /api/v1/categories/{categoryId}/hashtags (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 25
 * 목표 TPS: 16 req/s
 * 목표 P95: 300ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 25 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~20 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '30s', target: 25 },
    { duration: '1m', target: 25 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=16'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-cathashtags-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6cathash${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);

  const listRes = http.get(`${config.baseUrl}/api/v1/categories`, authHeaders(token));
  const categories = listRes.json().data.content;
  if (!categories || categories.length === 0) {
    throw new Error('setup 실패 - 조회할 카테고리 데이터가 없음 (최소 1개 카테고리 필요)');
  }

  return { token, categoryId: categories[0].id };
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/categories/${data.categoryId}/hashtags`,
    authHeaders(data.token),
  );
  checkStatus(res, 200);
  sleep(1);
}
