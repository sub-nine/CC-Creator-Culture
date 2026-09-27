import http from 'k6/http';
import { sleep } from 'k6';
import exec from 'k6/execution';
import encoding from 'k6/encoding';
import { Counter, Rate, Trend } from 'k6/metrics';
import config from '../../config/index.js';
import { login, authHeaders } from '../../lib/auth.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';

/**
 * setup에서 크리에이터 승인, 재고 100만 개 상품, 고객 500명과 장바구니를 자동 생성한다.
 * 기존 load-test/scripts/up.sh로 시작한 로컬 스택의 테스트용 MASTER 계정을 사용한다.
 * 로컬: BASE_URL=http://localhost:8080 k6 run load-test/scenarios/order-service/order-payment-stress.js
 * Docker 실행 (Grafana 대시보드 실시간 연동, 저장소 루트에서 실행 권장):
 *   TESTID="$(date +%s)"
 *   docker compose -f load-test/docker-compose.yml run --rm --build \
 *     k6 run --out experimental-prometheus-rw --tag testid="$TESTID" \
 *     scenarios/order-service/order-payment-stress.js
 * Docker 실행 (콘솔 전용 단독 실행):
 *   docker compose -f load-test/docker-compose.yml run --rm --build \
 *     k6 run scenarios/order-service/order-payment-stress.js
 * SMOKE_TEST=1 (사전 1 VU 검증용):
 *   TESTID="$(date +%s)"
 *   docker compose -f load-test/docker-compose.yml run --rm \
 *     -e SMOKE_TEST=1 \
 *     k6 run --out experimental-prometheus-rw --tag testid="$TESTID" \
 *     scenarios/order-service/order-payment-stress.js
 * 각 SKU 수량은 1개다. 400 성공 요청/초는 구매 약 100건/초이며 유지 구간만 최소 90,000개가 필요하다.
 * 자동 준비한 재고 100만 개가 소진되면 재고 부족 응답도 오류로 집계한다.
 * 장바구니 요청은 구매 SLA에서 제외하지만 서버 부하와 VU 점유 시간에는 포함된다.
 * 실행 중 장바구니 준비 실패는 해당 반복만 종료하고 별도 실패율 0.1% 이하로 판정한다.
 * P95, P99와 에러율은 실패 응답도 포함한다. 유지 구간은 P95 200ms, P99 300ms 미만이어야 한다.
 */
const smoke = __ENV.SMOKE_TEST === '1';
const ramp = smoke ? 1 : 300;
const steady = smoke ? 3 : 900;
const down = smoke ? 1 : 120;
const vus = smoke ? 1 : 500;
const endpoints = ['create_order', 'pay_order', 'get_order'];
const requests = new Counter('purchase_requests');
const successes = new Counter('purchase_successes');
const duration = new Trend('purchase_duration', true);
const errors = new Rate('purchase_errors');
const completed = new Counter('purchase_completed');
const preparationFailed = new Rate('cart_preparation_failed');
const preparationFailures = new Counter('cart_preparation_failures');
const recoveryFailed = new Rate('payment_recovery_failed');
const preparationReasons = ['list_failed', 'add_failed', 'cleanup_timeout', 'invalid_item', 'unexpected'];
const thresholds = { cart_preparation_failed: [smoke ? 'rate==0' : 'rate<=0.001'] };
for (const reason of preparationReasons) {
  thresholds[`cart_preparation_failures{reason:${reason}}`] = ['count>=0'];
}
for (const scope of ['', 'phase:steady']) {
  for (const endpoint of ['', ...endpoints]) {
    const tags = [scope, endpoint && `endpoint:${endpoint}`].filter(Boolean).join(',');
    const suffix = tags ? `{${tags}}` : '';
    thresholds[`purchase_requests${suffix}`] = ['count>=0'];
    thresholds[`purchase_successes${suffix}`] = ['count>=0'];
    thresholds[`purchase_duration${suffix}`] = scope ? ['p(95)<200', 'p(99)<300'] : ['p(95)>=0'];
    thresholds[`purchase_errors${suffix}`] = [smoke ? 'rate==0' : scope ? 'rate<=0.001' : 'rate<=1'];
  }
}
thresholds['purchase_completed'] = [smoke ? 'count>=1' : 'count>=0'];
thresholds['purchase_completed{phase:steady}'] = ['count>=0'];
if (!smoke) thresholds['purchase_successes{phase:steady}'] = [`count>=${400 * steady}`];

