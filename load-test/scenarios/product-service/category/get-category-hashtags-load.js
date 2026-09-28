import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { loginAsMaster, signupApprovedCreator } from '../../../lib/creator.js';
import { registerProduct, waitForHashtagId } from '../../../lib/product.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 카테고리 상세 화면에서 소속 해시태그 목록을 훑어보는 흐름. setup()에서
 *   조회 대상 카테고리를 만들고 해시태그 20개를 실제로 연결해둬(시드 데이터), 해시태그가
 *   0개인 비현실적 조건이 아니라 목록/페이징 비용이 실제로 발생하는 조건을 재현한다.
 * 엔드포인트: GET /api/v1/categories/{categoryId}/hashtags (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 12
 * 목표 TPS: 7 req/s 이상 (병목 판정용 아님 - sanity check, closed model 특성상 TPS로는 병목을 못 잡음)
 * 목표 P95: 100ms 미만 (실측 기준 재산정)
 * 목표 P99: 250ms 미만 (실측 기준 재산정)
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유:
 *   - VUser 12명
 *       - 공통 가정: DAU 5,000명 · 활성 4시간 · 평균 세션 5분 기준 Little's Law로 평균 동시접속자 104명, 피크 2배 208명
 *       - 상세 도달자(9.75%) 중 해시태그 탭까지 여는 비율 60%까지 곱해 산출 (208 * 9.75% * 60% ~ 12)
 *   - 목표 TPS 7: closed model(sleep(1)+VU) 특성상 응답이 1초보다 훨씬 빠른 한 TPS는 서버 성능과
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
    { duration: '30s', target: 12 },
    { duration: '1m', target: 12 },
    { duration: '10s', target: 0 },
  ],
  // setup()의 시드 생성 요청도 http_req_duration 등에 합산되므로, endpoint:hashtags 태그로
  // 부하 구간의 조회 요청만 걸러서 threshold를 검증한다
  thresholds: {
    'http_req_duration{endpoint:hashtags}': ['p(95)<100', 'p(99)<250'],
    'http_req_failed{endpoint:hashtags}': ['rate<0.01'],
    'http_reqs{endpoint:hashtags}': ['rate>=7'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-cathashtags-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6cathash${unique}`,
      phone: `010-${unique.slice(-8, -4)}-${unique.slice(-4)}`,
      address: '서울시 강남구',
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 회원가입 status=${signupRes.status} body=${signupRes.body}`);
  }

  const token = login(config.baseUrl, email, password);

  const masterToken = loginAsMaster(config.baseUrl);
  const categoryName = `k6cathash${unique.slice(-6)}`;
  const categoryRes = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name: categoryName, description: 'k6 카테고리 해시태그 목록 조회 부하 테스트용 시드 카테고리' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (categoryRes.status !== 201) {
    throw new Error(`setup 실패 - 시드 카테고리 생성 status=${categoryRes.status} body=${categoryRes.body}`);
  }
  const categoryId = categoryRes.json().data;

  // 해시태그가 0개면 목록 조회 비용이 항상 공짜라 병목을 찾을 수 없어, 해시태그 20개를
  // 만들어서 admin API로 동기적으로(비동기 유사도 파이프라인 안 거치고) 카테고리에 연결한다
  const creatorToken = signupApprovedCreator(config.baseUrl, 'cathashseed', unique);
  const HASHTAG_COUNT = 20;
  for (let i = 0; i < HASHTAG_COUNT; i++) {
    const hashtagName = `k6ch${unique.slice(-3)}${i}`;
    const productId = registerProduct(config.baseUrl, creatorToken, `${unique}-${i}`, 'k6 카테고리 해시태그 시드 상품', [
      hashtagName,
    ]);

    const hashtagId = waitForHashtagId(config.baseUrl, creatorToken, productId);

    const linkRes = http.post(`${config.baseUrl}/api/v1/admin/categories/${categoryId}/hashtags/${hashtagId}`, null, {
      headers: { Authorization: `Bearer ${masterToken}` },
    });
    if (linkRes.status !== 200) {
      throw new Error(`setup 실패 - 해시태그 연결 status=${linkRes.status} body=${linkRes.body}`);
    }
  }

  return { token, categoryId };
}

export default function (data) {
  const res = http.get(`${config.baseUrl}/api/v1/categories/${data.categoryId}/hashtags`, {
    ...authHeaders(data.token),
    tags: { endpoint: 'hashtags' },
  });
  checkStatus(res, 200);
  sleep(1);
}
