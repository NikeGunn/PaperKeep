output "lambda_go_role_arn" {
  value = aws_iam_role.lambda_go.arn
}

output "lambda_python_role_arn" {
  value = aws_iam_role.lambda_python.arn
}

output "lambda_go_role_name" {
  value = aws_iam_role.lambda_go.name
}

output "eventbridge_scheduler_role_arn" {
  value = aws_iam_role.eventbridge_scheduler.arn
}
