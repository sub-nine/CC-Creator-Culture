resource "aws_instance" "observation" {
  ami                         = data.aws_ssm_parameter.al2023_arm.value
  instance_type               = "t4g.medium"
  subnet_id                   = aws_subnet.app[local.azs[0]].id
  vpc_security_group_ids      = [aws_security_group.observation.id]
  iam_instance_profile        = aws_iam_instance_profile.observation.name
  associate_public_ip_address = false

  credit_specification {
    cpu_credits = "standard"
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size           = 50
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = false
  }

  tags = {
    Name = "${var.name_prefix}-observation"
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami]
  }
}

# Power state (running/stopped) is owned by the runtime stack: aws_ec2_instance_state.observation.

resource "aws_ssm_document" "start_observation" {
  name            = "${var.name_prefix}-start-observation"
  document_type   = "Command"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "After NAT exists, install docker if needed and run Prometheus, Grafana, Zipkin."
    mainSteps = [
      {
        action = "aws:runShellScript"
        name   = "StartObservation"
        inputs = {
          timeoutSeconds = "900"
          runCommand = [
            templatefile("${path.module}/observation/start-observation.sh.tftpl", {
              prometheus_image   = local.observation_images["PROMETHEUS_IMAGE"]
              grafana_image      = local.observation_images["GRAFANA_IMAGE"]
              zipkin_image       = local.observation_images["ZIPKIN_IMAGE"]
              grafana_secret_arn = aws_secretsmanager_secret.grafana.arn
              cloudmap_namespace = aws_service_discovery_private_dns_namespace.this.name
              name_prefix        = var.name_prefix
              k6_dashboard       = base64encode(jsonencode(jsondecode(file("${path.module}/../../../../docker/grafana/provisioning/dashboards/k6-prometheus.json"))))
              dashboard_provider = file("${path.module}/../../../../docker/grafana/provisioning/dashboards/dashboards.yml")
              prometheus_source  = replace(file("${path.module}/../../../../deploy/grafana/provisioning/datasources/prometheus.yml"), "http://prometheus:9090", "http://127.0.0.1:9090")
            })
          ]
        }
      }
    ]
  })
}
