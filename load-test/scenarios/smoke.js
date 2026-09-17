import http from 'k6/http';
import { check } from 'k6';

// 스모크: 단일 VU, 1회 반복, 읽기 요청 1건. 가입/시드/데이터 생성 없음.
// SMOKE_URL은 공개로 200을 기대하는 엔드포인트를 직접 지정한다.
// (예: 임베딩 서버 /docs. 게이트웨이 readiness는 인증이 걸릴 수 있어 기본값을 두지 않는다)
const url = __ENV.SMOKE_URL || '';
if (!url) {
  throw new Error('SMOKE_URL이 필요합니다 (예: <embedding_url>/docs)');
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
  const res = http.get(url);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body is not empty': (r) => r.body !== null && r.body.length > 0,
  });
}
