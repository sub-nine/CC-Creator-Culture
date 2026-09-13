#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENVCTL="$SCRIPT_DIR/envctl.sh"
SCHEMA="$SCRIPT_DIR/schema.py"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
STATE="$TEST_ROOT/state"
SHA="0123456789abcdef0123456789abcdef01234567"
DIGEST="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
MIGRATE_DIGEST="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
SEED_DIGEST="sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
ECR="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-test"
MANIFEST_SHA="sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

cleanup() { rm -rf "$TEST_ROOT"; }
trap cleanup EXIT
mkdir -p "$FAKE_BIN" "$STATE/s3" "$STATE/taskdefs" "$STATE/services" "$STATE/rds" "$STATE/snapshots"

fail_test() { echo "$1" >&2; exit 1; }

write_config() {
  jq -n '{
    region: "ap-northeast-2",
    cluster: "cc-test",
    services: {
      "config-server":"cc-test-config",
      "eureka-server":"cc-test-eureka",
      "gateway":"cc-test-gateway",
      "user-service":"cc-test-user",
      "product-service":"cc-test-product",
      "order-service":"cc-test-order"
    },
    service_task_families: {
      "config-server":"cc-test-config-server",
      "eureka-server":"cc-test-eureka-server",
      "gateway":"cc-test-gateway",
      "user-service":"cc-test-user-service",
      "product-service":"cc-test-product-service",
      "order-service":"cc-test-order-service"
    },
    subnets: ["subnet-aaa","subnet-bbb"],
    security_groups: {
      "config-server":"sg-config",
      "eureka-server":"sg-eureka",
      "gateway":"sg-gateway",
      "user-service":"sg-user",
      "product-service":"sg-product",
      "order-service":"sg-order",
      alb:"sg-alb",
      observation:"sg-obs",
      msk:"sg-msk",
      redis:"sg-redis",
      migration:"sg-migration",
      "rds-user-service":"sg-rds-user",
      "rds-product-service":"sg-rds-product",
      "rds-order-service":"sg-rds-order"
    },
    release_bucket: "cc-test-release",
    rds_instances: {
      "user-service":"cc-test-user",
      "product-service":"cc-test-product",
      "order-service":"cc-test-order"
    },
    observation_instance_id: "i-0123456789abcdef0",
    observation_bootstrap_document: {name:"cc-test-start-observation", version:"1"},
    msk_arn: "arn:aws:kafka:ap-northeast-2:123456789012:cluster/cc-test/abc",
    redis_id: "cc-test-redis",
    target_group_arn: "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:targetgroup/gw/abc",
    db_endpoints: {
      "user-service":"user.db.example",
      "product-service":"product.db.example",
      "order-service":"order.db.example"
    },
    secret_arns: {
      rds_master: {
        "user-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user-master",
        "product-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:product-master",
        "order-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:order-master"
      },
      app: {
        "user-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:user-app",
        "product-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:product-app",
        "order-service":"arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:order-app"
      }
    }
  }' > "$1"
}

write_release() {
  local dest="$1" result="${2:-passed}"
  jq -n --arg sha "$SHA" --arg ecr "$ECR" --arg digest "$DIGEST" --arg mig "$MIGRATE_DIGEST" --arg seed "$SEED_DIGEST" --arg manifest "$MANIFEST_SHA" --arg result "$result" '{
    schema_version: 1,
    release_id: ("rel-" + $sha),
    source_sha: $sha,
    config_sha: $sha,
    main_sha: $sha,
    ci_url: "https://github.com/example/cc-service/actions/runs/1",
    tag: "v1.2.3",
    images: {
      "config-server": ($ecr + "/config-server@" + $digest),
      "eureka-server": ($ecr + "/eureka-server@" + $digest),
      "gateway": ($ecr + "/gateway@" + $digest),
      "order-service": ($ecr + "/order-service@" + $digest),
      "product-service": ($ecr + "/product-service@" + $digest),
      "user-service": ($ecr + "/user-service@" + $digest)
    },
    migrations: {
      version: "V1",
      artifact: ($ecr + "/db-migrate@" + $mig),
      images: {"db-migrate": ($ecr + "/db-migrate@" + $mig), "db-seed": ($ecr + "/db-seed@" + $seed)}
    },
    seed: {artifact: ($ecr + "/db-seed@" + $seed)},
    artifacts: {
      candidate_manifest: ("candidates/" + $sha + ".json"),
      candidate_manifest_sha256: $manifest,
      oci_verified_record: ("candidates/" + $sha + ".oci-verified.json"),
      ecr_images: ("candidates/" + $sha + ".ecr.json")
    },
    validation: {
      oci_health_url: "https://dev.example.com/actuator/health",
      oci_health_result: "passed",
      verification_run_url: "https://github.com/example/cc-service/actions/runs/99",
      verification_result: $result,
      manifest_sha256: $manifest
    }
  }' > "$dest"
}

