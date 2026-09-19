import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { authHeaders } from '../../../lib/auth.js';
import { signupApprovedCreator } from '../../../lib/creator.js';
import { registerProduct } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 상품 등록 화면에서 크리에이터가 해시태그를 키워드로 찾아보는 흐름.
 *   setup()에서 검색어와 동일한 해시태그를 단 상품을 미리 등록해둬(시드 데이터),
 *   외부 데이터 상태와 무관하게 검색 결과가 항상 1건 이상 나오도록 한다.
 * 엔드포인트: GET /api/v1/hashtags?keyword= (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 20 req/s
 * 목표 P95: 250ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 반복마다 sleep(1)이 있어 VU당 최대 처리량이 초당 1건이라, 최대 VU 30 기준 램프업/다운 구간까지 포함한 전체 평균 기준 실측 상한(~24 req/s)보다 여유 있게 목표를 잡음
 */
export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<250'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=20'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'hashtagsearch', unique);

  // 해시태그는 문자/숫자만, 10자 이내여야 함(CreateProductRequest 검증 규칙)
  const keyword = `k6srch${unique.slice(-4)}`;
  registerProduct(config.baseUrl, creatorToken, unique, 'k6 해시태그 검색 부하 테스트용 시드 상품', [keyword]);

  return { token: creatorToken, keyword };
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/hashtags?keyword=${encodeURIComponent(data.keyword)}`,
    authHeaders(data.token),
  );
  checkStatus(res, 200);
  sleep(1);
}
