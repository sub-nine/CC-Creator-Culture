#!/usr/bin/env bash
# 측정 후 정합성 검증
#
# 처리량 수치와 별개로, 발급 수량이 정확한지 매 실행마다 확인한다.
# 테스트가 끝난 후 쿠폰이 정확히 100개만 발급됐는지 확인하는 용도

set -euo pipefail

COUPON_ID="${COUPON_ID:-01920000-0000-7000-8000-000000000001}"
EXPECTED_ISSUED="${EXPECTED_ISSUED:-100}"
EXPECTED_REMAINING="${EXPECTED_REMAINING:-0}"

PGDATABASE="${PGDATABASE:-order_db}"
PGUSER="${PGUSER:?Set PGUSER to the order database account}"
REDIS_DB="${REDIS_DB:-0}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-cc-service-apps-postgres-1}"
REDIS_CONTAINER="${REDIS_CONTAINER:-cc-service-apps-redis-1}"

PSQL=(
  docker exec -i "$POSTGRES_CONTAINER"
  psql -X
  -d "$PGDATABASE"
  -U "$PGUSER"
  -v ON_ERROR_STOP=1
  -q -t -A
)

REDIS=(
  docker exec "$REDIS_CONTAINER"
  redis-cli -e --raw
  -n "$REDIS_DB"
)


UUID_PATTERN='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
[[ "$COUPON_ID" =~ $UUID_PATTERN ]] || { echo 'Invalid lowercase COUPON_ID UUID' >&2; exit 1; }
[[ "$EXPECTED_ISSUED" =~ ^[0-9]{1,10}$ ]] || { echo 'Invalid EXPECTED_ISSUED' >&2; exit 1; }
[[ "$EXPECTED_REMAINING" =~ ^[0-9]{1,10}$ ]] || { echo 'Invalid EXPECTED_REMAINING' >&2; exit 1; }

ROWS=$("${PSQL[@]}" -c "SELECT COUNT(*) FROM public.p_user_coupons WHERE coupon_id = '${COUPON_ID}';")
TOTAL=$("${PSQL[@]}" -c "SELECT total_quantity FROM public.p_coupons WHERE id = '${COUPON_ID}';")
DUP=$("${PSQL[@]}" -c "SELECT COUNT(*) FROM (SELECT user_id FROM public.p_user_coupons WHERE coupon_id='${COUPON_ID}' GROUP BY user_id HAVING COUNT(*) > 1) t;")
REMAINING=$("${REDIS[@]}" GET "coupon:${COUPON_ID}:remaining")

for value in "$ROWS" "$TOTAL" "$DUP" "$REMAINING"; do
    [[ "$value" =~ ^[0-9]{1,10}$ ]] || { echo '[NG] Missing or invalid DB/Redis value' >&2; exit 1; }
done

echo "p_user_coupons 행 수 : ${ROWS}"
echo "total_quantity       : ${TOTAL}"
echo "Redis remaining      : ${REMAINING}"
echo "중복 발급 사용자      : ${DUP}"
echo

FAIL=0
[ "${ROWS}" = "${EXPECTED_ISSUED}" ] || { echo "[NG] 발급 수량이 기대값(${EXPECTED_ISSUED})과 다름"; FAIL=1; }
[ "${REMAINING}" = "${EXPECTED_REMAINING}" ] || { echo "[NG] Redis 잔여 수량이 기대값(${EXPECTED_REMAINING})과 다름"; FAIL=1; }
[ "${ROWS}" -le "${TOTAL}" ]           || { echo "[NG] 발급 이력 수가 total_quantity 초과"; FAIL=1; }
[ "${DUP}" = "0" ]                     || { echo "[NG] 중복 발급 발생"; FAIL=1; }
[ $((ROWS + REMAINING)) = "${TOTAL}" ] || { echo "[NG] Redis 잔여 수량과 DB 발급 이력의 합이 총량과 다름"; FAIL=1; }

[ "${FAIL}" = "0" ] && echo "[OK] 정합성 검증 통과"
exit "${FAIL}"
