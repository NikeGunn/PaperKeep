output "go_backend_repo_url" {
  value = aws_ecr_repository.go_backend.repository_url
}

output "python_intelligence_repo_url" {
  value = aws_ecr_repository.python_intelligence.repository_url
}

output "repo_arns" {
  value = [
    aws_ecr_repository.go_backend.arn,
    aws_ecr_repository.python_intelligence.arn,
  ]
}