export const options = {
  setupTimeout: '10m',
  scenarios: {
    purchase: {
      executor: 'ramping-vus', startVUs: 0,
      stages: [
        { duration: `${ramp}s`, target: vus },
        { duration: `${steady}s`, target: vus },
        { duration: `${down}s`, target: 0 },
      ],
      gracefulRampDown: '30s', gracefulStop: '30s',
    },
  },
  thresholds,
  summaryTrendStats: ['avg', 'p(95)', 'p(99)'],
  // 실제 주문번호가 URL 태그로 쌓이지 않도록 고정 name 태그만 사용한다.
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
};

export function setup() {
  const unique = `${Date.now()}`;
  const runId = `${unique}-${Math.random().toString(36).slice(2)}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'stress', unique);
  const skuId = createProductWithSku(config.baseUrl, creatorToken, unique, 'k6 구매 스트레스 상품');
  const password = 'Passw0rd!';
  const emails = [];
  console.log(`테스트 데이터 준비: 고객 ${vus}명, SKU 재고 1,000,000개`);

  // 가입을 먼저 끝낸 뒤 로그인해 준비 중 토큰 유효기간이 소진되는 시간을 줄인다.
  for (let i = 0; i < vus; i++) {
    const email = `k6-test-stress-${runId}-${i}@example.com`;
    const phone = `${unique.slice(-5)}${String(i).padStart(3, '0')}`;
    const response = http.post(`${config.baseUrl}/api/v1/auth/signup/customer`, JSON.stringify({
      email, password, nickname: `k6stress${unique}${i}`,
      phone: `010-${phone.slice(0, 4)}-${phone.slice(4)}`, address: '서울시 강남구',
    }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup_signup' }, timeout: '30s' });
    if (response.status !== 201) throw new Error(`setup 실패 - 고객 ${i + 1} 가입 HTTP ${response.status}`);
    emails.push(email);
    if ((i + 1) % 100 === 0) console.log(`고객 가입 ${i + 1}/${vus} 완료`);
  }

  const customers = emails.map((email) => {
    const token = login(config.baseUrl, email, password);
    if (!token) throw new Error('setup 실패 - 고객 로그인 실패');
    const customer = { token, skuIds: [skuId] };
    prepareCart(customer);
    return customer;
  });

  const subjects = new Set();

  for (const customer of customers) {
    let claims;
    try {
      claims = JSON.parse(encoding.b64decode(customer.token.split('.')[1], 'rawurl', 's'));
    } catch (_) { throw new Error('setup 실패 - 발급된 JWT 형식 오류'); }
    if (!claims.sub || subjects.has(claims.sub)) throw new Error('setup 실패 - 고객 식별자 누락 또는 중복');
    subjects.add(claims.sub);
    if (!Number.isFinite(claims.exp) || claims.exp * 1000 < Date.now() + (ramp + steady + down + 60) * 1000) {
      throw new Error('setup 실패 - 발급된 토큰의 남은 유효기간이 테스트 실행 시간보다 짧습니다.');
    }
  }
  console.log(`데이터 준비 완료: 고객 ${vus}명. 구매 부하 측정을 시작합니다.`);
  return { runId, customers };
}

let previousCartIds = [];
let pendingPayment = null;
let lastPreparationWarning = 0;

function phase() {
  const seconds = (Date.now() - exec.scenario.startTime) / 1000;
  return seconds >= ramp && seconds < ramp + steady ? 'steady' : 'ramp';
}

function bodyData(response) {
  try { return response.json().data; } catch (_) { return null; }
}

function params(token, name, timeout = '30s') {
  return { ...authHeaders(token), tags: { name }, timeout, redirects: 0 };
}

function failPreparation(message, reason) {
  const error = new Error(`장바구니 준비 실패: ${message} (VU ${exec.vu.idInTest})`);
  error.preparationReason = reason;
  throw error;
}

function cartItems(token, timeout = '30s') {
  const response = http.get(`${config.baseUrl}/api/v1/cart/items`, params(token, 'prepare_cart_list', timeout));
  const items = bodyData(response);
  if (response.status !== 200 || !Array.isArray(items)) failPreparation(`목록 응답 오류 HTTP ${response.status}, error_code=${response.error_code || 0}, error=${response.error || "없음"}`, 'list_failed');
  return items;
}

function prepareCart(customer) {
  const deadline = Date.now() + 30000;
  let items = cartItems(customer.token);
  while (items.some((item) => previousCartIds.includes(item.cartId))) {
    if (Date.now() >= deadline) failPreparation('이전 결제 항목 삭제 대기 30초 초과', 'cleanup_timeout');
    sleep(Math.min(1, (deadline - Date.now()) / 1000));
    if (Date.now() >= deadline) failPreparation('이전 결제 항목 삭제 대기 30초 초과', 'cleanup_timeout');
    items = cartItems(customer.token, `${deadline - Date.now()}ms`);
  }
  previousCartIds = [];
  return customer.skuIds.map((skuId) => {
    const existing = items.find((item) => item.skuId === skuId);
    if (existing) {
      if (!existing.cartId || existing.quantity !== 1) failPreparation('사전 장바구니 항목은 수량 1이어야 합니다.', 'invalid_item');
      return existing.cartId;
    }
    const response = http.post(`${config.baseUrl}/api/v1/cart/items`, JSON.stringify({ skuId, quantity: 1 }),
      params(customer.token, 'prepare_cart_add'));
    const cartId = bodyData(response);
    if (response.status !== 200 || typeof cartId !== 'string' || !cartId) failPreparation(`등록 응답 오류 HTTP ${response.status}, error_code=${response.error_code || 0}, error=${response.error || "없음"}`, 'add_failed');
    return cartId;
  });
}

function record(response, endpoint, expected, validate) {
  const data = bodyData(response);
  const ok = response.status === expected && !!data && !!validate(data);
  const tags = { endpoint, phase: phase() };
  requests.add(1, tags);
  successes.add(ok ? 1 : 0, tags);
  duration.add(response.timings.duration, tags);
  errors.add(!ok, tags);
  return ok ? data : null;
}

function recoverPayment(customer) {
  const { path, orderNumber, cartIds } = pendingPayment;
  const response = http.get(path, params(customer.token, 'recover_order'));
  const order = bodyData(response);
  if (response.status !== 200 || !order || order.orderNumber !== orderNumber) return false;

  if (['PAID', 'PROCESSING', 'COMPLETED'].includes(order.status)) {
    previousCartIds = cartIds;
  } else if (order.status === 'PENDING_PAYMENT') {
    // 같은 주문의 결제 재시도는 서버에서 중복 처리하지 않는다.
    const retry = http.post(`${path}/payments`, JSON.stringify({ result: 'SUCCESS' }),
      params(customer.token, 'recover_payment'));
    const paid = bodyData(retry);
    if (retry.status !== 200 || !paid || paid.status !== 'SUCCESS' || paid.orderNumber !== orderNumber) {
      return false;
    }
    previousCartIds = cartIds;
  } else if (!['FAILED', 'EXPIRED'].includes(order.status)) {
    return false;
  }
  pendingPayment = null;
  return true;
}

export default function (data) {
  const customer = data.customers[exec.vu.idInTest - 1];
  if (pendingPayment) {
    const recovered = recoverPayment(customer);
    recoveryFailed.add(!recovered);
    if (!recovered) {
      sleep(1);
      return;
    }
  }
  let cartIds;
  try {
    cartIds = prepareCart(customer);
    preparationFailed.add(false);
  } catch (error) {
    // 부하 중 준비 실패는 별도 SLA에 반영하고 현재 반복만 끝낸다. setup 실패는 그대로 중단된다.
    preparationFailed.add(true);
    preparationFailures.add(1, { reason: error.preparationReason || 'unexpected' });
    if (Date.now() - lastPreparationWarning >= 30000) {
      console.warn(error.message);
      lastPreparationWarning = Date.now();
    }
    sleep(1);
    return;
  }
  const createParams = params(customer.token, 'create_order');
  createParams.headers['Idempotency-Key'] = `${data.runId}-${exec.vu.idInTest}-${exec.vu.iterationInScenario}`;
  const created = record(http.post(`${config.baseUrl}/api/v1/orders`, JSON.stringify({
    items: cartIds.map((cartItemId) => ({ cartItemId, userCouponId: null })),
    shippingAddress: {
      recipientName: '부하테스터', recipientPhone: '010-0000-0000', postalCode: '06236',
      addressLine1: '서울시 강남구 테헤란로', addressLine2: '101호',
    },
  }), createParams), 'create_order', 201, (body) => typeof body.orderNumber === 'string' && body.orderNumber.length > 0);
  if (!created) return;
  const path = `${config.baseUrl}/api/v1/orders/${encodeURIComponent(created.orderNumber)}`;
  const paid = record(http.post(`${path}/payments`, JSON.stringify({ result: 'SUCCESS' }),
    params(customer.token, 'pay_order')), 'pay_order', 200,
  (body) => body.status === 'SUCCESS' && body.orderNumber === created.orderNumber);
  if (!paid) {
    pendingPayment = { path, orderNumber: created.orderNumber, cartIds };
    return;
  }
  previousCartIds = cartIds;
  let allValid = true;
  for (let i = 0; i < 2; i++) {
    const detail = record(http.get(path, params(customer.token, 'get_order')), 'get_order', 200,
      (body) => body.orderNumber === created.orderNumber && body.status === 'PAID');
    allValid = !!detail && allValid;
  }
  completed.add(allValid ? 1 : 0, { phase: phase() });
}
