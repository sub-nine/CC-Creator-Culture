#!/bin/sh
# 사용법:
#   load-test/scripts/run-aws.sh run <scenario-path> [k6-args...]
#   load-test/scripts/run-aws.sh stop <task-arn>
#
# terraform output -json k6_runner 의 키는 정확히 다음과 같아야 한다:
#   region, cluster, task_definition, subnet_ids, security_group_id,
#   base_url, embedding_url, prometheus_url
# 환경변수:
#   TERRAFORM_DIR   (기본 infra/terraform/aws/runtime)
#   CONTAINER_NAME  컨테이너 이름 (기본 k6). exitCode 조회에만 사용한다.
#   TESTID          미지정 시 타임스탬프
#   WAIT_TIMEOUT    완료 대기 상한 초 (기본 3600). 초과 시 자동 중단하지 않고
#                   태스크는 계속 실행될 수 있으므로 stop 서브커맨드로 직접 중단한다.
#   POLL_INTERVAL   describe-tasks 폴링 간격 초 (기본 15)
set -eu
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

TERRAFORM_DIR="${TERRAFORM_DIR:-infra/terraform/aws/runtime}"
CONTAINER_NAME="${CONTAINER_NAME:-k6}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-3600}"
POLL_INTERVAL="${POLL_INTERVAL:-15}"
for value in "$WAIT_TIMEOUT" "$POLL_INTERVAL"; do
  case "$value" in ''|*[!0-9]*) echo "Timeout/interval must be positive integers" >&2; exit 2 ;; esac
  [ "$value" -gt 0 ] || { echo "Timeout/interval must be positive integers" >&2; exit 2; }
done

usage() {
  echo "사용법: $0 run <scenario-path> [k6-args...] | stop <task-arn>" >&2
  exit 2
}

for tool in terraform aws jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "필요한 명령이 없습니다: $tool" >&2; exit 2; }
done

CMD="${1:-}"
[ -n "$CMD" ] || usage
case "$CMD" in
  run|stop) ;;
  *) usage ;;
esac

OUTPUT_JSON="$(terraform -chdir="$TERRAFORM_DIR" output -json k6_runner)"

# shellcheck disable=SC2016 # jq variables, not shell interpolation
KEY_CHECK='. as $o | type == "object" and all([ "region", "cluster", "task_definition", "subnet_ids", "security_group_id", "base_url", "embedding_url", "prometheus_url" ][]; . as $key | $o | has($key) and ($o[$key] | length > 0)) and ($o.subnet_ids | type == "array" and length > 0 and all(.[]; type == "string"))'

if ! echo "$OUTPUT_JSON" | jq -e "$KEY_CHECK" >/dev/null; then
  echo "k6_runner output 형식이 잘못되었습니다 (필수 키: region, cluster, task_definition, subnet_ids, security_group_id, base_url, embedding_url, prometheus_url)" >&2
  exit 2
fi

REGION="$(echo "$OUTPUT_JSON" | jq -r '.region')"
CLUSTER="$(echo "$OUTPUT_JSON" | jq -r '.cluster')"
TASK_DEF="$(echo "$OUTPUT_JSON" | jq -r '.task_definition')"
SECURITY_GROUP="$(echo "$OUTPUT_JSON" | jq -r '.security_group_id')"
BASE_URL="$(echo "$OUTPUT_JSON" | jq -r '.base_url' | sed 's|/$||')"
EMBEDDING_URL="$(echo "$OUTPUT_JSON" | jq -r '.embedding_url' | sed 's|/$||')"
PROM_URL="$(echo "$OUTPUT_JSON" | jq -r '.prometheus_url')"
SUBNETS_CSV="$(echo "$OUTPUT_JSON" | jq -r '.subnet_ids | join(",")')"

if [ "$CMD" = "stop" ]; then
  TASK_ARN="${2:-}"
  [ -n "$TASK_ARN" ] || usage
  case "$TASK_ARN" in arn:aws:ecs:*) ;; *) echo "task ARN 형식이 아닙니다: $TASK_ARN" >&2; exit 2 ;; esac
  aws --region "$REGION" ecs stop-task --cluster "$CLUSTER" --task "$TASK_ARN"
  exit 0
fi

SCENARIO="${2:-}"
[ -n "$SCENARIO" ] || usage
shift 2 || true

