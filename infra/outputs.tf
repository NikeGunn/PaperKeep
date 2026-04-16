output "api_gateway_url" {
  description = "API Gateway invoke URL (base URL for Android app)"
  value       = module.api_gateway.invoke_url
}

output "s3_bucket_name" {
  description = "S3 bucket name for vault storage"
  value       = module.s3.bucket_name
}

output "aurora_cluster_endpoint" {
  description = "Aurora cluster writer endpoint"
  value       = module.aurora.cluster_endpoint
  sensitive   = true
}

output "go_backend_ecr_url" {
  description = "ECR URL for Go backend image"
  value       = module.ecr.go_backend_repo_url
}

output "python_intelligence_ecr_url" {
  description = "ECR URL for Python intelligence image"
  value       = module.ecr.python_intelligence_repo_url
}
