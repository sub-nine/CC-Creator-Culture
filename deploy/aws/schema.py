#!/usr/bin/env python3
# Validate Terraform deployment_config and rewrite one container image.
# Release records: .github/scripts/validate-aws-release-record.sh

from __future__ import annotations

import json
import re
import sys

DIGEST = re.compile(r"@sha256:[0-9a-f]{64}$")
ARN = re.compile(r"^arn:aws:")
INSTANCE_ID = re.compile(r"^i-[0-9a-z]+$")

APP_SERVICES = (
    "config-server",
    "eureka-server",
    "gateway",
    "user-service",
    "product-service",
    "order-service",
)
DB_SERVICES = ("user-service", "product-service", "order-service")
SHARED_SG = ("alb", "observation", "msk", "redis")
CONFIG_REQUIRED = (
    "region",
    "cluster",
    "services",
    "service_task_families",
    "subnets",
    "security_groups",
    "release_bucket",
    "rds_instances",
    "observation_instance_id",
    "msk_arn",
    "redis_id",
    "target_group_arn",
)
STRIP_TASKDEF = (
    "taskDefinitionArn",
    "revision",
    "status",
    "requiresAttributes",
    "compatibilities",
    "registeredAt",
    "registeredBy",
    "deregisteredAt",
    "enableFaultInjection",
    "tags",
)


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def load(path: str) -> object:
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"unable to read JSON: {exc}")


def require_object(value: object, name: str) -> dict:
    if not isinstance(value, dict):
        fail(f"{name} must be an object")
    return value


def require_str(obj: dict, key: str, name: str) -> str:
    value = obj.get(key)
    if not isinstance(value, str) or not value.strip():
        fail(f"{name}.{key} must be a non-empty string")
    return value


def require_str_map(obj: dict, key: str, names: tuple[str, ...], name: str) -> dict:
    value = obj.get(key)
    if not isinstance(value, dict):
        fail(f"{name}.{key} must be an object")
    for item in names:
        mapped = value.get(item)
        if not isinstance(mapped, str) or not mapped.strip():
            fail(f"{name}.{key}.{item} must be a non-empty string")
    return value


def require_str_list(obj: dict, key: str, name: str) -> list:
    value = obj.get(key)
    if not isinstance(value, list) or not value:
        fail(f"{name}.{key} must be a non-empty list")
    for item in value:
        if not isinstance(item, str) or not item.strip():
            fail(f"{name}.{key} must contain only non-empty strings")
    return value


