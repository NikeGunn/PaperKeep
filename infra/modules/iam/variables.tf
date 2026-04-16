variable "name_prefix" { type = string }
variable "environment" { type = string }
variable "s3_bucket_arn" { type = string }
variable "secrets_arns" { type = list(string) }
variable "ecr_repo_arns" { type = list(string) }
