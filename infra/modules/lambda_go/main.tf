resource "aws_security_group" "lambda_go" {
  name        = "${var.name_prefix}-lambda-go"
  description = "Go backend Lambda"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-lambda-go-sg" }
}

resource "aws_lambda_function" "go_backend" {
  function_name = "${var.name_prefix}-go-backend"
  package_type  = "Image"
  image_uri     = "${var.ecr_image_uri}:latest"
  role          = var.execution_role_arn
  architectures = ["arm64"]
  memory_size   = 512
  timeout       = 30

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [aws_security_group.lambda_go.id]
  }

  environment {
    variables = {
      ENVIRONMENT        = var.environment
      DATABASE_URL       = var.database_url
      PASETO_KEY         = var.paseto_key
      ARGON2_TIME        = tostring(var.argon2_time)
      ARGON2_MEMORY      = tostring(var.argon2_memory)
      ARGON2_THREADS     = tostring(var.argon2_threads)
      IP_HASH_KEY        = var.ip_hash_key
      POSTMARK_TOKEN     = var.postmark_token
      S3_BUCKET_NAME     = var.s3_bucket_name
      DB_SECRET_ARN      = var.db_secret_arn
      PASETO_SECRET_ARN  = var.paseto_secret_arn
      IP_HASH_SECRET_ARN = var.ip_hash_secret_arn
      SERVER_PORT        = "8080"
      AWS_REGION_NAME    = "ap-south-1"
    }
  }

  tracing_config {
    mode = "Active"
  }

  lifecycle {
    ignore_changes = [image_uri] # Updated by CI/CD, not Terraform
  }

  tags = { Name = "${var.name_prefix}-go-backend" }
}

# Lambda URL for direct invocation (API Gateway handles HTTP)
resource "aws_lambda_function_url" "go_backend" {
  function_name      = aws_lambda_function.go_backend.function_name
  authorization_type = "NONE"
  cors {
    allow_origins = ["*"]
    allow_methods = ["*"]
    allow_headers = ["*"]
  }
}

resource "aws_cloudwatch_log_group" "go_backend" {
  name              = "/aws/lambda/${aws_lambda_function.go_backend.function_name}"
  retention_in_days = var.environment == "prod" ? 30 : 7
}
