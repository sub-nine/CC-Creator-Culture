import http from 'k6/http';
import { sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import config from '../../../config/index.js';
import { authHeaders } from '../../../lib/auth.js';
import { signupApprovedCreator, loginAsMaster } from '../../../lib/creator.js';
import { registerProduct } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: setup()에서 고정 타겟 카테고리를 하나 미리 만들어둬(빈 후보 목록이면
 *   파이프라인이 아예 안 돌고 바로 신규 승격돼버리는 것을 방지), 매 iteration 그 카테고리와
 *   편집거리가 큰(=Levenshtein 유사도 낮은) 완전 무작위 해시태그를 단 상품을 등록한다.
 *   Levenshtein 스테이지(@Order(1))에서 안 끝나고 반드시 Embedding 스테이지(@Order(2))까지
 *   내려가 실제 embedding-service를 호출하게 만드는 게 목적. HashtagCreatedEvent -> tryLink()
 *   비동기 처리 경로 전체(Levenshtein+Embedding)에 부하를 준다.
 * 엔드포인트: POST /api/v1/admin/categories (setup, 타겟 카테고리 생성) +
 *   POST /api/v1/products (등록) + GET /api/v1/products/{productId}(MERGE 확인) +
 *   GET /api/v1/admin/categories/merge-requests(PENDING_APPROVAL 확인) - 비동기 처리 완료 폴링,
 *   게이트웨이 정책상 로그인 필요
 * 테스트 유형: 부하 테스트 (load), 비동기 파이프라인 처리 지연 측정
 * 최대 VUser: 10
 * 목표 TPS: 상품 등록 자체는 3 req/s 내외 (등록 API 응답은 빠르지만, 뒤쪽 파이프라인이 병목이라
 *   VU를 크게 늘리지 않음 - embedding-service/HikariCP 압박 정도는 Grafana Infra Monitoring 참고)
 * 목표 P95: 상품 등록 API 자체는 500ms, 해시태그->카테고리 연결까지는 10s
 * 허용 에러율: 상품 등록 1% 미만, 해시태그 연결(비동기) 완료율 95% 이상
 * 프로파일 선정 이유: 등록 응답은 Kafka 이벤트 발행 후 바로 돌아오므로 http_req_duration만으로는
 *   파이프라인 부하를 알 수 없음 - 등록 후 두 조회 API를 폴링해 실제 연결(MERGE 또는
 *   PENDING_APPROVAL) 완료까지 걸리는 시간(hashtag_link_latency)과 완료율(hashtag_link_success)을
 *   커스텀 지표로 별도 측정함
 */
export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 10 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    hashtag_link_latency: ['p(95)<10000'],
    hashtag_link_success: ['rate>0.95'],
  },
};

const linkLatency = new Trend('hashtag_link_latency', true);
const linkSuccess = new Rate('hashtag_link_success');

const POLL_INTERVAL_SECONDS = 1;
const POLL_MAX_ATTEMPTS = 15;

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'hashtaglink', unique);

  const masterToken = loginAsMaster(config.baseUrl);
  const categoryName = `k6mg${unique.slice(-5)}`;
  const categoryRes = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name: categoryName, description: 'k6 해시태그 부하 테스트용 타겟 카테고리(임베딩 비교 후보 확보용)' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (categoryRes.status !== 201) {
    throw new Error(`setup 실패 - 타겟 카테고리 생성 status=${categoryRes.status} body=${categoryRes.body}`);
  }

  return { creatorToken, masterToken };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  // 해시태그는 문자/숫자만, 10자 이내여야 함(CreateProductRequest 검증 규칙) - "k6" + base36 8자
  // 타겟 카테고리 이름과 편집거리가 크게 나도록 완전 무작위로 생성 - Levenshtein에서 안 끝나고
  // Embedding 스테이지까지 반드시 거치게 하기 위함
  const suffix = (Date.now() * 100 + (__VU % 10) * 10 + (__ITER % 10)).toString(36).slice(-8);
  const hashtagName = `k6${suffix}`;

  const productId = registerProduct(config.baseUrl, data.creatorToken, unique, 'k6 해시태그 부하 테스트 상품', [
    hashtagName,
  ]);

  const startedAt = Date.now();
  let linked = false;
  for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
    sleep(POLL_INTERVAL_SECONDS);

    // MERGE(자동 승인) 확인 - 기존 카테고리와 MERGE된 경우, 신규 카테고리로 승격된 경우 모두 잡힘
    const productRes = http.get(`${config.baseUrl}/api/v1/products/${productId}`, authHeaders(data.creatorToken));
    checkStatus(productRes, 200);
    const categories = productRes.json('data.categories');
    if (categories && categories.length > 0) {
      linked = true;
      break;
    }

    // PENDING_APPROVAL(승인 대기) 확인 - products 응답은 MERGED만 집계하므로 별도로 확인해야 함
    const mergeRequestsRes = http.get(
      `${config.baseUrl}/api/v1/admin/categories/merge-requests?sort=createdAt,desc&size=50`,
      authHeaders(data.masterToken),
    );
    checkStatus(mergeRequestsRes, 200);
    const pendingRequests = mergeRequestsRes.json('data.content');
    if (pendingRequests && pendingRequests.some((request) => request.hashtagName === hashtagName)) {
      linked = true;
      break;
    }
  }

  linkLatency.add(Date.now() - startedAt);
  linkSuccess.add(linked);
}
