output "invoke_url" {
  value = "${aws_apigatewayv2_api.main.api_endpoint}/v1"
}

output "api_id" {
  value = aws_apigatewayv2_api.main.id
}
