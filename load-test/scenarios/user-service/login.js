import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login } from '../../lib/auth.js';

/**
 * 시나리오 설명: 이미 가입된 계정으로 반복 로그인하는 흐름만 따로 떼어 로그인 엔드포인트 자체의 처리량을 측정
 * 엔드포인트: POST /api/v1/auth/login (공개 엔드포인트)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 20 req/s
 * 목표 P95: 400ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 30 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~24 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 30 },
    { duration: '40s', target: 30 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<400'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=20'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-login-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6login${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  return { email, password };
}

export default function (data) {
  login(config.baseUrl, data.email, data.password);
  sleep(1);
}
