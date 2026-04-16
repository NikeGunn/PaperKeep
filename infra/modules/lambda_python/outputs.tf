output "function_arn" {
  value = aws_lambda_function.python_intelligence.arn
}

output "function_name" {
  value = aws_lambda_function.python_intelligence.function_name
}

output "security_group_id" {
  value = aws_security_group.lambda_python.id
}
