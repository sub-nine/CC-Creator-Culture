import http from 'k6/http';
import { check } from 'k6';

export function login(baseUrl, email, password) {
  const res = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  const ok = check(res, { '로그인 성공': (r) => r.status === 200 });
  if (!ok) {
    console.error(`로그인 실패: status=${res.status} body=${res.body}`);
    return undefined;
  }

  return res.json().data.accessToken;
}

export function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}
