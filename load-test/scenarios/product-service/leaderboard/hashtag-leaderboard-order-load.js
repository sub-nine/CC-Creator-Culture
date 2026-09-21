import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { signupApprovedCreator } from '../../../lib/creator.js';
import { createProductWithSku } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 메인 화면의 인기 해시태그 리더보드를 조회하는 흐름. 리더보드가 비어있으면
 *   실제 집계 비용 없이 항상 공짜로 통과해버리므로, setup()에서 해시태그를 단 상품 10개를
 *   등록하고 상품마다 다른 수량으로 주문·결제를 완료해 순위가 실제로 갈리는 조건을 만든다.
 *   (해시태그는 상품 등록 시 HashtagProduct 링크가 바로 생겨서 admin 카테고리 연결 단계가
 *   필요 없다) 주문 결제 점수는 OrderPaidEventConsumer가 Kafka 메시지 수신 즉시 동기
 *   반영하므로(product-view 시나리오의 ProductViewCountScheduler 매분 배치와 달리 배치 대기가
 *   없음), 반영 대기 시간을 짧게 잡는다.
 * 엔드포인트: GET /api/v1/leaderboards/hashtags (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 25
 * 목표 TPS: 8 req/s 이상 (병목 판정용 아님 - sanity check, closed model 특성상 TPS로는 병목을 못 잡음)
 * 목표 P95: 100ms 미만 (실측 기준 재산정)
 * 목표 P99: 250ms 미만 (실측 기준 재산정)
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유:
 *   - VUser 25명
 *       - 공통 가정: DAU 5,000명 · 활성 4시간 · 평균 세션 5분 기준 Little's Law로 평균 동시접속자 104명, 피크 2배 208명
 *       - 리더보드는 메인 화면 진입 시 클릭 없이 바로 노출되는 위젯이라 노출 비율 12%로 가정 (208 * 12% ~ 25)
 *   - 목표 TPS 8: closed model(sleep(1)+VU) 특성상 응답이 1초보다 훨씬 빠른 한 TPS는 서버 성능과
 *     무관하게 거의 항상 VU 수 근처로 나와 병목을 못 잡음 -
 *     로드 자체가 안 걸렸는지만 거르는 느슨한 sanity check로 낮춤
 *   - 목표 P95 100ms / P99 250ms: 프로덕션 SLA(캐싱 여부 무관 사용자 체감 기준)와 회귀 탐지(베이스라인
 *     대비 몇 배 느려지면 잡아내는 기준)를 절충
 *   - Ramp-up/down 30초/10초
 *       - 실제 시간축 아님, 반복 테스트를 위한 완만한 증감 패턴만 압축 재현
 * TODO: TPS를 진짜 병목 탐지 게이트로 쓰려면 arrival-rate(open model) executor로 전환 필요
 */
export const options = {
  stages: [
    { duration: '30s', target: 25 },
    { duration: '1m', target: 25 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:leaderboard}': ['p(95)<100', 'p(99)<250'],
    'http_req_failed{endpoint:leaderboard}': ['rate<0.01'],
    'http_reqs{endpoint:leaderboard}': ['rate>=8'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-hashtagleaderboardorder-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6hashlbo${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);

  const creatorToken = signupApprovedCreator(config.baseUrl, 'hashlboseed', unique);
  const PRODUCT_COUNT = 10;
  const skuIds = [];
  for (let i = 0; i < PRODUCT_COUNT; i++) {
    const hashtagName = `k6hlbo${unique.slice(-4)}${i}`;
    const skuId = createProductWithSku(config.baseUrl, creatorToken, `${unique}-${i}`, 'k6 해시태그 리더보드(주문) 시드 상품', [
      hashtagName,
    ]);
    skuIds.push(skuId);
  }

  // 상품마다 다른 수량으로 주문·결제를 완료해서(i번째 상품을 (i+1)개 주문) 순위 편차를 만든다
  skuIds.forEach((skuId, i) => {
    const addRes = http.post(
      `${config.baseUrl}/api/v1/cart/items`,
      JSON.stringify({ skuId, quantity: i + 1 }),
      authHeaders(token),
    );
    if (addRes.status !== 200) {
      throw new Error(`setup 실패 - 장바구니 담기 status=${addRes.status} body=${addRes.body}`);
    }
    const cartRes = http.get(`${config.baseUrl}/api/v1/cart/items`, authHeaders(token));
    const cartItemId = cartRes.json().data[0].cartId;

    const orderHeaders = authHeaders(token);
    orderHeaders.headers['Idempotency-Key'] = `k6-test-hashlbo-${unique}-${i}`;
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
    const orderNumber = orderRes.json().data.orderNumber;

    const payRes = http.post(
      `${config.baseUrl}/api/v1/orders/${orderNumber}/payments`,
      JSON.stringify({ result: 'SUCCESS' }),
      authHeaders(token),
    );
    if (payRes.status !== 200) {
      throw new Error(`setup 실패 - 결제 status=${payRes.status} body=${payRes.body}`);
    }
  });

  // 주문 결제 점수는 Kafka 메시지 수신 즉시 동기 반영이라, 배치 스케줄러 대기 없이 전파 지연만 대기
  sleep(5);

  return { token };
}

export default function (data) {
  const res = http.get(`${config.baseUrl}/api/v1/leaderboards/hashtags?period=WEEKLY&limit=10`, {
    ...authHeaders(data.token),
    tags: { endpoint: 'leaderboard' },
  });
  checkStatus(res, 200);
  sleep(1);
}
