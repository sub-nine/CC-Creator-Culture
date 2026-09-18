import http from 'k6/http';
import { sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import config from '../../config/index.js';
import { authHeaders } from '../../lib/auth.js';
import { signupApprovedCreator } from '../../lib/creator.js';
import { createProductWithSku } from '../../lib/product.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 매번 새로운(기존 카테고리와 매칭되지 않는) 해시태그를 단 상품을 계속 등록해,
 *   HashtagCreatedEvent -> tryLink()(Levenshtein/Embedding 파이프라인) -> 새 카테고리 승격까지
 *   이어지는 비동기 처리 경로에 부하를 준다.
 * 엔드포인트: POST /api/v1/products (등록) + GET /api/v1/categories (비동기 처리 완료 폴링, 게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load), 비동기 파이프라인 처리 지연 측정
 * 최대 VUser: 10
 * 목표 TPS: 상품 등록 자체는 3 req/s 내외 (등록 API 응답은 빠르지만, 뒤쪽 파이프라인이 병목이라
 *   VU를 크게 늘리지 않음 - embedding-service/HikariCP 압박 정도는 Grafana Infra Monitoring 참고)
 * 목표 P95: 상품 등록 API 자체는 500ms, 해시태그->카테고리 연결까지는 10s
 * 허용 에러율: 상품 등록 1% 미만, 해시태그 연결(비동기) 완료율 95% 이상
 * 프로파일 선정 이유: 등록 응답은 Kafka 이벤트 발행 후 바로 돌아오므로 http_req_duration만으로는
 *   파이프라인 부하를 알 수 없음 - 등록 후 카테고리 검색 API를 폴링해 실제 연결 완료까지 걸리는
 *   시간(hashtag_link_latency)과 완료율(hashtag_link_success)을 커스텀 지표로 별도 측정함
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
  return { creatorToken };
}

export default function (data) {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  // 해시태그는 문자/숫자만, 10자 이내여야 함(CreateProductRequest 검증 규칙) - "k6" + base36 8자
  const suffix = (Date.now() * 100 + (__VU % 10) * 10 + (__ITER % 10)).toString(36).slice(-8);
  const hashtagName = `k6${suffix}`;

  createProductWithSku(config.baseUrl, data.creatorToken, unique, 'k6 해시태그 부하 테스트 상품', [
    hashtagName,
  ]);

  const startedAt = Date.now();
  let linked = false;
  for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
    sleep(POLL_INTERVAL_SECONDS);

    const res = http.get(
      `${config.baseUrl}/api/v1/categories?keyword=${encodeURIComponent(hashtagName)}`,
      authHeaders(data.creatorToken),
    );
    checkStatus(res, 200);

    const content = res.json('data.content');
    if (content && content.some((category) => category.name === hashtagName)) {
      linked = true;
      break;
    }
  }

  linkLatency.add(Date.now() - startedAt);
  linkSuccess.add(linked);
}
