import http from 'k6/http';
import { group, sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 크리에이터가 미리 등록해둔 상품을, 매 반복마다 새로 가입하는 고객이 장바구니에 담는 흐름
 * 엔드포인트: POST /api/v1/cart/items
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 8 tx/s (회원가입+로그인+장바구니 담기 전체 흐름 완주 기준)
 * 목표 P95: 800ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 매 반복 회원가입이 끼어 있어 단순 조회보다 느리고, 담기 자체는 조회 대비 호출 빈도가 낮은 액션이라 목표를 낮게 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '40s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
    iterations: ['rate>=8'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'cart', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 장바구니 테스트 상품');
  return { skuId };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-cart-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');

  let signupOk = false;
  group('회원가입', () => {
    const signupRes = http.post(
      `${config.baseUrl}/api/v1/auth/signup/customer`,
      JSON.stringify({
        email,
        password,
        nickname: `k6cart${unique}`,
        phone: `010-${phoneDigits.slice(0, 4)}-${phoneDigits.slice(4)}`,
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

  group('장바구니_담기', () => {
    const res = http.post(
      `${config.baseUrl}/api/v1/cart/items`,
      JSON.stringify({ skuId: data.skuId, quantity: 1 }),
      authHeaders(token),
    );
    checkStatus(res, 200);
  });

  sleep(1);
}
