#!/usr/bin/env python3
from pathlib import Path
import json
import subprocess
import tempfile

root = Path(__file__).resolve().parents[4]
schema = root / "deploy" / "aws" / "schema.py"
apps = [
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
]
dbs = ["user-service", "product-service", "order-service"]
config = {
    "region": "ap-northeast-2",
    "cluster": "cc-test",
    "services": {name: f"cc-test-{name}" for name in apps},
    "service_task_families": {name: f"cc-test-{name}" for name in apps},
    "subnets": ["subnet-example-a", "subnet-example-b"],
    "security_groups": {
        **{name: f"sg-app-{name}" for name in apps},
        "alb": "sg-alb",
        "observation": "sg-observation",
        "msk": "sg-msk",
        "redis": "sg-redis",
        **{f"rds-{name}": f"sg-rds-{name}" for name in dbs},
    },
    "release_bucket": "example-release-bucket",
    "rds_instances": {name: f"cc-test-{name}" for name in dbs},
    "observation_instance_id": "i-example",
    "msk_arn": "arn:aws:kafka:ap-northeast-2:123:cluster/cc-test-kafka/abcd",
    "redis_id": "cc-test-redis",
    "target_group_arn": "arn:aws:elasticloadbalancing:ap-northeast-2:123:targetgroup/cc-test-gateway/abcd",
    "db_endpoints": {name: f"{name}.example.rds.amazonaws.com" for name in dbs},
    "secret_arns": {
        "jwt": "arn:aws:secretsmanager:ap-northeast-2:123:secret:jwt",
        "redis": "arn:aws:secretsmanager:ap-northeast-2:123:secret:redis",
        "kafka": {name: f"arn:aws:secretsmanager:ap-northeast-2:123:secret:msk-{name}" for name in dbs},
        "app": {name: f"arn:aws:secretsmanager:ap-northeast-2:123:secret:app-{name}" for name in dbs},
        "rds_master": {name: f"arn:aws:secretsmanager:ap-northeast-2:123:secret:rds-{name}" for name in dbs},
    },
}
with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as handle:
    json.dump(config, handle)
    path = handle.name
subprocess.run(["python3", str(schema), "config", path], check=True)
print("deployment_config schema test passed.")
