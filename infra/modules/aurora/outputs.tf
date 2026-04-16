output "cluster_endpoint" {
  value = aws_rds_cluster.aurora.endpoint
}

output "cluster_reader_endpoint" {
  value = aws_rds_cluster.aurora.reader_endpoint
}

output "security_group_id" {
  value = aws_security_group.aurora.id
}

output "cluster_master_user_secret_arn" {
  value = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
}
