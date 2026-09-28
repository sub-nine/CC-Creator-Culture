import http from 'k6/http';
import { login } from './auth.js';

const MASTER_EMAIL = 'k6-test-master@example.com';
const MASTER_PASSWORD = 'Passw0rd!';

// load-test/seed-master.sql로 심어둔 MASTER 계정 토큰. creator 승인에만 쓴다
export function loginAsMaster(baseUrl) {
  const token = login(baseUrl, MASTER_EMAIL, MASTER_PASSWORD);
  if (!token) {
    throw new Error('setup 실패 - MASTER 로그인 실패. load-test/seed-master.sql을 실행했는지 확인하세요.');
  }
  return token;
}

// creator 가입 -> MASTER 승인 -> 로그인까지 마쳐서 바로 쓸 수 있는 creator 토큰을 반환한다
export function signupApprovedCreator(baseUrl, label, unique) {
  const masterToken = loginAsMaster(baseUrl);

  const email = `k6-test-${label}-creator-${unique}@example.com`;
  const password = 'Passw0rd!';
  // unique(타임스탬프)만으로는 여러 시나리오가 비슷한 시각에 호출될 때 마지막 8자리가
  // 겹칠 수 있어(전화번호 중복 409 발생) 랜덤 값을 섞어 충돌 가능성을 낮춘다
  const phoneSeed = `${unique}${Math.floor(Math.random() * 1e6)}`;
  const phoneDigits = phoneSeed.slice(-8).padStart(8, '0');

  const signupRes = http.post(
    `${baseUrl}/api/v1/auth/signup/creator`,
    JSON.stringify({
      email,
      password,
      nickname: `k6${label}cr${unique}`,
      phone: `010-${phoneDigits.slice(0, 4)}-${phoneDigits.slice(4)}`,
      address: '서울시 강남구',
      creatorName: `k6${label}상점${unique}`,
      businessRegistrationNumber: `${unique.slice(-3)}-${unique.slice(-2)}-${unique.slice(-5)}`,
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (signupRes.status !== 201) {
    throw new Error(`setup 실패 - 크리에이터 가입 status=${signupRes.status} body=${signupRes.body}`);
  }
  const creatorId = signupRes.json().data.creatorId;

  const approvalRes = http.patch(
    `${baseUrl}/api/v1/admin/creators/${creatorId}/approval`,
    JSON.stringify({ approvalStatus: 'APPROVED' }),
    { headers: { Authorization: `Bearer ${masterToken}`, 'Content-Type': 'application/json' } },
  );
  if (approvalRes.status !== 200) {
    throw new Error(`setup 실패 - 크리에이터 승인 status=${approvalRes.status} body=${approvalRes.body}`);
  }

  const creatorToken = login(baseUrl, email, password);
  if (!creatorToken) {
    throw new Error('setup 실패 - 승인된 크리에이터 로그인 실패');
  }
  return creatorToken;
}
