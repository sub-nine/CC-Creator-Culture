import http from 'k6/http';
import { group, sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 주문 생성 직후 모의 결제(성공)까지 이어지는 구매 완결 흐름
 * 엔드포인트: POST /api/v1/cart/items -> POST /api/v1/orders -> POST /api/v1/orders/{orderNumber}/payments
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 10
 * 목표 TPS: 2 tx/s (회원가입+로그인+담기+주문 생성+결제 전체 흐름 완주 기준)
 * 목표 P95: 2000ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 주문 생성 뒤 결제까지 한 단계 더 거치는 가장 무거운 구매 흐름이라 create-order보다 낮은 목표 처리량을 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '40s', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
    iterations: ['rate>=2'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'pay', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 결제 테스트 상품');
  return { skuId };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-pay-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');

  let signupOk = false;
  group('회원가입', () => {
    const signupRes = http.post(
      `${config.baseUrl}/api/v1/auth/signup/customer`,
      JSON.stringify({
        email,
        password,
        nickname: `k6pay${unique}`,
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

  let cartItemId;
  group('장바구니_담기', () => {
    const addRes = http.post(
      `${config.baseUrl}/api/v1/cart/items`,
      JSON.stringify({ skuId: data.skuId, quantity: 1 }),
      authHeaders(token),
    );
    if (checkStatus(addRes, 200)) {
      const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(token));
      cartItemId = cartRes.json().data[0].cartId;
    }
  });
  if (!cartItemId) return;

  let orderNumber;
  group('주문_생성', () => {
    const headers = authHeaders(token);
    headers.headers['Idempotency-Key'] = `k6-test-create-${unique}`;
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
    if (checkStatus(orderRes, 201)) {
      orderNumber = orderRes.json().data.orderNumber;
    }
  });
  if (!orderNumber) return;

  group('결제', () => {
    const res = http.post(
      `${config.baseUrl}/api/v1/orders/${orderNumber}/payments`,
      JSON.stringify({ result: 'SUCCESS' }),
      authHeaders(token),
    );
    checkStatus(res, 200);
  });

  sleep(1);
}