init_taskdefs() {
  local family name
  for family in cc-test-config-server cc-test-eureka-server cc-test-gateway cc-test-user-service cc-test-product-service cc-test-order-service db-migrate db-seed; do
    case "$family" in
      cc-test-config-server) name=config-server ;;
      cc-test-eureka-server) name=eureka-server ;;
      cc-test-gateway) name=gateway ;;
      cc-test-user-service) name=user-service ;;
      cc-test-product-service) name=product-service ;;
      cc-test-order-service) name=order-service ;;
      db-migrate|db-seed) name=flyway ;;
      *) name="$family" ;;
    esac
    jq -n --arg family "$family" --arg name "$name" --arg image "$ECR/$family@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" '{
      taskDefinition: {
        family: $family,
        revision: 1,
        taskDefinitionArn: ("arn:aws:ecs:ap-northeast-2:123456789012:task-definition/" + $family + ":1"),
        status: "ACTIVE",
        cpu: "512",
        memory: "1024",
        networkMode: "awsvpc",
        requiresCompatibilities: ["FARGATE"],
        containerDefinitions: [{
          name: $name,
          image: $image,
          essential: true,
          environment: [{name:"KEEP_ME", value:"yes"}]
        }]
      }
    }' > "$STATE/taskdefs/${family}.json"
  done
  printf '%s\n' cc-test-config cc-test-eureka cc-test-gateway cc-test-user cc-test-product cc-test-order | while read -r svc; do
    family="cc-test-${svc#cc-test-}"
    case "$svc" in
      cc-test-config) family=cc-test-config-server ;;
      cc-test-eureka) family=cc-test-eureka-server ;;
      cc-test-gateway) family=cc-test-gateway ;;
      cc-test-user) family=cc-test-user-service ;;
      cc-test-product) family=cc-test-product-service ;;
      cc-test-order) family=cc-test-order-service ;;
    esac
    jq -n --arg svc "$svc" --arg td "$family:1" '{desiredCount:0, taskDefinition:$td}' > "$STATE/services/${svc}.json"
  done
  echo available > "$STATE/rds/cc-test-user"
  echo available > "$STATE/rds/cc-test-product"
  echo available > "$STATE/rds/cc-test-order"
  echo running > "$STATE/ec2-state"
  echo 0 > "$STATE/lag"
  echo 1 > "$STATE/snapshots/cc-test-user"
  echo 1 > "$STATE/snapshots/cc-test-product"
  echo 1 > "$STATE/snapshots/cc-test-order"
  echo 123456789012 > "$STATE/account"
  echo 0 > "$STATE/migrate-exit"
}

cat > "$FAKE_BIN/aws" << 'FAKE'
#!/usr/bin/env python3
import json, os, sys, pathlib, shutil, re
state = pathlib.Path(os.environ["FAKE_AWS_STATE"])
(state / "commands.log").open("a").write(" ".join(sys.argv[1:]) + "\n")
args = sys.argv[1:]
flags = {}
positionals = []
i = 0
while i < len(args):
    a = args[i]
    if a.startswith("--"):
        key = a[2:]
        if "=" in key:
            k, v = key.split("=", 1)
            flags[k] = v
        elif i + 1 < len(args) and not args[i + 1].startswith("--"):
            flags[key] = args[i + 1]
            i += 1
        else:
            flags[key] = True
    else:
        positionals.append(a)
    i += 1
