import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 주문 내역에서 특정 주문의 상세 정보를 조회하는 흐름
 * 엔드포인트: GET /api/v1/orders/{orderNumber}
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 20 req/s
 * 목표 P95: 300ms
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
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=20'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'orderdetail', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 주문 상세 테스트 상품');

  const customerUnique = `${Date.now()}`;
  const email = `k6-test-orderdetail-${customerUnique}@example.com`;
  const password = 'Passw0rd!';
  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6orderdetail${customerUnique}`,
      phone: `010-${customerUnique.slice(-8, -4)}-${customerUnique.slice(-4)}`,
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
  const cartItemId = cartRes.json().data[0].cartId;

  const headers = authHeaders(token);
  headers.headers['Idempotency-Key'] = `k6-test-setup-${unique}`;
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
    headers,
  );
  if (orderRes.status !== 201) {
    throw new Error(`setup 실패 - 주문 생성 status=${orderRes.status} body=${orderRes.body}`);
  }
  const orderNumber = orderRes.json().data.orderNumber;

  return { token, orderNumber };
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/orders/${data.orderNumber}`,
    authHeaders(data.token),
  );
  checkStatus(res, 200);
  sleep(1);
}
