import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 상품 상세 조회 요청을 단계적으로 늘려 처리 한계와 병목 구간을 확인하는 흐름
 * 엔드포인트: GET /api/v1/products/{productId}
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
    'http_req_duration{operation:detail}': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed{operation:detail}': ['rate<0.001'],
  },
};

export function setup() {
  const productIds = new Set();
  for (let page = 0; page < 20; page++) {
    const res = http.get(`${config.baseUrl}/api/v1/products?page=${page}&size=50`);
    if (res.status !== 200) {
      throw new Error(`상품 목록 조회 실패: page=${page}, status=${res.status}`);
    }
    const data = res.json().data;
    const products = data?.content;
    if (!Array.isArray(products) || products.some(product => !product?.productId)) {
      throw new Error(`상품 목록 응답에 유효한 상품 ID가 없습니다: page=${page}`);
    }
    products.forEach(product => productIds.add(product.productId));
    if (data.last === true || products.length === 0) break;
  }
  if (productIds.size === 0) {
    throw new Error('상세조회에 사용할 상품 ID가 없습니다. 상품 데이터를 확인하세요.');
  }
  console.log(`상세조회 테스트 대상: ${productIds.size}개 상품`);
  return [...productIds];
}

export default function (productIds) {
  const productId = productIds[(__VU - 1 + __ITER) % productIds.length];
  const res = http.get(`${config.baseUrl}/api/v1/products/${productId}`, {
    tags: { name: 'GET /api/v1/products/:productId', operation: 'detail' },
  });
  checkStatus(res, 200);
  sleep(1);
}