case "$SCENARIO" in
  scenarios/*.js) ;;
  *) echo "시나리오는 load-test 기준 scenarios/*.js 경로여야 합니다: $SCENARIO" >&2; exit 2 ;;
esac
case "$SCENARIO" in
  *..*|*//*) echo "경로 순회가 포함되어 있습니다: $SCENARIO" >&2; exit 2 ;;
esac
[ -f "load-test/$SCENARIO" ] || { echo "Scenario not found: $SCENARIO" >&2; exit 2; }

TESTID="${TESTID:-$(date +%s)}"
EMBED_URL="${EMBEDDING_URL}/embed"
SMOKE_URL="${EMBEDDING_URL}/docs"

K6_COMMAND="$(jq -cn '["run", "--out", "experimental-prometheus-rw", "--tag", ("testid=" + $testid), $scenario] + $ARGS.positional' --arg testid "$TESTID" --arg scenario "$SCENARIO" --args -- "$@")"
NETWORK_CONFIG="$(jq -cn '{awsvpcConfiguration: {subnets: ($subnets | split(",")), securityGroups: [$sg], assignPublicIp: "DISABLED"}}' --arg subnets "$SUBNETS_CSV" --arg sg "$SECURITY_GROUP")"
OVERRIDES="$(jq -cn '{containerOverrides: [{name: $container, command: $k6command, environment: [{name: "TESTID", value: $testid}, {name: "TARGET", value: "prod"}, {name: "BASE_URL", value: $baseUrl}, {name: "EMBED_URL", value: $embedUrl}, {name: "SMOKE_URL", value: $smokeUrl}, {name: "K6_PROMETHEUS_RW_SERVER_URL", value: $promUrl}, {name: "K6_PROMETHEUS_RW_TREND_STATS", value: "avg,min,med,max,p(90),p(95),p(99)"}]}]}' --arg testid "$TESTID" --arg container "$CONTAINER_NAME" --arg baseUrl "$BASE_URL" --arg embedUrl "$EMBED_URL" --arg smokeUrl "$SMOKE_URL" --arg promUrl "$PROM_URL" --argjson k6command "$K6_COMMAND")"

RUN_JSON="$(aws --region "$REGION" ecs run-task --cluster "$CLUSTER" --task-definition "$TASK_DEF" --launch-type FARGATE --network-configuration "$NETWORK_CONFIG" --overrides "$OVERRIDES")"

FAILURE_COUNT="$(echo "$RUN_JSON" | jq '.failures | length')"
TASK_ARN="$(echo "$RUN_JSON" | jq -r '.tasks[0].taskArn // empty')"

if [ "$FAILURE_COUNT" != "0" ]; then
  echo "run-task 실패:" >&2
  echo "$RUN_JSON" | jq '.failures' >&2
  exit 1
fi
[ -n "$TASK_ARN" ] || { echo "task ARN을 확인할 수 없습니다." >&2; exit 1; }

echo "TASK_ARN=$TASK_ARN"
echo "TESTID=$TESTID (로그: CloudWatch stdout, 지표: Prometheus remote write, 태그: testid=$TESTID)"
echo "참고: 타임아웃 시에도 태스크를 자동 중단하지 않는다. 중단이 필요하면 직접 실행: $0 stop $TASK_ARN"

START="$(date +%s)"
TASK_JSON=""
while :; do
  TASK_JSON="$(aws --region "$REGION" ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK_ARN")"
  DESCRIBE_FAILURES="$(echo "$TASK_JSON" | jq '.failures | length')"
  if [ "$DESCRIBE_FAILURES" != "0" ]; then
    echo "describe-tasks 실패:" >&2
    echo "$TASK_JSON" | jq '.failures' >&2
    exit 1
  fi
  [ "$(echo "$TASK_JSON" | jq '.tasks | length')" = 1 ] || { echo "Task not found; use the printed ARN to investigate" >&2; exit 1; }
  STATUS="$(echo "$TASK_JSON" | jq -r '.tasks[0].lastStatus // empty')"
  if [ "$STATUS" = "STOPPED" ]; then
    break
  fi
  NOW="$(date +%s)"
  if [ $((NOW - START)) -ge "$WAIT_TIMEOUT" ]; then
    echo "대기 상한(${WAIT_TIMEOUT}초)을 초과했습니다. 태스크는 자동 중단되지 않고 계속 실행될 수 있다." >&2
    echo "직접 중단: $0 stop $TASK_ARN" >&2
    exit 3
  fi
  sleep "$POLL_INTERVAL"
done

EXIT_CODE="$(echo "$TASK_JSON" | jq -r --arg name "$CONTAINER_NAME" '.tasks[0].containers[] | select(.name == $name) | .exitCode // empty')"
REASON="$(echo "$TASK_JSON" | jq -r --arg name "$CONTAINER_NAME" '. as $task | .tasks[0].containers[] | select(.name == $name) | .reason // $task.tasks[0].stoppedReason // empty')"

if [ "$EXIT_CODE" = "0" ]; then
  echo "k6 정상 종료(exitCode=0). CloudWatch 로그와 Prometheus(testid=$TESTID 필터)에서 결과를 확인하세요."
else
  echo "k6 실패: exitCode=$EXIT_CODE reason=${REASON:-unknown}" >&2
  exit 1
fi
