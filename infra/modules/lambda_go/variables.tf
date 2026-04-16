variable "name_prefix" { type = string }
variable "environment" { type = string }
variable "ecr_image_uri" { type = string }
variable "execution_role_arn" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "db_secret_arn" { type = string }
variable "aurora_endpoint" { type = string }
variable "s3_bucket_name" { type = string }
variable "paseto_secret_arn" { type = string }
variable "ip_hash_secret_arn" { type = string }
variable "database_url" { type = string }
variable "paseto_key" {
  type      = string
  sensitive = true
}
variable "ip_hash_key" {
  type      = string
  sensitive = true
}
variable "argon2_time" {
  type    = number
  default = 1
}
variable "argon2_memory" {
  type    = number
  default = 65536
}
variable "argon2_threads" {
  type    = number
  default = 4
}
variable "postmark_token" {
  type    = string
  default = "dummy-staging-token"
}
