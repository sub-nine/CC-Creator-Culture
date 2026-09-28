import http from 'k6/http';
import { sleep } from 'k6';
import { Trend } from 'k6/metrics';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { loginAsMaster, signupApprovedCreator } from '../../../lib/creator.js';
import { registerProduct, waitForHashtagId } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 메인 화면의 인기 카테고리 리더보드를 조회하는 흐름. 리더보드가 비어있으면
 *   실제 집계 비용 없이 항상 공짜로 통과해버리므로, setup()에서 상품 10개를 등록하고 해시태그를
 *   카테고리에 동기적으로 연결한 뒤 서로 다른 횟수로 조회수를 발생시켜 순위가 실제로 갈리는
 *   조건을 만든다. 조회수 -> 리더보드 반영까지 최대 60초 걸리는 ProductViewCountScheduler(매분
 *   실행)를 거치므로, 고정 대기 대신 리더보드를 폴링해 이 카테고리의 점수가 기대치(조회수
 *   합계 55 * PRODUCT_SYNC_VIEW 가중치 1.0 = 55)로 반영될 때까지 확인하고 그 지연을
 *   leaderboard_ready_latency로 측정한다. setupTimeout을 늘린 이유도 동일.
 * 엔드포인트: GET /api/v1/leaderboards/categories (게이트웨이 정책상 로그인 필요)
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
  // 리더보드 시드 데이터가 반영되길 기다리는 데 65초 넘게 걸려 k6 기본값(60초)으로는 부족함
  setupTimeout: '120s',
  stages: [
    { duration: '30s', target: 25 },
    { duration: '1m', target: 25 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:leaderboard}': ['p(95)<100', 'p(99)<250'],
    'http_req_failed{endpoint:leaderboard}': ['rate<0.01'],
    'http_reqs{endpoint:leaderboard}': ['rate>=8'],
    leaderboard_ready_latency: ['p(95)<70000'],
  },
};

const leaderboardReadyLatency = new Trend('leaderboard_ready_latency', true);

// 상품별 조회수 1+2+...+10 합계에 PRODUCT_SYNC_VIEW 가중치(1.0)를 곱한 기대 점수
const EXPECTED_CATEGORY_SCORE = 55;
const POLL_INTERVAL_SECONDS = 5;
const POLL_MAX_ATTEMPTS = 16;

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-catleaderboard-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6catlb${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);

  // 리더보드용 카테고리를 만들고, 해시태그 10개를 상품과 함께 등록해 admin API로
  // 동기적으로(비동기 유사도 파이프라인 안 거치고) 카테고리에 연결한다
  const masterToken = loginAsMaster(config.baseUrl);
  const categoryName = `k6clb${unique.slice(-6)}`;
  const categoryRes = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name: categoryName, description: 'k6 카테고리 리더보드 부하 테스트용 시드 카테고리' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (categoryRes.status !== 201) {
    throw new Error(`setup 실패 - 시드 카테고리 생성 status=${categoryRes.status} body=${categoryRes.body}`);
  }
  const categoryId = categoryRes.json().data;

  const creatorToken = signupApprovedCreator(config.baseUrl, 'catlbseed', unique);
  const PRODUCT_COUNT = 10;
  const productIds = [];
  for (let i = 0; i < PRODUCT_COUNT; i++) {
    const hashtagName = `k6clb${unique.slice(-4)}${i}`;
    const productId = registerProduct(config.baseUrl, creatorToken, `${unique}-${i}`, 'k6 카테고리 리더보드 시드 상품', [
      hashtagName,
    ]);
    productIds.push(productId);

    const hashtagId = waitForHashtagId(config.baseUrl, creatorToken, productId);

    const linkRes = http.post(`${config.baseUrl}/api/v1/admin/categories/${categoryId}/hashtags/${hashtagId}`, null, {
      headers: { Authorization: `Bearer ${masterToken}` },
    });
    if (linkRes.status !== 200) {
      throw new Error(`setup 실패 - 해시태그 연결 status=${linkRes.status} body=${linkRes.body}`);
    }
  }

  // 뷰어(로그인 사용자/게스트 쿠키)당 30분 dedup이 있어(ProductViewRepositoryImpl) 같은
  // viewerId로 아무리 반복 조회해도 1회만 카운트된다. 그래서 매 조회를 비로그인 + 게스트 쿠키
  // 초기화로 호출해 매번 새 viewerId(guest:UUID)를 만든다. 위 waitForHashtagId에서 creator
  // 계정으로 이미 1회 조회했으므로(그것도 dedup 대상이라 1로 집계), 상품별로 i번만 추가
  // 조회하면 최종 합계가 정확히 (i+1)이 된다
  const jar = http.cookieJar();
  productIds.forEach((productId, i) => {
    for (let v = 0; v < i; v++) {
      jar.clear(`${config.baseUrl}/api/v1/products/${productId}`);
      http.get(`${config.baseUrl}/api/v1/products/${productId}`);
    }
  });

  // 고정 대기 대신, ProductViewCountScheduler 반영 후 이 카테고리의 점수가 기대치대로
  // 나올 때까지 폴링하며 실제 반영 지연을 측정한다
  const triggeredAt = Date.now();
  let ready = false;
  for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
    sleep(POLL_INTERVAL_SECONDS);

    // limit을 넉넉히 잡아 다른 시나리오가 남긴 카테고리에 밀려 못 잡히는 상황을 방지
    const leaderboardRes = http.get(`${config.baseUrl}/api/v1/leaderboards/categories?period=WEEKLY&limit=200`, authHeaders(token));
    checkStatus(leaderboardRes, 200);
    const items = leaderboardRes.json('data.items') || [];
    const match = items.find((item) => item.targetId === categoryId);
    if (match && Math.abs(match.score - EXPECTED_CATEGORY_SCORE) < 0.01) {
      ready = true;
      break;
    }
  }
  leaderboardReadyLatency.add(Date.now() - triggeredAt);
  if (!ready) {
    throw new Error(`setup 실패 - 리더보드 점수 반영 확인 실패 (categoryId=${categoryId}, 기대 점수=${EXPECTED_CATEGORY_SCORE})`);
  }

  return { token };
}

export default function (data) {
  const res = http.get(`${config.baseUrl}/api/v1/leaderboards/categories?period=WEEKLY&limit=10`, {
    ...authHeaders(data.token),
    tags: { endpoint: 'leaderboard' },
  });
  checkStatus(res, 200);
  sleep(1);
}
