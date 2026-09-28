import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { authHeaders, login } from '../../lib/auth.js';
import { checkStatus } from '../../lib/checks.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * 시나리오 설명: 장바구니 조회 요청을 단계적으로 늘려 처리 한계와 병목 구간을 확인하는 흐름
 * 엔드포인트: GET /api/v1/cart/items
 * 테스트 유형: 스트레스 테스트
 * 최대 VUser: 1,000명
 * TPS 판단 기준: VUser 증가 대비 처리량 증가 둔화 또는 정체
 * 목표 P95: 500ms
 * 목표 P99: 1,000ms
 * 허용 에러: 0.1% 미만
 * 총 테스트 시간: 15분 (setup 제외)
 *
 * 부하:
 * - 0 → 100 VU (1분)
 * - 100 VU 유지 (1분)
 * - 100 → 300 VU (1분)
 * - 300 VU 유지 (2분)
 * - 300 → 500 VU (1분)
 * - 500 VU 유지 (2분)
 * - 500 → 750 VU (1분)
 * - 750 VU 유지 (2분)
 * - 750 → 1,000 VU (1분)
 * - 1,000 VU 유지 (2분)
 * - 1,000 → 0 VU (1분)
 */
export const options = {
  setupTimeout: '10m',

  stages: [
    { duration: '1m', target: 100 },
    { duration: '1m', target: 100 },
    { duration: '1m', target: 300 },
    { duration: '2m', target: 300 },
    { duration: '1m', target: 500 },
    { duration: '2m', target: 500 },
    { duration: '1m', target: 750 },
    { duration: '2m', target: 750 },
    { duration: '1m', target: 1000 },
    { duration: '2m', target: 1000 },
    { duration: '1m', target: 0 },
  ],

  thresholds: {
    'http_req_duration{operation:get_cart_items}': [
      'p(95)<500',
      'p(99)<1000',
    ],
    'http_req_failed{operation:get_cart_items}': [
      'rate<0.001',
    ],
  },
};

const CUSTOMER_COUNT = 50;
const CART_SIZE = 10;

const TEST_PASSWORD = 'Passw0rd!';

function customerEmail(index) {
  return `k6-test-cart50-${index}@example.com`;
}

function customerNickname(index) {
  return `k6cart50${index}`;
}

function customerPhone(index) {
  return `010-9000-${String(index).padStart(4, '0')}`;
}

function loginExistingCustomers() {
  const customers = [];

  for (let index = 0; index < CUSTOMER_COUNT; index++) {
    const email = customerEmail(index);

    const token = login(
        config.baseUrl,
        email,
        TEST_PASSWORD,
    );

    if (!token) {
      throw new Error(
          `테스트 계정 로그인 실패 index=${index} email=${email}`,
      );
    }

    customers.push({ token });

    if ((index + 1) % 10 === 0) {
      console.log(
          `CART_REUSE progress=${index + 1}/${CUSTOMER_COUNT}`,
      );
    }
  }

  console.log(
      `CART_REUSE completed customers=${customers.length}`,
  );

  return customers;
}

function createTestData() {
  const runId = `${Date.now()}`;

  const creatorToken = signupApprovedCreator(
      config.baseUrl,
      'cart50',
      runId,
  );

  const skuIds = [];

  for (let index = 0; index < CART_SIZE; index++) {
    const skuId = createProductWithSku(
        config.baseUrl,
        creatorToken,
        `${runId}-${index}`,
        `k6 장바구니 부하 테스트 상품 ${index}`,
    );

    skuIds.push(skuId);
  }

  console.log(
      `CART_SETUP products=${skuIds.length}`,
  );

  const customers = [];

  for (let index = 0; index < CUSTOMER_COUNT; index++) {
    const email = customerEmail(index);

    const signup = http.post(
        `${config.baseUrl}/api/v1/auth/signup/customer`,
        JSON.stringify({
          email,
          password: TEST_PASSWORD,
          nickname: customerNickname(index),
          phone: customerPhone(index),
          address: '서울시 강남구',
        }),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        },
    );

    if (signup.status !== 201) {
      throw new Error(
          `setup 고객 가입 실패 ` +
          `index=${index} ` +
          `email=${email} ` +
          `status=${signup.status} ` +
          `body=${signup.body}`,
      );
    }

    const token = login(
        config.baseUrl,
        email,
        TEST_PASSWORD,
    );

    if (!token) {
      throw new Error(
          `setup 고객 로그인 실패 index=${index} email=${email}`,
      );
    }

    for (const skuId of skuIds) {
      const add = http.post(
          `${config.baseUrl}/api/v1/cart/items`,
          JSON.stringify({
            skuId,
            quantity: 1,
          }),
          authHeaders(token),
      );

      if (add.status !== 200) {
        throw new Error(
            `setup 장바구니 등록 실패 ` +
            `index=${index} ` +
            `skuId=${skuId} ` +
            `status=${add.status} ` +
            `body=${add.body}`,
        );
      }
    }

    customers.push({ token });

    if ((index + 1) % 10 === 0) {
      console.log(
          `CART_SETUP progress=${index + 1}/${CUSTOMER_COUNT}`,
      );
    }
  }

  console.log(
      `CART_SETUP completed ` +
      `customers=${customers.length} ` +
      `cartItems=${customers.length * CART_SIZE}`,
  );

  return customers;
}

export function setup() {
  if (__ENV.REUSE_ACCOUNTS === 'true') {
    console.log('CART_SETUP reuse mode');
    return loginExistingCustomers();
  }

  console.log('CART_SETUP create mode');
  return createTestData();
}

export default function (customers) {
  const customer =
      customers[(__VU - 1) % customers.length];

  const res = http.get(
      `${config.baseUrl}/api/v1/cart/items`,
      {
        ...authHeaders(customer.token),

        tags: {
          name: 'GET /api/v1/cart/items',
          operation: 'get_cart_items',
        },
      },
  );

  checkStatus(res, 200);

  sleep(1);
}