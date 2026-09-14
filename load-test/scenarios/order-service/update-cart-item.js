import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 장바구니에 담아둔 상품의 수량을 반복적으로 변경하는 흐름
 * 엔드포인트: PATCH /api/v1/cart/items/{cartId}
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 13 req/s
 * 목표 P95: 300ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 20 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~16 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '40s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=13'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'cart', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 수량 변경 테스트 상품');

  const email = `k6-test-cart-${unique}@example.com`;
  const password = 'Passw0rd!';
  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6cart${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 고객 가입 status=${signupRes.status} body=${signupRes.body}`);
  }
  const token = login(config.baseUrl, email, password);

  const addRes = http.post(
    `${config.baseUrl}/api/v1/cart/items`,
    JSON.stringify({ skuId, quantity: 1 }),
    authHeaders(token),
  );
  if (addRes.status !== 200) {
    throw new Error(`setup 실패 - 장바구니 담기 status=${addRes.status} body=${addRes.body}`);
  }

  const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(token));
  const cartId = cartRes.json().data[0].cartId;

  return { token, cartId };
}

export default function (data) {
  const quantity = Math.floor(Math.random() * 99) + 1;
  const res = http.patch(
    `${config.baseUrl}/api/v1/cart/items/${data.cartId}`,
    JSON.stringify({ quantity }),
    authHeaders(data.token),
  );
  checkStatus(res, 200);
  sleep(1);
}
