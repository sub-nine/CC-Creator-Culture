import { check } from 'k6';

export function checkStatus(res, expected = 200) {
  return check(res, { [`status is ${expected}`]: (r) => r.status === expected });
}
