import http from 'k6/http';
import { group, sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 신규 고객이 장바구니에 담은 상품의 수량을 바로 변경하는 흐름
 * 엔드포인트: POST /api/v1/cart/items -> PATCH /api/v1/cart/items/{cartId}
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 8 tx/s (회원가입+로그인+담기+수량변경 전체 흐름 완주 기준)
 * 목표 P95: 900ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 새 고객이 자기 장바구니만 수정하도록 해서(이전엔 VU 전체가 같은 cartId를 동시에 PATCH해 DB 락 대기로 tail latency가 튀었음), add-cart-item과 동일한 4단계 흐름 기준으로 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '40s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<900'],
    http_req_failed: ['rate<0.01'],
    iterations: ['rate>=8'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'cart', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 수량 변경 테스트 상품');
  return { skuId };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-cart-upd-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');

  let signupOk = false;
  group('회원가입', () => {
    const signupRes = http.post(
      `${config.baseUrl}/api/v1/auth/signup/customer`,
      JSON.stringify({
        email,
        password,
        nickname: `k6cartupd${unique}`,
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

  let cartId;
  group('장바구니_담기', () => {
    const addRes = http.post(
      `${config.baseUrl}/api/v1/cart/items`,
      JSON.stringify({ skuId: data.skuId, quantity: 1 }),
      authHeaders(token),
    );
    if (checkStatus(addRes, 200)) {
      const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(token));
      cartId = cartRes.json().data[0].cartId;
    }
  });
  if (!cartId) return;

  group('수량_변경', () => {
    const quantity = Math.floor(Math.random() * 99) + 1;
    const res = http.patch(
      `${config.baseUrl}/api/v1/cart/items/${cartId}`,
      JSON.stringify({ quantity }),
      authHeaders(token),
    );
    checkStatus(res, 200);
  });

  sleep(1);
}