for skip in ("output", "region", "color"):
    flags.pop(skip, None)

def out(data):
    json.dump(data, sys.stdout)

def s3_path(key):
    path = state / "s3" / key
    path.parent.mkdir(parents=True, exist_ok=True)
    return path

svc = positionals[0] if positionals else ""
op = positionals[1] if len(positionals) > 1 else ""

if svc == "sts" and op == "get-caller-identity":
    account = (state / "account").read_text().strip()
    out({"Account": account, "Arn": f"arn:aws:sts::{account}:assumed-role/cc-test-gha-runtime-deploy/session", "UserId": "AIDATEST"})
    raise SystemExit(0)
if svc == "s3api" and op == "head-object":
    raise SystemExit(0 if s3_path(flags["key"]).is_file() else 1)
if svc == "s3api" and op == "list-objects-v2":
    prefix = flags.get("prefix", "")
    contents = []
    root = state / "s3"
    if root.exists():
        for path in root.rglob("*"):
            if path.is_file():
                key = str(path.relative_to(root))
                if not prefix or key.startswith(prefix):
                    contents.append({"Key": key})
    out({"Contents": contents} if contents else {})
    raise SystemExit(0)
if svc == "s3api" and op == "get-object":
    src = s3_path(flags["key"])
    dest = positionals[-1]
    if not src.is_file():
        raise SystemExit(1)
    shutil.copyfile(src, dest)
    out({"ETag": "ok"})
    raise SystemExit(0)
if svc == "s3api" and op == "put-object":
    shutil.copyfile(flags["body"], s3_path(flags["key"]))
    out({"ETag": "ok"})
    raise SystemExit(0)
if svc == "ecs" and op == "describe-task-definition":
    td = flags["task-definition"]
    family = td.split("/")[-1].split(":")[0]
    path = state / "taskdefs" / f"{family}.json"
    if not path.is_file():
        raise SystemExit(1)
    data = json.loads(path.read_text())
    if ":" in td.split("/")[-1]:
        rev = int(td.split(":")[-1])
        data["taskDefinition"]["revision"] = rev
    json.dump(data, sys.stdout)
    raise SystemExit(0)
if svc == "ecs" and op == "register-task-definition":
    raw = flags["cli-input-json"]
    path = raw[7:] if raw.startswith("file://") else raw
    taskdef = json.loads(pathlib.Path(path).read_text())
    family = taskdef["family"]
    store = state / "taskdefs" / f"{family}.json"
    rev = 1
    if store.is_file():
        rev = json.loads(store.read_text())["taskDefinition"].get("revision", 1) + 1
    if os.environ.get("FAIL_REGISTER") == family:
        raise SystemExit(1)
    taskdef["revision"] = rev
    taskdef["taskDefinitionArn"] = f"arn:aws:ecs:ap-northeast-2:123456789012:task-definition/{family}:{rev}"
    store.write_text(json.dumps({"taskDefinition": taskdef}))
    json.dump({"taskDefinition": taskdef}, sys.stdout)
    raise SystemExit(0)
if svc == "ecs" and op == "describe-services":
    name = flags["services"]
    data = json.loads((state / "services" / f"{name}.json").read_text())
    td = data["taskDefinition"]
    arn = f"arn:aws:ecs:ap-northeast-2:123456789012:task-definition/{td}"
    rollout = "IN_PROGRESS" if os.environ.get("FAIL_ROLLOUT") == name else "COMPLETED"
    running = 0 if rollout == "IN_PROGRESS" else int(data.get("desiredCount", 0))
    out({"services": [{"serviceName": name, "desiredCount": data["desiredCount"], "taskDefinition": td, "deployments": [{"status": "PRIMARY", "taskDefinition": arn, "rolloutState": rollout, "desiredCount": data["desiredCount"], "runningCount": running, "failedTasks": 0}]}]}) 
    raise SystemExit(0)
if svc == "ecs" and op == "update-service":
    name = flags["service"]
    path = state / "services" / f"{name}.json"
    data = json.loads(path.read_text()) if path.is_file() else {}
    if "task-definition" in flags:
        data["taskDefinition"] = flags["task-definition"]
    if "desired-count" in flags:
        data["desiredCount"] = int(flags["desired-count"])
    path.write_text(json.dumps(data))
    out({"service": data})
    raise SystemExit(0)