def _nonempty_str(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate_security_groups(config: dict) -> None:
    groups = config.get("security_groups")
    if not isinstance(groups, dict):
        fail("deployment_config.security_groups must be an object")
    for key in APP_SERVICES + SHARED_SG:
        if not _nonempty_str(groups.get(key)):
            fail(f"deployment_config.security_groups.{key} must be a non-empty string")
    nested = groups.get("rds")
    prefixed_ok = all(_nonempty_str(groups.get(f"rds-{name}")) for name in DB_SERVICES)
    nested_ok = isinstance(nested, dict) and all(_nonempty_str(nested.get(name)) for name in DB_SERVICES)
    if not prefixed_ok and not nested_ok:
        fail("deployment_config.security_groups must include rds-<db-service> keys or a rds map")
    if "migration" in groups and not _nonempty_str(groups.get("migration")):
        fail("deployment_config.security_groups.migration must be a non-empty string")


def validate_config(data: object) -> None:
    config = require_object(data, "deployment_config")
    for key in CONFIG_REQUIRED:
        if key not in config:
            fail(f"deployment_config missing {key}")
    require_str(config, "region", "deployment_config")
    require_str(config, "cluster", "deployment_config")
    require_str(config, "release_bucket", "deployment_config")
    require_str(config, "redis_id", "deployment_config")
    require_str_list(config, "subnets", "deployment_config")
    require_str_map(config, "services", APP_SERVICES, "deployment_config")
    require_str_map(config, "service_task_families", APP_SERVICES, "deployment_config")
    validate_security_groups(config)
    require_str_map(config, "rds_instances", DB_SERVICES, "deployment_config")
    observation = require_str(config, "observation_instance_id", "deployment_config")
    if not INSTANCE_ID.match(observation):
        fail("deployment_config.observation_instance_id must be an instance id")
    if not ARN.match(require_str(config, "msk_arn", "deployment_config")):
        fail("deployment_config.msk_arn must be an ARN")
    if not ARN.match(require_str(config, "target_group_arn", "deployment_config")):
        fail("deployment_config.target_group_arn must be an ARN")
    if "db_endpoints" in config:
        require_str_map(config, "db_endpoints", DB_SERVICES, "deployment_config")
    if "secret_arns" in config:
        secrets = config["secret_arns"]
        if not isinstance(secrets, dict):
            fail("deployment_config.secret_arns must be an object")
        seed = secrets.get("seed")
        if seed is not None and (not isinstance(seed, str) or not seed.startswith("arn:aws:secretsmanager:")):
            fail("deployment_config.secret_arns.seed must be a Secrets Manager ARN")



def select_container(containers: list, container_name: str | None) -> int:
    if container_name:
        matches = [i for i, item in enumerate(containers) if item.get("name") == container_name]
        if len(matches) != 1:
            fail(f"container {container_name} must exist exactly once")
        return matches[0]
    essentials = [i for i, item in enumerate(containers) if item.get("essential", True)]
    if len(essentials) != 1:
        fail("task definition must contain exactly one essential container")
    return essentials[0]


def oneoff_taskdef(image: str, spec_path: str) -> None:
    if not DIGEST.search(image):
        fail("replacement image must use an immutable sha256 digest")
    spec = require_object(load(spec_path), "oneoff spec")
    payload = json.load(sys.stdin)
    taskdef = payload.get("taskDefinition", payload)
    if not isinstance(taskdef, dict):
        fail("task definition must be an object")
    for key in STRIP_TASKDEF:
        taskdef.pop(key, None)
    containers = taskdef.get("containerDefinitions")
    if not isinstance(containers, list) or not containers:
        fail("task definition must contain containerDefinitions")
    index = select_container(containers, spec.get("container") if isinstance(spec.get("container"), str) else None)
    container = containers[index]
    container["image"] = image
    env_items = {item.get("name"): item for item in container.get("environment") or [] if isinstance(item, dict) and item.get("name")}
    environment = spec.get("environment") or {}
    if not isinstance(environment, dict):
        fail("oneoff spec.environment must be an object")
    for key, value in environment.items():
        if not isinstance(key, str) or not isinstance(value, str):
            fail("oneoff environment values must be strings")
        env_items[key] = {"name": key, "value": value}
    container["environment"] = list(env_items.values())
    secret_items = {item.get("name"): item for item in container.get("secrets") or [] if isinstance(item, dict) and item.get("name")}
    secrets = spec.get("secrets") or {}
    if not isinstance(secrets, dict):
        fail("oneoff spec.secrets must be an object")
    for key, arn in secrets.items():
        if not isinstance(key, str) or not isinstance(arn, str) or not ARN.match(arn):
            fail("oneoff secrets must map names to ARNs")
        secret_items[key] = {"name": key, "valueFrom": arn}
    if secret_items:
        container["secrets"] = list(secret_items.values())
    if "entrypoint" in spec:
        entrypoint = spec["entrypoint"]
        if not isinstance(entrypoint, list) or any(not isinstance(item, str) for item in entrypoint):
            fail("oneoff spec.entrypoint must be a list of strings")
        container["entryPoint"] = entrypoint
    json.dump(taskdef, sys.stdout)


def rewrite_taskdef(image: str, container_name: str | None, config_sha: str | None = None) -> None:
    if not DIGEST.search(image):
        fail("replacement image must use an immutable sha256 digest")
    payload = json.load(sys.stdin)
    taskdef = payload.get("taskDefinition", payload)
    if not isinstance(taskdef, dict):
        fail("task definition must be an object")
    for key in STRIP_TASKDEF:
        taskdef.pop(key, None)
    containers = taskdef.get("containerDefinitions")
    if not isinstance(containers, list) or not containers:
        fail("task definition must contain containerDefinitions")
    if container_name:
        matches = [i for i, item in enumerate(containers) if item.get("name") == container_name]
        if len(matches) != 1:
            fail(f"container {container_name} must exist exactly once")
        index = matches[0]
    else:
        essentials = [i for i, item in enumerate(containers) if item.get("essential", True)]
        if len(essentials) != 1:
            fail("task definition must contain exactly one essential container")
        index = essentials[0]
    containers[index]["image"] = image
    if config_sha is not None:
        if not re.fullmatch(r"[0-9a-f]{40}", config_sha):
            fail("config_sha must be a full lowercase Git SHA")
        container = containers[index]
        key = "CONFIG_GIT_DEFAULT_LABEL" if container_name == "config-server" else "SPRING_CLOUD_CONFIG_LABEL"
        env = {item["name"]: item for item in container.get("environment", [])}
        env.pop("CONFIG_REPO_PATH", None)
        env[key] = {"name": key, "value": config_sha}
        container["environment"] = list(env.values())
    json.dump(taskdef, sys.stdout)


def main() -> None:
    if len(sys.argv) < 2:
        fail("Usage: schema.py config <file> | rewrite-taskdef <image> [container-name [config-sha]] | oneoff-taskdef <image> <spec.json>")
    command = sys.argv[1]
    if command == "config":
        if len(sys.argv) != 3:
            fail("Usage: schema.py config <file>")
        validate_config(load(sys.argv[2]))
        return
    if command == "rewrite-taskdef":
        if len(sys.argv) not in {3, 4, 5}:
            fail("Usage: schema.py rewrite-taskdef <image> [container-name [config-sha]]")
        name = sys.argv[3] if len(sys.argv) >= 4 else None
        rewrite_taskdef(sys.argv[2], name, sys.argv[4] if len(sys.argv) == 5 else None)
        return
    if command == "oneoff-taskdef":
        if len(sys.argv) != 4:
            fail("Usage: schema.py oneoff-taskdef <image> <spec.json>")
        oneoff_taskdef(sys.argv[2], sys.argv[3])
        return
    fail("Usage: schema.py config <file> | rewrite-taskdef <image> [container-name [config-sha]] | oneoff-taskdef <image> <spec.json>")


if __name__ == "__main__":
    main()
