import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { checkStatus } from '../../lib/checks.js';

/**
 * 시나리오 설명: 크리에이터 지원자가 사업자 정보를 포함해 가입 신청을 하는 흐름
 * 엔드포인트: POST /api/v1/auth/signup/creator (공개 엔드포인트)
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 15
 * 목표 TPS: 10 req/s
 * 목표 P95: 500ms
 * 허용 에러율: 1% 미만
 * 프로파일 선정 이유: 단일 요청이지만 사업자등록번호/상호명 중복 검증과 INSERT가 고객 가입보다 많아 그만큼 낮은 처리량과 다소 높은 지연을 목표로 잡음
 */
export const options = {
  stages: [
    { duration: '20s', target: 15 },
    { duration: '40s', target: 15 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=10'],
  },
};

export default function () {
  const unique = `${Date.now()}-${__VU}-${__ITER}`;
  const email = `k6-test-creator-${unique}@example.com`;
  const password = 'Passw0rd!';
  const phoneDigits = `${Date.now()}${__VU}${__ITER}`.slice(-8).padStart(8, '0');
  const phone = `010-${phoneDigits.slice(0, 4)}-${phoneDigits.slice(4)}`;
  const bizDigits = `${Date.now()}${__VU}${__ITER}`.slice(-10).padStart(10, '0');
  const businessRegistrationNumber = `${bizDigits.slice(0, 3)}-${bizDigits.slice(3, 5)}-${bizDigits.slice(5)}`;

  const res = http.post(
    `${config.baseUrl}/api/v1/auth/signup/creator`,
    JSON.stringify({
      email,
      password,
      nickname: `k6creator${unique}`,
      phone,
      address: '서울시 강남구',
      creatorName: `k6testcreator${unique}`,
      businessRegistrationNumber,
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  checkStatus(res, 201);
  sleep(1);
}
