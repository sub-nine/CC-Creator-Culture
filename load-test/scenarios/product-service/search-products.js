import http from 'k6/http';
import { sleep } from 'k6';
import config from '../../config/index.js';
import {checkStatus} from '../../lib/checks.js';

/**
 * 시나리오 설명: 키워드와 페이지 크기를 바꿔가며 상품 통합검색
 * 엔드포인트: GET /api/v1/products
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 500
 * 목표 RPS: 300 이상
 * 목표 P95: 300ms 미만
 * 허용 에러율: 1% 미만
 */
export const options = {
  stages: [
    {duration: '2m', target: 100},
    {duration: '3m', target: 300},
    {duration: '5m', target: 500},
    {duration: '10m', target: 500},
    {duration: '2m', target: 0},
  ],

  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>=300'],
  },
};

export default function () {
  const keywords = ['000', 'k6태그001', 'k6 카테고리 001'];
  const sizes = [10, 30, 50];
  const keyword = keywords[__ITER % keywords.length];
  const size = sizes[__ITER % sizes.length];
  const url = `${config.baseUrl}/api/v1/products?keyword=${encodeURIComponent(keyword)}&page=0&size=${size}`;

  const res = http.get(url);
  checkStatus(res, 200);
  sleep(1);
}