if svc == "ecs" and op == "wait":
    raise SystemExit(0)
if svc == "ecs" and op == "run-task":
    out({"tasks": [{"taskArn": "arn:aws:ecs:ap-northeast-2:123456789012:task/cc-test/migrate1"}]})
    raise SystemExit(0)
if svc == "ecs" and op == "describe-tasks":
    code = int((state / "migrate-exit").read_text().strip())
    out({"tasks": [{"lastStatus": "STOPPED", "containers": [{"name": "flyway", "exitCode": code}]}]}) 
    raise SystemExit(0)
if svc == "rds" and op == "describe-db-instances":
    ident = flags["db-instance-identifier"]
    status = (state / "rds" / ident).read_text().strip()
    out({"DBInstances": [{"DBInstanceIdentifier": ident, "DBInstanceStatus": status}]})
    raise SystemExit(0)
if svc == "rds" and op == "start-db-instance":
    ident = flags["db-instance-identifier"]
    (state / "rds" / ident).write_text("available\n")
    out({})
    raise SystemExit(0)
if svc == "rds" and op == "stop-db-instance":
    ident = flags["db-instance-identifier"]
    (state / "rds" / ident).write_text("stopped\n")
    out({})
    raise SystemExit(0)
if svc == "rds" and op == "describe-db-snapshots":
    ident = flags["db-instance-identifier"]
    present = (state / "snapshots" / ident).is_file()
    snaps = [{"Status": "available"}] if present else []
    out({"DBSnapshots": snaps})
    raise SystemExit(0)
if svc == "ec2" and op == "describe-instances":
    out({"Reservations": [{"Instances": [{"State": {"Name": (state / "ec2-state").read_text().strip()}}]}]}) 
    raise SystemExit(0)
if svc == "ec2" and op == "start-instances":
    (state / "ec2-state").write_text("running\n")
    out({})
    raise SystemExit(0)
if svc == "ec2" and op == "stop-instances":
    (state / "ec2-state").write_text("stopped\n")
    out({})
    raise SystemExit(0)
if svc == "application-autoscaling" and op == "describe-scalable-targets":
    rid = flags.get("resource-ids") or flags.get("resource-id") or "x"
    path = state / "asg" / rid.replace("/", "_")
    if path.is_file():
        data = json.loads(path.read_text())
    else:
        data = {"ResourceId": rid, "MinCapacity": 0, "MaxCapacity": 0}
    out({"ScalableTargets": [data]})
    raise SystemExit(0)
if svc == "application-autoscaling" and op == "register-scalable-target":
    rid = flags.get("resource-id") or flags.get("resource-ids") or "x"
    path = state / "asg" / rid.replace("/", "_")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"ResourceId": rid, "MinCapacity": int(str(flags.get("min-capacity", "0")).replace("True","0")), "MaxCapacity": int(str(flags.get("max-capacity", "0")).replace("True","0"))}))
    out({})
    raise SystemExit(0)
if svc == "elbv2" and op == "describe-target-health":
    out({"TargetHealthDescriptions": [{"TargetHealth": {"State": "healthy"}}]})
    raise SystemExit(0)
if svc == "kafka" and op == "describe-cluster":
    out({"ClusterInfo": {"ClusterName": "cc-test", "State": "ACTIVE"}})
    raise SystemExit(0)
if svc == "elasticache" and op in {"describe-replication-groups", "describe-cache-clusters"}:
    out({"ReplicationGroups": [{"Status": "available"}], "CacheClusters": [{"CacheClusterStatus": "available"}]})
    raise SystemExit(0)
if svc == "cloudwatch" and op == "list-metrics":
    if os.environ.get("LAG_NO_METRICS") == "1":
        out({"Metrics": []})
        raise SystemExit(0)
    out({"Metrics": [{"Namespace": "AWS/Kafka", "MetricName": "SumOffsetLag", "Dimensions": [{"Name": "Cluster Name", "Value": "cc-test"}, {"Name": "Consumer Group", "Value": "g1"}, {"Name": "Topic", "Value": "order.paid"}]}]})
    raise SystemExit(0)
