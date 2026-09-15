import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 헤더/네비게이션의 안 읽은 알림 배지를 위해 자주 폴링될 것으로 예상되는 미확인 알림 수 조회
 * 엔드포인트: GET /api/v1/notifications/unread-count (인증 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 40
 * 목표 TPS: 27 req/s
 * 목표 P95: 200ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 40 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~32 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '30s', target: 40 },
    { duration: '1m', target: 40 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=27'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-unreadcount-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6unread${unique}`,
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
  const res = http.get(`${config.baseUrl}/api/v1/notifications/unread-count`, authHeaders(data.token));
  checkStatus(res, 200);
  sleep(1);
}
