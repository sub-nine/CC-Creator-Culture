import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../../config/index.js';
import { login, authHeaders } from '../../../lib/auth.js';
import { loginAsMaster } from '../../../lib/creator.js';
import { checkStatus } from '../../../lib/checks.js';

/**
 * 시나리오 설명: 카테고리 탐색 화면에서 키워드로 카테고리를 검색하는 흐름. setup()에서
 *   키워드에 매칭되는 카테고리 20개 + 매칭 안 되는 노이즈 카테고리 30개(총 50개)를 미리
 *   만들어둬(시드 데이터), 검색 결과가 1건뿐인 비현실적인 조건이 아니라 여러 건 중
 *   필터링/페이징하는 실제 서비스에 가까운 조건을 재현한다.
 * 엔드포인트: GET /api/v1/categories?keyword= (게이트웨이 정책상 로그인 필요)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 30
 * 목표 TPS: 10 req/s 이상 (병목 판정용 아님 - sanity check, 아래 참고)
 * 목표 P95: 100ms 미만 (실측 기준 재산정, 아래 참고)
 * 목표 P99: 250ms 미만 (실측 기준 재산정, 아래 참고)
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유:
 *   - VUser 30명: DAU 5,000명 가정 시 Little's Law 기반 피크 동시접속자 추정치(~208명)
 *       - 카테고리 탐색 비중 15%로 산출
 *   - 목표 TPS 10: closed model(sleep(1)+VU) 특성상 TPS는 VU 수에서 역산된 값이지 실제
 *     트래픽 추정치가 아님 - 응답이 1초보다 훨씬 빠른 한(지금 수십ms 수준) TPS는 서버 성능과
 *     무관하게 거의 항상 VU 수 근처로 나와서, 이 값으로는 병목을 못 찾음
 *       - 테스트가 완전히 실패해 로드 자체가 안 걸렸는지만 거르는 느슨한 sanity check로 낮춤
 *       - 병목 판정은 실제 처리 비용을 그대로 반영하는 P95/P99가 전담
 *   - 목표 P95 100ms / P99 250ms: 프로덕션 SLA(캐싱 여부 무관하게 유지할 사용자 체감 기준)와
 *     회귀 탐지(실측 베이스라인 대비 몇 배 느려지면 잡아내는 기준)를 절충. 매칭20+노이즈30 시드
 *     기준 실측치(p95=32.87ms, p99=251.31ms, endpoint:search 태그 스코프) 대비 P95는 3배 여유,
 *     P99는 거의 근접 - 기존 250ms/500ms는 실측 대비 10배 가까이 헐렁해서 병목이 나도 못 잡았음
 *     (지난 TPS Fail 당시 P95=36.53ms로 250ms 대비 여전히 여유)
 *   - Ramp-up/down 30초/10초
 *       - 실제 시간축 아님, 반복 테스트를 위한 완만한 증감 패턴만 압축 재현
 * TODO: TPS를 진짜 병목 탐지 게이트로 쓰려면 arrival-rate(open model) executor로 전환 필요 -
 *   서버가 느려져도 부하량이 줄지 않아 closed model의 self-throttling 문제를 근본적으로 해결함
 */
export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 30 },
    { duration: '10s', target: 0 },
  ],
  // setup()의 시드 생성 요청도 http_req_duration 등에 합산되므로, endpoint:search 태그로
  // 부하 구간의 검색 요청만 걸러서 threshold를 검증한다
  thresholds: {
    'http_req_duration{endpoint:search}': ['p(95)<100', 'p(99)<250'],
    'http_req_failed{endpoint:search}': ['rate<0.01'],
    'http_reqs{endpoint:search}': ['rate>=10'],
  },
};

export function setup() {
  const unique = `${Date.now()}`;
  const email = `k6-test-categories-${unique}@example.com`;
  const password = 'Passw0rd!';

  const signupRes = http.post(
    `${config.baseUrl}/api/v1/auth/signup/customer`,
    JSON.stringify({
      email,
      password,
      nickname: `k6categories${unique}`,
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
  const keyword = `k6search${unique.slice(-6)}`;

  // 검색 결과가 1건뿐이면 실제 서비스처럼 여러 건 중 필터링/페이징하는 부하를 재현할 수 없어
  // 키워드에 매칭되는 카테고리와 매칭 안 되는 노이즈 카테고리를 함께 심어 테이블 크기를 키운다
  const MATCHING_COUNT = 20;
  const NOISE_COUNT = 30;

  for (let i = 0; i < MATCHING_COUNT; i++) {
    seedCategory(masterToken, `${keyword}-${i}`);
  }
  for (let i = 0; i < NOISE_COUNT; i++) {
    seedCategory(masterToken, `k6noise${unique.slice(-6)}-${i}`);
  }

  return { token, keyword };
}

function seedCategory(masterToken, name) {
  const res = http.post(
    `${config.baseUrl}/api/v1/admin/categories`,
    JSON.stringify({ name, description: 'k6 카테고리 검색 부하 테스트용 시드 카테고리' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (res.status !== 201) {
    throw new Error(`setup 실패 - 시드 카테고리 생성 status=${res.status} body=${res.body}`);
  }
}

export default function (data) {
  const res = http.get(
    `${config.baseUrl}/api/v1/categories?keyword=${encodeURIComponent(data.keyword)}`,
    { ...authHeaders(data.token), tags: { endpoint: 'search' } },
  );
  checkStatus(res, 200);
  sleep(1);
}
