variable "environment" {
  description = "Deployment environment (staging or prod)"
  type        = string
  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod"
  }
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "scanvault"
}

# Sensitive variables — set via TF_VAR_* env vars or terraform.tfvars (never commit secrets)
variable "database_url" {
  description = "PostgreSQL connection string for the Lambda (must be URL-encoded)"
  type        = string
  sensitive   = true
}

variable "paseto_key" {
  description = "Base64-encoded 32-byte PASETO symmetric key"
  type        = string
  sensitive   = true
}

variable "ip_hash_key" {
  description = "Base64-encoded key for IP address hashing in logs"
  type        = string
  sensitive   = true
}

variable "postmark_token" {
  description = "Postmark API token for transactional emails"
  type        = string
  sensitive   = true
  default     = "dummy-staging-token"
}