if svc == "cloudwatch" and op == "get-metric-data":
    lag = float((state / "lag").read_text().strip())
    values = [lag] if (state / "lag").is_file() and os.environ.get("LAG_MISSING") != "1" else []
    out({"MetricDataResults": [{"Id": "lag", "Values": values}]})
    raise SystemExit(0)
if svc == "ssm" and op == "describe-instance-information":
    out({"InstanceInformationList": [{"InstanceId": "i-0123456789abcdef0", "PingStatus": "Online"}]})
    raise SystemExit(0)
if svc == "ssm" and op == "send-command":
    out({"Command": {"CommandId": "cmd-obs-1"}})
    raise SystemExit(0)
if svc == "ssm" and op == "get-command-invocation":
    out({"Status": "Success", "CommandId": flags.get("command-id", "cmd-obs-1")})
    raise SystemExit(0)
print("unhandled aws", args, file=sys.stderr)
raise SystemExit(2)
FAKE
chmod 755 "$FAKE_BIN/aws"

cat > "$FAKE_BIN/terraform" << 'FAKE'
#!/usr/bin/env bash
set -Eeuo pipefail
echo "terraform $*" >> "$FAKE_AWS_STATE/commands.log"
chdir=""
if [[ "${1:-}" == -chdir=* ]]; then
  chdir="${1#-chdir=}"
  shift
fi
case "${1:-}" in
  destroy) echo "destroy blocked" >&2; exit 1 ;;
  show)
    jq -n '{format_version:"1.2", resource_changes:[{address:"aws_nat_gateway.runtime", type:"aws_nat_gateway", change:{actions:["create"]}}]}'
    ;;
  apply)
    echo applied >> "$FAKE_AWS_STATE/commands.log"
    ;;
  output)
    if [[ -f "$FAKE_AWS_STATE/tf-output.json" ]]; then
      cat "$FAKE_AWS_STATE/tf-output.json"
      exit 0
    fi
    exit 1
    ;;
  *) exit 0 ;;
esac
FAKE
chmod 755 "$FAKE_BIN/terraform"

export PATH="$FAKE_BIN:$PATH"
export AWS_BIN="$FAKE_BIN/aws"
export TF_BIN="$FAKE_BIN/terraform"
export FAKE_AWS_STATE="$STATE"
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=ap-northeast-2
export ENVCTL_WAIT_ATTEMPTS=2
export ENVCTL_WAIT_SLEEP=0
export OPERATION_ID=testop

run_ctl() {
  ENVCTL_ALLOW_LIVE=true AWS_BIN="$AWS_BIN" TF_BIN="$TF_BIN" FAKE_AWS_STATE="$STATE" \
    bash "$ENVCTL" "$@"
}

init_taskdefs
write_config "$TEST_ROOT/config.json"
write_release "$TEST_ROOT/release.json"

python3 "$SCHEMA" config "$TEST_ROOT/config.json"

jq '.security_groups = {app:"sg-app", alb:"sg-alb", rds:"sg-rds", observation:"sg-obs", msk:"sg-msk", redis:"sg-redis"}' \
  "$TEST_ROOT/config.json" > "$TEST_ROOT/sg-app-only.json"
if python3 "$SCHEMA" config "$TEST_ROOT/sg-app-only.json" >/dev/null 2>"$TEST_ROOT/sg-app-only.err"; then
  fail_test "shared app SG must be rejected"
fi
grep -Fq "security_groups.config-server" "$TEST_ROOT/sg-app-only.err"

jq 'del(.region)' "$TEST_ROOT/config.json" > "$TEST_ROOT/no-region.json"
if python3 "$SCHEMA" config "$TEST_ROOT/no-region.json" >/dev/null 2>&1; then
  fail_test "missing region must fail"
fi

