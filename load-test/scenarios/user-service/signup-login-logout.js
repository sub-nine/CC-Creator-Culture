import http from 'k6/http';
import { group, sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 신규 가입 직후 로그인해서 세션을 열고 로그아웃까지 마치는 전형적인 세션 라이프사이클
 * 엔드포인트: POST /api/v1/auth/signup/customer -> POST /api/v1/auth/login -> POST /api/v1/auth/logout
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 15
 * 목표 TPS: 5 tx/s (전체 3단계 흐름 완주 기준)
 * 목표 P95: 800ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 3단계 요청을 순서대로 거치므로 단일 조회보다 낮은 처리량/높은 지연을 목표로 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 15 },
    { duration: '40s', target: 15 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
    iterations: ['rate>=5'],
  },
};

export default function () {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');
  const phone = `010-${phoneDigits.slice(0, 4)}-${phoneDigits.slice(4)}`;

  let signupOk = false;
  group('회원가입', () => {
    const signupRes = http.post(
      `${config.baseUrl}/api/v1/auth/signup/customer`,
      JSON.stringify({
        email,
        password,
        nickname: `k6user${unique}`,
        phone,
        address: '서울시 강남구',
      }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    signupOk = checkStatus(signupRes, 201);
  });
  if (!signupOk) return;

  let token;
  group('로그인', () => {
    token = login(config.baseUrl, email, password);
  });
  if (!token) return;

  group('로그아웃', () => {
    const logoutRes = http.post(`${config.baseUrl}/api/v1/auth/logout`, null, authHeaders(token));
    checkStatus(logoutRes, 200);
  });

  sleep(1);
}
