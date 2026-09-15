import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 크리에이터가 자신의 상품에 걸린 주문 상품 목록을 조회하는 흐름
 * 엔드포인트: GET /api/v1/creator/order-items
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 13 req/s
 * 목표 P95: 300ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 20 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~16 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
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

  const creatorToken = signupApprovedCreator(config.baseUrl, 'orderitems', unique);
  const skuId = createProductWithSku(
    config.baseUrl,
    creatorToken,
    unique,
    'k6 크리에이터 주문 상품 조회 테스트 상품',
  );

  const password = 'Passw0rd!';
  const customerUnique = `${Date.now()}`;
  const customerEmail = `k6-test-orderitems-customer-${customerUnique}@example.com`;
  const customerSignupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email: customerEmail,
      password,
      nickname: `k6orderitemcx${customerUnique}`,
      phone: `010-${customerUnique.slice(-8, -4)}-${customerUnique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (customerSignupRes.status !== 201) {
    throw new Error(
      `setup 실패 - 고객 가입 status=${customerSignupRes.status} body=${customerSignupRes.body}`,
    );
  }
  const customerToken = login(config.baseUrl, customerEmail, password);

  const addRes = http.post(
    `${config.baseUrl}/api/v1/cart/items`,
    JSON.stringify({ skuId, quantity: 1 }),
    authHeaders(customerToken),
  );
  if (addRes.status !== 200) {
    throw new Error(`setup 실패 - 장바구니 담기 status=${addRes.status} body=${addRes.body}`);
  }
  const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(customerToken));
  const cartItemId = cartRes.json().data[0].cartId;

  const orderHeaders = authHeaders(customerToken);
  orderHeaders.headers['Idempotency-Key'] = `k6-test-setup-${unique}`;
  const orderRes = http.post(
    `${config.baseUrl}/api/v1/orders`,
    JSON.stringify({
      items: [{ cartItemId, userCouponId: null }],
      shippingAddress: {
        recipientName: '테스터',
        recipientPhone: '010-0000-0000',
        postalCode: '06236',
        addressLine1: '서울시 강남구 테헤란로',
        addressLine2: '101동 101호',
      },
    }),
    orderHeaders,
  );
  if (orderRes.status !== 201) {
    throw new Error(`setup 실패 - 주문 생성 status=${orderRes.status} body=${orderRes.body}`);
  }

  return { creatorToken };
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/creator/order-items`,
    authHeaders(data.creatorToken),
  );
  checkStatus(res, 200);
  sleep(1);
}
