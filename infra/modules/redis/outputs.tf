output "endpoint" {
  value = aws_elasticache_serverless_cache.redis.endpoint[0].address
}

output "port" {
  value = aws_elasticache_serverless_cache.redis.endpoint[0].port
}

output "security_group_id" {
  value = aws_security_group.redis.id
}
