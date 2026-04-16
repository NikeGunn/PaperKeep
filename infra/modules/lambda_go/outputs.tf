output "function_arn" {
  value = aws_lambda_function.go_backend.arn
}

output "invoke_arn" {
  value = aws_lambda_function.go_backend.invoke_arn
}

output "function_name" {
  value = aws_lambda_function.go_backend.function_name
}

output "security_group_id" {
  value = aws_security_group.lambda_go.id
}

output "function_url" {
  value = aws_lambda_function_url.go_backend.function_url
}
