resource "aws_iam_role" "kafka" {
  name               = "${var.name_prefix}-kafka"
  assume_role_policy = data.aws_iam_policy_document.observation_assume.json
}

resource "aws_iam_role_policy_attachment" "kafka_ssm" {
  role       = aws_iam_role.kafka.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "kafka" {
  name = "${var.name_prefix}-kafka"
  role = aws_iam_role.kafka.name
}

resource "aws_instance" "kafka" {
  ami                         = data.aws_ssm_parameter.al2023_arm.value
  instance_type               = "t4g.small"
  subnet_id                   = aws_subnet.app[local.azs[0]].id
  vpc_security_group_ids      = [aws_security_group.kafka.id]
  iam_instance_profile        = aws_iam_instance_profile.kafka.name
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
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = false
  }

  tags = {
    Name = "${var.name_prefix}-kafka"
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami]
  }
}

resource "aws_ebs_volume" "kafka" {
  availability_zone = aws_instance.kafka.availability_zone
  size              = 20
  type              = "gp3"
  encrypted         = true

  tags = {
    Name = "${var.name_prefix}-kafka-data"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "kafka" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.kafka.id
  instance_id = aws_instance.kafka.id
}

# Power state (running/stopped) is owned by the runtime stack: aws_ec2_instance_state.kafka.

resource "aws_ssm_document" "start_kafka" {
  name            = "${var.name_prefix}-start-kafka"
  document_type   = "Command"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "After NAT exists, install docker if needed and run a single KRaft Kafka broker."
    mainSteps = [
      {
        action = "aws:runShellScript"
        name   = "StartKafka"
        inputs = {
          timeoutSeconds = "900"
          runCommand = [
            templatefile("${path.module}/kafka/start-kafka.sh.tftpl", {
              kafka_image     = local.kafka_image
              advertised_host = "kafka.${aws_service_discovery_private_dns_namespace.this.name}"
              cluster_id      = "CcTestAwsKRaft0000001"
            })
          ]
        }
      }
    ]
  })
}
