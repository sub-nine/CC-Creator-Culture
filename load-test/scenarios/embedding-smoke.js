import http from 'k6/http';
import { check } from 'k6';

// 임베딩 스모크: 단일 VU, 1회 반복, POST /embed 1건. 실제 부하는 아니고 가동 확인용이다.
// EMBED_URL은 임베딩 서비스의 /embed 전체 URL을 직접 지정한다 (예: <embedding_url>/embed).
const url = __ENV.EMBED_URL || '';
if (!url) {
  throw new Error('EMBED_URL이 필요합니다 (예: <embedding_url>/embed)');
}

export const options = {
  vus: 1,
  iterations: 1,
  maxRedirects: 0,
  thresholds: {
    checks: ['rate==1'],
  },
};

export default function () {
  const res = http.post(
    url,
    JSON.stringify({ text: 'k6 임베딩 스모크 확인용 문장' }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  let vector;
  try {
    vector = res.status === 200 ? res.json('vector') : undefined;
  } catch (e) {
    vector = undefined;
  }
  check(vector, {
    'vector is an array': (v) => Array.isArray(v),
    'vector length is 768': (v) => Array.isArray(v) && v.length === 768,
    'vector values are finite': (v) =>
      Array.isArray(v) && v.every((x) => Number.isFinite(x)),
  });
}
