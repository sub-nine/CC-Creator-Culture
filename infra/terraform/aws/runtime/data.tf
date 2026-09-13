data "aws_msk_bootstrap_brokers" "this" {
  cluster_arn = aws_msk_cluster.this.arn
}

data "aws_instance" "observation" {
  instance_id = var.persistent_config.observation_instance_id
}
