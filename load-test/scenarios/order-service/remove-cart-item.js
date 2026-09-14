import http from 'k6/http';
import { group, sleep } from 'k6';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 신규 고객이 담은 장바구니 상품을 곧바로 삭제하는 흐름 (마음이 바뀐 케이스)
 * 엔드포인트: POST /api/v1/cart/items -> POST /api/v1/cart/items/delete
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 20
 * 목표 TPS: 8 tx/s (회원가입+로그인+담기+삭제 전체 흐름 완주 기준)
 * 목표 P95: 900ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 매 반복 회원가입 + 담기 + 삭제 4단계를 거치므로 add-cart-item보다도 더 낮은 처리량/더 높은 지연을 목표로 잡음
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
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 장바구니 삭제 테스트 상품');
  return { skuId };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-cart-rm-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');

  let signupOk = false;
  group('회원가입', () => {
    const signupRes = http.post(
      `${config.baseUrl}/api/v1/auth/signup/customer`,
      JSON.stringify({
        email,
        password,
        nickname: `k6cartrm${unique}`,
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
    const res = http.post(
      `${config.baseUrl}/api/v1/cart/items`,
      JSON.stringify({ skuId: data.skuId, quantity: 1 }),
      authHeaders(token),
    );
    if (checkStatus(res, 200)) {
      const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(token));
      cartId = cartRes.json().data[0].cartId;
    }
  });
  if (!cartId) return;

  group('장바구니_삭제', () => {
    const res = http.post(
      `${config.baseUrl}/api/v1/cart/items/delete`,
      JSON.stringify({ cartIds: [cartId] }),
      authHeaders(token),
    );
    checkStatus(res, 200);
  });

  sleep(1);
}
