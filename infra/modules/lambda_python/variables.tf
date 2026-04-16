variable "name_prefix" { type = string }
variable "environment" { type = string }
variable "ecr_image_uri" { type = string }
variable "execution_role_arn" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "s3_bucket_name" { type = string }
