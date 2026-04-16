resource "aws_security_group" "lambda_python" {
  name        = "${var.name_prefix}-lambda-python"
  description = "Python intelligence Lambda"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-lambda-python-sg" }
}

resource "aws_lambda_function" "python_intelligence" {
  function_name = "${var.name_prefix}-python-intelligence"
  package_type  = "Image"
  image_uri     = "${var.ecr_image_uri}:latest"
  role          = var.execution_role_arn
  architectures = ["arm64"]
  memory_size   = 2048
  timeout       = 120

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [aws_security_group.lambda_python.id]
  }

  environment {
    variables = {
      ENVIRONMENT    = var.environment
      S3_BUCKET_NAME = var.s3_bucket_name
      RUNTIME_MODE   = "lambda"
    }
  }

  tracing_config {
    mode = "Active"
  }

  lifecycle {
    ignore_changes = [image_uri]
  }

  tags = { Name = "${var.name_prefix}-python-intelligence" }
}

resource "aws_cloudwatch_log_group" "python_intelligence" {
  name              = "/aws/lambda/${aws_lambda_function.python_intelligence.function_name}"
  retention_in_days = var.environment == "prod" ? 30 : 7
}
