#!/usr/bin/env bash
# infra-bootstrap.sh — Build Docker images, push to ECR, run Terraform
# Usage: ./scripts/infra-bootstrap.sh [staging|prod]
set -euo pipefail

ENVIRONMENT="${1:-staging}"
AWS_PROFILE="${AWS_PROFILE:-scanvault}"
AWS_REGION="ap-south-1"
ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "==> Authenticating with ECR..."
aws ecr get-login-password --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "==> Initializing Terraform..."
cd "$(dirname "$0")/../infra"
terraform init -reconfigure
terraform workspace select "$ENVIRONMENT" 2>/dev/null || terraform workspace new "$ENVIRONMENT"

echo "==> Getting ECR URLs from Terraform..."
GO_REPO=$(terraform output -raw go_backend_ecr_url 2>/dev/null || echo "${ECR_REGISTRY}/scanvault-${ENVIRONMENT}-go-backend")
PY_REPO=$(terraform output -raw python_intelligence_ecr_url 2>/dev/null || echo "${ECR_REGISTRY}/scanvault-${ENVIRONMENT}-python-intelligence")

echo "==> Building Go backend (linux/arm64)..."
docker buildx build \
  --platform linux/arm64 \
  --tag "${GO_REPO}:latest" \
  --file "$(dirname "$0")/../backend/Dockerfile" \
  --push \
  "$(dirname "$0")/../backend"

echo "==> Building Python intelligence (linux/arm64)..."
docker buildx build \
  --platform linux/arm64 \
  --tag "${PY_REPO}:latest" \
  --file "$(dirname "$0")/../intelligence/Dockerfile" \
  --push \
  "$(dirname "$0")/../intelligence"

echo "==> Applying Terraform..."
terraform apply -var="environment=${ENVIRONMENT}" -auto-approve

echo "==> Writing API base URL..."
"$(dirname "$0")/get_api_url.sh"

echo "==> Done! Environment: ${ENVIRONMENT}"
