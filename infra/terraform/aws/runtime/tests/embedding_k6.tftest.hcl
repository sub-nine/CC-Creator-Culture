# Mock providers only: no cloud API calls and no real infrastructure changes.
mock_provider "aws" {
  override_during = plan
  mock_resource "aws_acm_certificate" {
    defaults = {
      arn = "arn:aws:acm:ap-northeast-2:123456789012:certificate/00000000-0000-0000-0000-000000000000"
      domain_validation_options = [{
        domain_name          = "api.example.test", resource_record_name = "_verify.api.example.test",
        resource_record_type = "CNAME", resource_record_value = "_verify.acm-validations.aws."
      }]
    }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:targetgroup/test/1234567890123456" }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:loadbalancer/app/test/1234567890123456" }
  }
  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:ecs:ap-northeast-2:123456789012:cluster/cc-test", id = "arn:aws:ecs:ap-northeast-2:123456789012:cluster/cc-test" }
  }
  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/cc-test-mock:1" }
  }
  mock_data "aws_instance" {
    defaults = { private_ip = "10.0.10.10" }
  }
}
mock_provider "cloudflare" { override_during = plan }

variables {
  cloudflare_zone_id  = "00000000000000000000000000000000"
  redis_user_group_id = "cc-test-app"
  api_hostname        = "api.example.test"
  release_sha         = "0000000000000000000000000000000000000000"
  image_tags = {
    for name in ["config-server", "eureka-server", "gateway", "user-service", "product-service", "order-service", "embedding-service", "k6", "db-seed"] :
    name => "0000000000000000000000000000000000000000000000000000000000000000"
  }
  config_labels = {
    for name in ["config-server", "eureka-server", "gateway", "user-service", "product-service", "order-service"] :
    name => "0000000000000000000000000000000000000000"
  }
  persistent_config = {
    name_prefix                    = "cc-test", region = "ap-northeast-2", vpc_id = "vpc-12345678"
    public_subnet_ids              = ["subnet-11111111", "subnet-22222222"]
    app_subnet_ids                 = ["subnet-33333333", "subnet-44444444"]
    data_subnet_ids                = ["subnet-55555555", "subnet-66666666", "subnet-77777777"]
    public_route_table_id          = "rtb-11111111", private_route_table_id = "rtb-22222222", internet_gateway_id = "igw-11111111"
    app_ports                      = {}, management_port = 9090, zipkin_port = 9411, db_port = 5432, db_names = {}
    observation_instance_id        = "i-11111111", observation_private_ip = "10.0.10.10", observation_images = {}
    observation_bootstrap_document = { name = "cc-test-start-observation", version = "1" }
    kafka_instance_id              = "i-22222222"
    kafka_bootstrap_document       = { name = "cc-test-start-kafka", version = "1" }
    rds_instances                  = {}
    db_endpoints                   = { user-service = "user.test", product-service = "product.test", order-service = "order.test" }
    release_bucket                 = "cc-test-releases", selected_manifest_key = "selected/manifest.json"
    log_groups = {
      for name in ["config-server", "eureka-server", "gateway", "user-service", "product-service", "order-service", "embedding-service", "k6"] : name => "/ecs/cc-test/${name}"
    }
    security_groups = {
      config-server     = "sg-11111111", eureka-server = "sg-22222222", gateway = "sg-33333333"
      user-service      = "sg-44444444", product-service = "sg-55555555", order-service = "sg-66666666"
      embedding-service = "sg-77777777", k6 = "sg-88888888"
      alb               = "sg-99999999", observation = "sg-aaaaaaaa", kafka = "sg-bbbbbbbb", redis = "sg-cccccccc", migration = "sg-dddddddd"
      rds               = { user-service = "sg-eeeeeeee", product-service = "sg-eeeeeeee", order-service = "sg-eeeeeeee" }
    }
    secret_arns = {
      rds_master = {
        for name in ["user-service", "product-service", "order-service"] : name => "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:${name}-123456"
      }
      jwt     = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:jwt-123456"
      redis   = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:redis-123456"
      grafana = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:grafana-123456"
      seed    = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:seed-123456"
      r2      = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:r2-123456"
    }
    roles = {
      ecs_execution = "arn:aws:iam::123456789012:role/cc-test-ecs-execution"
      ecs_task      = "arn:aws:iam::123456789012:role/cc-test-ecs-task"
      observation   = "arn:aws:iam::123456789012:role/cc-test-observation"
    }
    cloudmap = {
      namespace_id = "ns-test", namespace_name = "cc-test.internal", hosted_zone_id = "ZTEST"
      service_ids  = { config-server = "srv-config", eureka-server = "srv-eureka", embedding-service = "srv-embedding" }
      dns_names = {
        config-server = "config-server.cc-test.internal", eureka-server = "eureka-server.cc-test.internal"
        kafka         = "kafka.cc-test.internal", embedding-service = "embedding-service.cc-test.internal"
      }
    }
    ecr_repository_urls = {
      for name in ["config-server", "eureka-server", "gateway", "user-service", "product-service", "order-service", "embedding-service"] : name => "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-test/${name}"
    }
    ecr_repository_arns = {}
    artifact_repository_urls = {
      db-seed = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-test/db-seed"
      k6      = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/cc-test/k6"
    }
    artifact_repository_arns = {}
  }
}

run "running" {
  command = plan
  variables { app_running = true }
  assert {
    condition     = aws_ecs_service.embedding.desired_count == 1 && aws_ecs_task_definition.embedding.cpu == "512" && aws_ecs_task_definition.embedding.memory == "2048"
    error_message = "Embedding must run once with 0.5 CPU and 2 GiB."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.embedding.container_definitions)[0].portMappings[0].containerPort == 8000 && !can(jsondecode(aws_ecs_task_definition.embedding.container_definitions)[0].environment)
    error_message = "Embedding must use port8000 without Java configuration."
  }
  assert {
    condition     = anytrue([for item in local.container_environment["product-service"] : item.name == "EMBEDDING_SERVICE_URL" && item.value == "http://embedding-service.cc-test.internal:8000"])
    error_message = "Product must reach the private embedding DNS name."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.k6.container_definitions)[0].command == ["version"] && aws_ecs_task_definition.k6.cpu == "1024" && aws_ecs_task_definition.k6.memory == "2048" && !contains(keys(local.ecs_service_names), "k6")
    error_message = "k6 must remain an idle one-off definition, not an always-running service."
  }
  assert {
    condition     = output.k6_runner.security_group_id == "sg-88888888" && output.k6_runner.prometheus_url == "http://10.0.10.10:9090/api/v1/write"
    error_message = "Manual runner must use the dedicated SG and private receiver."
  }
}
run "stopped" {
  command = plan
  variables { app_running = false }
  assert {
    condition     = aws_ecs_service.embedding.desired_count == 0
    error_message = "Environment stop must also stop embedding."
  }
}
