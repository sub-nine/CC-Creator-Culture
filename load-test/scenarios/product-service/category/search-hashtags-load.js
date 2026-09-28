import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { authHeaders } from '../../../lib/auth.js';
import { signupApprovedCreator } from '../../../lib/creator.js';
import { registerProduct } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 상품 등록 화면에서 크리에이터가 해시태그를 키워드로 찾아보는 흐름.
 *   setup()에서 키워드에 매칭되는 해시태그 20개 + 매칭 안 되는 노이즈 해시태그 30개(총 50개)를
 *   상품 등록으로 미리 만들어둬(시드 데이터), 해시태그가 1개뿐인 비현실적 조건이 아니라 여러
 *   건 중 필터링/페이징하는 실제 서비스에 가까운 조건을 재현한다.
 * 엔드포인트: GET /api/v1/hashtags?keyword= (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 15
 * 목표 TPS: 5 req/s 이상 (병목 판정용 아님 - sanity check, closed model 특성상 TPS로는 병목을 못 잡음)
 * 목표 P95: 100ms 미만 (실측 기준 재산정)
 * 목표 P99: 250ms 미만 (실측 기준 재산정)
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유:
 *   - VUser 15명: customer와 별도로 크리에이터 모수 기반 Little's Law 역산
 *       - 활성 크리에이터 500명(DAU 10%) 가정, 활성시간 4시간, 세션 10분(등록 작업은 검색보다 오래 걸림)
 *       - 평균 동시 작업 크리에이터 ~21명, 피크 2배 ~42명, 그중 해시태그 검색 UI 사용 비율 40% 가정
 *       - 42명 * 40% ~ 17명, 반올림해서 VUser 15명으로 설정
 *   - 목표 TPS 5: closed model(sleep(1)+VU) 특성상 응답이 1초보다 훨씬 빠른 한 TPS는 서버 성능과
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
    { duration: '30s', target: 15 },
    { duration: '1m', target: 15 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:search}': ['p(95)<100', 'p(99)<250'],
    'http_req_failed{endpoint:search}': ['rate<0.01'],
    'http_reqs{endpoint:search}': ['rate>=5'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const creatorToken = signupApprovedCreator(config.baseUrl, 'hashtagsearch', unique);

  // 해시태그는 문자/숫자만, 10자 이내여야 함(CreateProductRequest 검증 규칙)
  // 검색 결과가 1건뿐이면 실제 서비스처럼 여러 건 중 필터링/페이징하는 부하를 재현할 수 없어
  // 키워드에 매칭되는 해시태그와 매칭 안 되는 노이즈 해시태그를 함께 심어 테이블 크기를 키운다
  const keyword = `k6sr${unique.slice(-4)}`;
  const MATCHING_COUNT = 20;
  const NOISE_COUNT = 30;

  for (let i = 0; i < MATCHING_COUNT; i++) {
    registerProduct(config.baseUrl, creatorToken, `${unique}-m${i}`, 'k6 해시태그 검색 부하 테스트용 시드 상품', [
      `${keyword}${i}`,
    ]);
  }
  for (let i = 0; i < NOISE_COUNT; i++) {
    registerProduct(config.baseUrl, creatorToken, `${unique}-n${i}`, 'k6 해시태그 검색 부하 테스트용 시드 상품', [
      `k6no${unique.slice(-4)}${i}`,
    ]);
  }

  return { token: creatorToken, keyword };
}

export default function (data) {
  const res = http.get(`${config.baseUrl}/api/v1/hashtags?keyword=${encodeURIComponent(data.keyword)}`, {
    ...authHeaders(data.token),
    tags: { endpoint: 'search' },
  });
  checkStatus(res, 200);
  sleep(1);
}
