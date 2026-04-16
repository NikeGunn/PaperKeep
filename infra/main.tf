terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
  backend "s3" {
    bucket  = "scanvault-tfstate"
    key     = "scanvault/terraform.tfstate"
    region  = "ap-south-1"
    profile = "scanvault"
    encrypt = true
  }
}

provider "aws" {
  region  = "ap-south-1"
  profile = "scanvault"
  default_tags {
    tags = {
      Project     = "scanvault"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name_prefix = "scanvault-${var.environment}"
}

module "ecr" {
  source      = "./modules/ecr"
  name_prefix = local.name_prefix
  environment = var.environment
}

module "s3" {
  source      = "./modules/s3"
  name_prefix = local.name_prefix
  environment = var.environment
}

module "secrets" {
  source      = "./modules/secrets"
  name_prefix = local.name_prefix
  environment = var.environment
}

module "iam" {
  source        = "./modules/iam"
  name_prefix   = local.name_prefix
  environment   = var.environment
  s3_bucket_arn = module.s3.bucket_arn
  secrets_arns  = module.secrets.secret_arns
  ecr_repo_arns = module.ecr.repo_arns
}

# VPC for Lambda + Aurora + Redis (minimal, no NAT gateway)
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = "${local.name_prefix}-vpc" }
}

resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "ap-south-1a"
  tags              = { Name = "${local.name_prefix}-private-a" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "ap-south-1b"
  tags              = { Name = "${local.name_prefix}-private-b" }
}

# Internet Gateway for public subnet (Lambda needs this via VPC endpoint)
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-igw" }
}

# VPC Endpoint for S3 (free — avoids NAT gateway data charges)
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.ap-south-1.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]
  tags              = { Name = "${local.name_prefix}-s3-endpoint" }
}

# VPC Endpoint for Secrets Manager
resource "aws_vpc_endpoint" "secretsmanager" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.ap-south-1.secretsmanager"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
  tags                = { Name = "${local.name_prefix}-secretsmanager-endpoint" }
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${local.name_prefix}-vpc-endpoints"
  description = "Allow Lambda to reach VPC endpoints"
  vpc_id      = aws_vpc.main.id
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-private-rt" }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

module "aurora" {
  source        = "./modules/aurora"
  name_prefix   = local.name_prefix
  environment   = var.environment
  vpc_id        = aws_vpc.main.id
  subnet_ids    = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  lambda_sg_id  = module.lambda_go.security_group_id
  db_secret_arn = module.secrets.db_secret_arn
}

module "redis" {
  source       = "./modules/redis"
  name_prefix  = local.name_prefix
  environment  = var.environment
  vpc_id       = aws_vpc.main.id
  subnet_ids   = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  lambda_sg_id = module.lambda_go.security_group_id
}

module "lambda_go" {
  source             = "./modules/lambda_go"
  name_prefix        = local.name_prefix
  environment        = var.environment
  ecr_image_uri      = module.ecr.go_backend_repo_url
  execution_role_arn = module.iam.lambda_go_role_arn
  vpc_id             = aws_vpc.main.id
  subnet_ids         = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  db_secret_arn      = module.secrets.db_secret_arn
  aurora_endpoint    = module.aurora.cluster_endpoint
  redis_endpoint     = module.redis.endpoint
  s3_bucket_name     = module.s3.bucket_name
  paseto_secret_arn  = module.secrets.paseto_secret_arn
  captcha_secret_arn = module.secrets.captcha_secret_arn
}

module "lambda_python" {
  source             = "./modules/lambda_python"
  name_prefix        = local.name_prefix
  environment        = var.environment
  ecr_image_uri      = module.ecr.python_intelligence_repo_url
  execution_role_arn = module.iam.lambda_python_role_arn
  vpc_id             = aws_vpc.main.id
  subnet_ids         = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  redis_endpoint     = module.redis.endpoint
  s3_bucket_name     = module.s3.bucket_name
}

module "api_gateway" {
  source           = "./modules/api_gateway"
  name_prefix      = local.name_prefix
  environment      = var.environment
  lambda_go_arn    = module.lambda_go.function_arn
  lambda_go_invoke = module.lambda_go.invoke_arn
}

module "cloudwatch" {
  source         = "./modules/cloudwatch"
  name_prefix    = local.name_prefix
  environment    = var.environment
  lambda_go_name = module.lambda_go.function_name
  lambda_py_name = module.lambda_python.function_name
}