jq -n '{taskDefinition:{family:"x",containerDefinitions:[
  {name:"sidecar",image:"old@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",essential:true,environment:[{name:"KEEP_ME",value:"yes"}]},
  {name:"user-service",image:"old@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",essential:true,environment:[{name:"KEEP_ME",value:"yes"}]}
]}}' > "$TEST_ROOT/td.json"
python3 "$SCHEMA" rewrite-taskdef "$ECR/user-service@$DIGEST" user-service < "$TEST_ROOT/td.json" > "$TEST_ROOT/td.out"
jq -e --arg img "$ECR/user-service@$DIGEST" '
  .containerDefinitions[1].image == $img
  and .containerDefinitions[1].environment[0].value == "yes"
  and .containerDefinitions[0].image != $img
' "$TEST_ROOT/td.out" >/dev/null

if python3 "$SCHEMA" rewrite-taskdef "$ECR/user-service@$DIGEST" < "$TEST_ROOT/td.json" >/dev/null 2>&1; then
  fail_test "two-container rewrite without name must fail"
fi

write_release "$TEST_ROOT/unverified.json" failed
if run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/unverified.json" >/dev/null 2>"$TEST_ROOT/unverified.err"; then
  fail_test "unverified release must fail"
fi
grep -Fq "verification_result passed" "$TEST_ROOT/unverified.err"
[[ ! -f "$STATE/s3/runtime/current.json" ]]

if ENVCTL_ALLOW_LIVE=false AWS_BIN="$AWS_BIN" bash "$ENVCTL" --action start --config "$TEST_ROOT/config.json" >/dev/null 2>"$TEST_ROOT/nolive.err"; then
  fail_test "live mutation must be blocked"
fi
grep -Fq "ENVCTL_ALLOW_LIVE" "$TEST_ROOT/nolive.err"

if run_ctl --action start --config "$TEST_ROOT/config.json" >/dev/null 2>"$TEST_ROOT/nocurrent.err"; then
  fail_test "start without current must fail"
fi
grep -Fq "runtime/current.json" "$TEST_ROOT/nocurrent.err"
grep -E 'seed|flush|restore-db-instance|create-cache-cluster' "$STATE/commands.log" && fail_test "start must not init data" || true

: > "$STATE/commands.log"
if run_ctl --action create --config "$TEST_ROOT/config.json" >/dev/null 2>"$TEST_ROOT/noplan.err"; then
  fail_test "create without plan must fail"
fi
grep -Fq -- "--plan" "$TEST_ROOT/noplan.err"

printf 'fakeplan' > "$TEST_ROOT/saved.tfplan"
run_ctl --action create --config "$TEST_ROOT/config.json" --plan "$TEST_ROOT/saved.tfplan" --terraform-dir "$REPO_ROOT/infra/terraform/aws/bootstrap" >/dev/null
grep -F "terraform -chdir=$REPO_ROOT/infra/terraform/aws/bootstrap apply -input=false -auto-approve $TEST_ROOT/saved.tfplan" "$STATE/commands.log"
grep -E 'terraform .*destroy' "$STATE/commands.log" && fail_test "create must not destroy" || true

: > "$STATE/commands.log"
run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null
[[ -f "$STATE/s3/runtime/current.json" ]]
[[ ! -f "$STATE/s3/runtime/previous.json" ]]
for logical in config-server eureka-server gateway user-service product-service order-service; do
  key=SPRING_CLOUD_CONFIG_LABEL
  [[ "$logical" != config-server ]] || key=CONFIG_GIT_DEFAULT_LABEL
  jq -e --arg key "$key" --arg sha "$SHA" '.taskDefinition.containerDefinitions[0].environment | any(.name == $key and .value == $sha)' "$STATE/taskdefs/cc-test-$logical.json" >/dev/null
done
run_task_count="$(grep -c 'ecs run-task' "$STATE/commands.log")"
[[ "$run_task_count" -eq 3 ]] || fail_test "deploy must migrate 3 DBs, got $run_task_count"
grep -F "sg-migration" "$STATE/commands.log"
python3 - "$STATE/taskdefs/db-migrate.json" <<'CHK'
import json,sys
td=json.load(open(sys.argv[1]))["taskDefinition"]
env={e["name"]:e["value"] for e in td["containerDefinitions"][0]["environment"]}
assert env["SERVICE"]=="order", env
assert env["DB_HOST"]=="order.db.example", env
assert env["DB_NAME"]=="order_db", env
print("migrate env ok")
CHK
grep -F "seed" "$STATE/commands.log" | grep -v bootstrap | grep -v db-seed && fail_test "default deploy must not seed" || true
awk '/ecs update-service/ {print}' "$STATE/commands.log" | grep -q cc-test-config
awk '/ecs update-service/ {print}' "$STATE/commands.log" | grep -q cc-test-gateway
# first success then second deploy writes previous
write_release "$TEST_ROOT/release.json"
run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null
[[ -f "$STATE/s3/runtime/previous.json" ]]

