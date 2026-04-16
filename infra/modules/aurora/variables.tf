variable "name_prefix" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "lambda_sg_id" { type = string }
variable "db_secret_arn" { type = string }
