import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import config from '../../config/index.js';
import { checkStatus } from '../../lib/checks.js';
import { textSummary } from 'https://raw.githubusercontent.com/grafana/k6/v1.0.0/internal/js/summary.js';

const STEADY_START_MS = 240000;
const STEADY_END_MS = 540000;

/**
 * 시나리오 설명: 상품 목록에서 하나를 선택해 상세 페이지로 들어가는 흐름
 * 엔드포인트: GET /api/v1/products/{productId}
 * 테스트 유형: 부하 테스트 (load)
 * 최대 VUser: 100
 * 목표 TPS: 80 req/s (100 VU 유지 5분 동안 24,000건 이상)
 * 목표 P95: 100ms / 목표 P99: 200ms (100 VU 유지 구간)
 * 허용 에러율: 0.1% 미만
 * 프로파일 선정 이유: 예상 피크 60 VUser의 성능을 확인하고, 최대 100 VUser까지 증가시켜 성능 여유를 검증
 */
export const options = {
  stages: [
    { duration: '1m', target: 60 },
    { duration: '2m', target: 60 },
    { duration: '1m', target: 100 },
    { duration: '5m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{operation:detail,phase:steady_100}': ['p(95)<100', 'p(99)<200'],
    'http_req_failed{operation:detail}': ['rate<0.001'],
    'http_reqs{operation:detail,phase:steady_100}': ['count>=24000'],
  },
};

export function setup() {
  const productIds = new Set();
  for (let page = 0; page < 20; page++) {
    const res = http.get(`${config.baseUrl}/api/v1/products?page=${page}&size=50`);
    if (res.status !== 200) {
      throw new Error(`상품 목록 조회 실패: page=${page}, status=${res.status}`);
    }
    const data = res.json().data;
    const products = data?.content;
    if (!Array.isArray(products) || products.some(product => !product?.productId)) {
      throw new Error(`상품 목록 응답에 유효한 상품 ID가 없습니다: page=${page}`);
    }
    products.forEach(product => productIds.add(product.productId));
    if (data.last === true || products.length === 0) break;
  }
  if (productIds.size === 0) {
    throw new Error('상세조회에 사용할 상품 ID가 없습니다. 상품 데이터를 확인하세요.');
  }
  console.log(`상세조회 테스트 대상: ${productIds.size}개 상품`);
  return [...productIds];
}

export default function (productIds) {
  const elapsed = Date.now() - exec.scenario.startTime;
  const phase = elapsed >= STEADY_START_MS && elapsed < STEADY_END_MS ? 'steady_100' : 'other';
  const productId = productIds[(__VU - 1 + __ITER) % productIds.length];
  const res = http.get(`${config.baseUrl}/api/v1/products/${productId}`, {
    tags: { name: 'GET /api/v1/products/:productId', operation: 'detail', phase },
  });
  checkStatus(res, 200);
  sleep(1);
}

export function handleSummary(data) {
  const seconds = Math.max(0,
    Math.min(data.state.testRunDurationMs, STEADY_END_MS) - STEADY_START_MS) / 1000;
  const metric = data.metrics['http_reqs{operation:detail,phase:steady_100}'];
  if (metric) {
    metric.values.rate = seconds > 0 ? metric.values.count / seconds : 0;
  }
  const report = {
    thresholds: {},
    checks: { metrics: {}, ordered_checks: data.root_group.checks },
    metrics: { http: {}, execution: {}, network: {}, custom: {} },
    groups: {},
    scenarios: {},
  };
  for (const [name, value] of Object.entries(data.metrics)) {
    const entry = { ...value, name };
    if (value.thresholds) {
      report.thresholds[name] = {
        metric: entry,
        thresholds: Object.entries(value.thresholds).map(([source, result]) => ({ source, ...result })),
      };
    }
    if (name.startsWith('http_') &&
        (!/^http_(req_duration|req_failed|reqs)(\{|$)/.test(name) || name.includes('expected_response:'))) {
      continue;
    }
    if (name === 'checks') {
      const { passes, fails } = value.values;
      const total = passes + fails;
      report.checks.metrics.checks_total = {
        name: 'checks_total', type: 'counter', contains: 'default',
        values: { count: total, rate: data.state.testRunDurationMs > 0 ? total / (data.state.testRunDurationMs / 1000) : 0 },
      };
      report.checks.metrics.checks_succeeded = { ...entry, name: 'checks_succeeded' };
      report.checks.metrics.checks_failed = {
        ...entry, name: 'checks_failed',
        values: { passes: fails, fails: passes, rate: total > 0 ? fails / total : 0 },
      };
      continue;
    }
    const section = name.startsWith('http_') ? 'http'
      : name.startsWith('data_') ? 'network'
      : /^(vus|iterations|iteration_duration|dropped_iterations)/.test(name) ? 'execution' : 'custom';
    report.metrics[section][name] = entry;
  }
  return { stdout: textSummary(report, { ...data.options, enableColors: true }) };

}
