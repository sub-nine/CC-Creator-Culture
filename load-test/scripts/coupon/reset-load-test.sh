#!/usr/bin/env bash
# k6 쿠폰 발급 스파이크 테스트 초기화 스크립트
#
# 매 측정 직전에 실행한다. 실행하지 않으면 두 번째 측정부터
# 전부 SOLD_OUT 또는 DUPLICATE 로 끝난다.
#
# 사용법
#   ./reset-load-test.sh

set -euo pipefail

# ---------- 설정 ----------
COUPON_ID="${COUPON_ID:-01920000-0000-7000-8000-000000000001}"
TOTAL_QUANTITY="${TOTAL_QUANTITY:-100}"   # 현재 시나리오: 쿠폰 100개, 요청 500건
ADMIN_ID="${ADMIN_ID:-01920000-0000-7000-8000-0000000000ff}"

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
[[ "$ADMIN_ID" =~ $UUID_PATTERN ]] || { echo 'Invalid ADMIN_ID UUID' >&2; exit 1; }
[[ "$TOTAL_QUANTITY" =~ ^[1-9][0-9]{0,8}$ ]] || { echo 'Invalid quantity (1..999999999)' >&2; exit 1; }

echo "== 1. 발급 이력 삭제 =="
"${PSQL[@]}" <<SQL
BEGIN;
DELETE FROM public.p_user_coupons WHERE coupon_id = '${COUPON_ID}';
DELETE FROM public.p_coupons WHERE id = '${COUPON_ID}';
INSERT INTO public.p_coupons (
    id, coupon_name, discount_rate, total_quantity, issued_quantity,
    started_at, expired_at, created_at, created_by, updated_at
) VALUES (
    '${COUPON_ID}', '부하테스트 쿠폰', 10, ${TOTAL_QUANTITY}, 0,
    now() - interval '1 hour', now() + interval '7 days',
    now(), '${ADMIN_ID}', now()
);
COMMIT;
SQL

echo "== 2. Redis 키 정리: 현재 동기 발급 =="
# Scan only this coupon's keys; check scan success before deletion.
"${REDIS[@]}" DEL "coupon:${COUPON_ID}:remaining" > /dev/null
for suffix in 'issued:*'; do
    keys=$("${REDIS[@]}" --scan --pattern "coupon:${COUPON_ID}:${suffix}")
    while IFS= read -r key; do
        [[ -z "$key" ]] || "${REDIS[@]}" DEL "$key" > /dev/null
    done <<< "$keys"
done

echo "== 3. Redis 키 정리: 향후 비동기 발급 =="
# 동기 테스트에서는 생성되지 않지만, 향후 Kafka 비동기 테스트와 같은 초기화
# 스크립트를 재사용할 수 있도록 요청 상태와 실패 로그 키도 정리한다.
for suffix in 'issue:request:*' 'issue:failed:*'; do
    keys=$("${REDIS[@]}" --scan --pattern "coupon:${COUPON_ID}:${suffix}")
    while IFS= read -r key; do
        [[ -z "$key" ]] || "${REDIS[@]}" DEL "$key" > /dev/null
    done <<< "$keys"
done

echo "== 4. Redis warm-up =="
"${REDIS[@]}" SET "coupon:${COUPON_ID}:remaining" "${TOTAL_QUANTITY}" > /dev/null

echo
echo "초기화 완료"
echo "  couponId : ${COUPON_ID}"
echo "  수량      : $("${REDIS[@]}" GET "coupon:${COUPON_ID}:remaining")"
echo "  발급 이력 : $("${PSQL[@]}" -t -A -c "SELECT COUNT(*) FROM public.p_user_coupons WHERE coupon_id = '${COUPON_ID}';")"