# first failure: no current after wiping, migrate fails, no current written
rm -f "$STATE/s3/runtime/current.json" "$STATE/s3/runtime/previous.json" "$STATE/s3/selected/manifest.json"
echo 1 > "$STATE/migrate-exit"
: > "$STATE/commands.log"
if run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null 2>"$TEST_ROOT/firstfail.err"; then
  fail_test "failed migrate must fail deploy"
fi
[[ ! -f "$STATE/s3/runtime/current.json" ]]
[[ -f "$STATE/s3/operations/failures/testop.json" ]]
grep -F "ecs update-service" "$STATE/commands.log" && fail_test "failed migrate must not update services" || true

# rollback path: current exists, register fails on gateway after migrate success
echo 0 > "$STATE/migrate-exit"
run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null
cp "$STATE/s3/runtime/current.json" "$TEST_ROOT/before-fail.json"
export FAIL_REGISTER=cc-test-gateway
: > "$STATE/commands.log"
if run_ctl --action deploy --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null 2>"$TEST_ROOT/rollback.err"; then
  fail_test "gateway register failure must fail"
fi
unset FAIL_REGISTER
cmp -s "$STATE/s3/runtime/current.json" "$TEST_ROOT/before-fail.json" || fail_test "current must stay on failure"
grep -Fq "Restoring the last successful" "$TEST_ROOT/rollback.err" || grep -Fq "update-service" "$STATE/commands.log"

: > "$STATE/commands.log"
run_ctl --action bootstrap-roles --config "$TEST_ROOT/config.json" --release "$TEST_ROOT/release.json" >/dev/null
[[ -f "$STATE/s3/runtime/roles-bootstrapped.json" ]]
python3 - "$STATE/taskdefs/db-seed.json" <<'CHK'
import json,sys
td=json.load(open(sys.argv[1]))["taskDefinition"]
c=td["containerDefinitions"][0]
env={e["name"]:e["value"] for e in c["environment"]}
sec={s["name"]:s["valueFrom"] for s in c["secrets"]}
assert env["SERVICE"]=="order"
assert "ADMIN_PASSWORD" in sec and "rds-master" not in sec.get("FLYWAY_PASSWORD","")
assert sec["ADMIN_PASSWORD"].endswith("order-master:password::")
assert sec["APP_PASSWORD"].endswith("order-app:password::")
print("bootstrap secrets ok")
CHK
jq '.msk_arn = "arn:aws:kafka:ap-northeast-2:123456789012:cluster/new/xyz"' "$TEST_ROOT/config.json" > "$TEST_ROOT/config-rebind.json"
: > "$STATE/commands.log"
run_ctl --action start --config "$TEST_ROOT/config-rebind.json" >"$TEST_ROOT/rebind.out" 2>"$TEST_ROOT/rebind.err"
grep -q 'Rebinding last successful images' "$TEST_ROOT/rebind.err"
grep -q 'ecs register-task-definition' "$STATE/commands.log"

run_ctl --action stop --config "$TEST_ROOT/config.json" >"$TEST_ROOT/stop2.out" 2>"$TEST_ROOT/stop2.err" || true
grep -E 'MSK|parked at 0' "$TEST_ROOT/stop2.out" "$TEST_ROOT/stop2.err" >/dev/null

run_ctl --action teardown --config "$TEST_ROOT/config.json" --plan "$TEST_ROOT/saved.tfplan" --terraform-dir "$REPO_ROOT/infra/terraform/aws/bootstrap" >"$TEST_ROOT/nc.out" 2>"$TEST_ROOT/nc.err" || true
grep -q 'confirm-data-preserved' "$TEST_ROOT/nc.err"

echo "envctl tests passed."

