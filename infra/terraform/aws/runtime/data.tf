data "aws_instance" "observation" {
  instance_id = var.persistent_config.observation_instance_id
}
