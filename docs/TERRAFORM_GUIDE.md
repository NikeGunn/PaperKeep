# ScanVault — Terraform Operations Guide

> For DevOps engineers and solo developers managing the AWS infrastructure.

---

## Quick Reference

| Command | Purpose |
|---|---|
| `terraform init` | Initialize (run once, or after provider changes) |
| `terraform plan` | Preview changes |
| `terraform apply` | Apply changes to AWS |
| `terraform destroy` | Tear down all resources (DANGER) |
| `terraform output` | Show infrastructure URLs/values |
| `terraform state list` | List all managed resources |
| `terraform import` | Bring manually-created resources under Terraform management |

---

## Setup (One-Time)

### 1. Install Prerequisites

```bash
# Terraform v1.13+
winget install HashiCorp.Terraform   # Windows
brew install terraform               # macOS

# AWS CLI v2
winget install Amazon.AWSCLI         # Windows

# Verify
terraform version
aws --version
```

### 2. Configure AWS Credentials

```bash
# Option A: Environment variables (recommended for CI/CD)
export AWS_ACCESS_KEY_ID="AKIAVA5YLHOHGVOQRYOE"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="ap-south-1"

# Option B: AWS profile (for local dev)
aws configure --profile scanvault
# Enter: Access Key, Secret Key, Region: ap-south-1, Output: json
```

### 3. Configure Terraform CLI (REQUIRED on Windows — fixes IPv6 registry issue)

The file `%APPDATA%\terraform.rc` is already configured on Nikhil's machine.
On a new machine, create `%APPDATA%\terraform.rc` (Windows) or `~/.terraformrc` (Linux/macOS):

```hcl
provider_installation {
  filesystem_mirror {
    path    = "C:/Users/YOUR_USER/Desktop/ScanVault/infra/.terraform/providers"
    include = ["registry.terraform.io/hashicorp/aws", "registry.terraform.io/hashicorp/random"]
  }
  direct {
    exclude = ["registry.terraform.io/hashicorp/aws", "registry.terraform.io/hashicorp/random"]
  }
}
```

> **Why:** On Windows, Terraform uses IPv6 to reach `registry.terraform.io`, which may be dropped by some ISPs.
> The filesystem mirror forces Terraform to use already-downloaded providers — no registry network call needed.
> If providers aren't downloaded yet, delete the `filesystem_mirror` block, run `terraform init` once on a working network, then add the block back.

### 4. Create terraform.tfvars

```bash
cd infra/
cp terraform.tfvars.example terraform.tfvars
# Edit with real values — see "Secrets" section below
```

### 5. Initialize

```bash
cd infra/
terraform init \
  -backend-config="access_key=${AWS_ACCESS_KEY_ID}" \
  -backend-config="secret_key=${AWS_SECRET_ACCESS_KEY}" \
  -backend-config="region=ap-south-1" \
  -backend-config="bucket=scanvault-tfstate" \
  -backend-config="key=scanvault/terraform.tfstate" \
  -reconfigure
```

---

## Day-to-Day Operations

### Check Current State

```bash
cd infra/

# Show all outputs (API URL, S3 bucket, ECR URLs)
terraform output

# Show specific output
terraform output api_gateway_url

# List all resources Terraform manages
terraform state list

# Show details of a resource
terraform state show module.lambda_go.aws_lambda_function.go_backend
```

### Make Infrastructure Changes

```bash
# Always preview first
terraform plan -var-file=terraform.tfvars

# Apply changes
terraform apply -var-file=terraform.tfvars

# Target a single module (faster for isolated changes)
terraform apply -var-file=terraform.tfvars -target=module.api_gateway
```

### After Manual AWS Console/CLI Changes

If you made changes directly in AWS (like we did for DB access during bootstrap), sync state:

```bash
# Import a resource that exists in AWS but not in state
terraform import -var-file=terraform.tfvars \
  module.api_gateway.aws_apigatewayv2_stage.default \
  "API_GATEWAY_ID/\$default"

# Refresh state from real AWS
terraform refresh -var-file=terraform.tfvars
```

---

## Secrets Management

Sensitive values are in `infra/terraform.tfvars` (gitignored). To get the values:

```bash
# DB password
aws secretsmanager get-secret-value \
  --secret-id scanvault-staging/db-password \
  --query SecretString --output text

# Paseto key
aws secretsmanager get-secret-value \
  --secret-id scanvault-staging/paseto-key \
  --query SecretString --output text

# IP hash key
aws secretsmanager get-secret-value \
  --secret-id scanvault-staging/ip-hash-key \
  --query SecretString --output text

# URL-encode DB password (special chars in passwords break connection strings)
python3 -c "import urllib.parse; print(urllib.parse.quote('RAW_PASSWORD', safe=''))"
```

### DATABASE_URL format
```
postgresql://scanvault_app:URL_ENCODED_PASS@AURORA_ENDPOINT:5432/scanvault?sslmode=require
```

---

## Cost Management

### Current Staging Infrastructure (optimized 2026-04-16)

**Target: under $10/month.** Actual estimate: **~$1.30–$3.50/month** (dev-only, zero users).

| Service | Config | Est. Monthly Cost |
|---|---|---|
| **Aurora Serverless v2** | PostgreSQL 15.10, **0–2 ACU** | **~$0 idle**, ~$0.09/ACU-hr when active |
| ~~ElastiCache Serverless~~ | **DELETED** (in-memory rate limiter used instead) | ~~$15+/mo~~ **$0** |
| **Lambda Go** | arm64, 512MB, 30s timeout | ~$0/mo (free tier: 1M req + 400K GB-s) |
| **Lambda Python** | arm64, 2048MB, 120s timeout | ~$0/mo (rarely invoked in dev) |
| **API Gateway HTTP** | $1/million requests | ~$0/mo (free: first 300M req/12 months) |
| **S3** | Vault storage + versioning | ~$0.02/mo (5GB free tier) |
| **ECR** | Go + Python images (~50MB each) | ~$0.10/mo |
| **Secrets Manager** | **3 secrets** (db, paseto, ip-hash) | **$1.20/mo** |
| **CloudWatch Logs** | 7-day retention | ~$0/mo (5GB free tier) |
| **VPC Endpoints** | S3 Gateway only (**FREE**) | **$0** |
| **Data Transfer** | Lambda→Aurora (free within VPC) | $0 |

**Total Staging Estimate: ~$1.30–$3.50/month** ← down from $112+/month before optimization

### What was deleted to cut costs

| Deleted resource | Saved per month |
|---|---|
| ElastiCache Serverless Redis | ~$15+ |
| 4× VPC Interface Endpoints (secretsmanager, elasticache, ecr, ec2) | ~$57.60 |
| captcha-key Secrets Manager secret | $0.40 |
| Aurora min ACU 0.5 → 0 | ~$32 |

### Free Tier Reminders

- Lambda: 1M requests/month free (forever, not just 12 months)
- S3: 5GB + 20k GET ops + 2k PUT ops free (first 12 months)
- CloudWatch: 5GB logs ingestion free (forever)
- API Gateway HTTP: 300M requests free first 12 months

### Cost Rules for This Project

1. **REDIS_URL is optional** — never re-add ElastiCache just for dev. Only add back when Phase 4C needs real Redis queue (async task processing). When you do, add a VPC Gateway endpoint (free) or keep Redis in same subnet.
2. **Aurora pauses after 5 min idle** — first request of the day has ~5–8s cold start. That's fine for dev.
3. **No VPC Interface endpoints until prod** — they cost $0.01/AZ/hr regardless of usage. Use them only for compliance/security at prod scale.

### Check Actual Costs

```bash
# Monthly cost by service (Cost Explorer — needs ~24h lag)
aws ce get-cost-and-usage \
  --time-period Start=2026-05-01,End=2026-05-31 \
  --granularity MONTHLY \
  --metrics "BlendedCost" \
  --group-by Type=DIMENSION,Key=SERVICE \
  --region us-east-1 \
  --query 'ResultsByTime[0].Groups[].{Service:Keys[0],Cost:Metrics.BlendedCost.Amount}' \
  --output table
```

### Set a Budget Alert

```bash
aws budgets create-budget \
  --account-id 345594608526 \
  --budget '{
    "BudgetName": "scanvault-staging-alert",
    "BudgetLimit": {"Amount": "50", "Unit": "USD"},
    "TimeUnit": "MONTHLY",
    "BudgetType": "COST"
  }' \
  --notifications-with-subscribers '[{
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 80
    },
    "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "knewboy.nykhil@gmail.com"}]
  }]'
```

---

## Architecture Overview

```
ap-south-1 (Mumbai)
├── VPC (10.0.0.0/16) — no NAT gateway (cost saving)
│   ├── subnet private-a (10.0.1.0/24) — ap-south-1a
│   ├── subnet private-b (10.0.2.0/24) — ap-south-1b
│   ├── VPC Endpoint: S3 (Gateway — FREE)
│   └── VPC Endpoint: Secrets Manager (Interface — $7.20/mo)
│
├── Aurora Serverless v2 (PostgreSQL 15.10)
│   ├── Cluster: scanvault-staging-aurora
│   ├── Instance: scanvault-staging-aurora-instance
│   ├── Min 0.5 ACU / Max 2 ACU
│   └── Private only (no public endpoint)
│
├── ElastiCache Serverless (Redis)
│   └── scanvault-staging-redis
│
├── Lambda Functions
│   ├── scanvault-staging-go-backend (arm64, 512MB, 30s)
│   └── scanvault-staging-python-intelligence (arm64, 2048MB, 120s)
│
├── API Gateway HTTP API
│   ├── scanvault-staging-api
│   ├── Stage: $default (no URL prefix)
│   └── Route: $default → Lambda Go (proxy all)
│
├── ECR Repositories
│   ├── scanvault-staging-go-backend
│   └── scanvault-staging-python-intelligence
│
├── S3 Bucket
│   └── scanvault-staging-vault-203a9e83
│
└── Secrets Manager
    ├── scanvault-staging/db-password
    ├── scanvault-staging/paseto-key
    ├── scanvault-staging/ip-hash-key
    └── scanvault-staging/captcha-key
```

---

## Database Operations

Aurora is in a private VPC — you cannot connect directly. Options:

### Option A: Run Migrations via psql (requires temporary public access)

```bash
# 1. Add your IP to Aurora security group
MY_IP=$(curl -s https://api.ipify.org)
aws ec2 authorize-security-group-ingress \
  --group-id sg-053529fe7d0d74d85 \
  --protocol tcp --port 5432 --cidr "${MY_IP}/32" \
  --region ap-south-1

# 2. Add internet route to private route table
aws ec2 create-route \
  --route-table-id rtb-0d7ff48b51c71ad89 \
  --destination-cidr-block 0.0.0.0/0 \
  --gateway-id igw-032ed358c3b15986b \
  --region ap-south-1

# 3. Make Aurora instance publicly accessible
aws rds modify-db-instance \
  --db-instance-identifier scanvault-staging-aurora-instance \
  --publicly-accessible --apply-immediately --region ap-south-1

# Wait ~1 minute, then connect
ADMIN_PASS=$(aws secretsmanager get-secret-value \
  --secret-id 'rds!cluster-83ef463d-3f03-4035-a6d0-38e0071ee67c' \
  --query 'SecretString' --output text | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['password'])")

PGPASSWORD="$ADMIN_PASS" psql \
  "host=scanvault-staging-aurora.cluster-cjk6c26cyw2o.ap-south-1.rds.amazonaws.com \
   port=5432 user=scanvault_admin dbname=scanvault sslmode=require"

# 4. After migrations, CLEAN UP (re-lock Aurora):
aws ec2 revoke-security-group-ingress \
  --group-id sg-053529fe7d0d74d85 \
  --protocol tcp --port 5432 --cidr "${MY_IP}/32" --region ap-south-1

aws ec2 delete-route \
  --route-table-id rtb-0d7ff48b51c71ad89 \
  --destination-cidr-block 0.0.0.0/0 --region ap-south-1

aws rds modify-db-instance \
  --db-instance-identifier scanvault-staging-aurora-instance \
  --no-publicly-accessible --apply-immediately --region ap-south-1
```

### Option B: Run goose Migrations (after public access is enabled)

```bash
# URL-encode admin password first
ADMIN_PASS_ENCODED=$(python3 -c "import urllib.parse; print(urllib.parse.quote('ADMIN_PASS', safe=''))")
DB_URL="postgresql://scanvault_admin:${ADMIN_PASS_ENCODED}@AURORA_ENDPOINT:5432/scanvault?sslmode=require"

cd backend/
/c/Users/Nautilus/go/bin/goose -dir db/migrations postgres "$DB_URL" up
```

---

## CI/CD Deployment

The `backend-deploy-aws.yml` workflow auto-deploys on push to main.

**Required GitHub Secrets:**
```
AWS_ACCESS_KEY_ID     → AKIAVA5YLHOHGVOQRYOE
AWS_SECRET_ACCESS_KEY → (from ScanVault_accessKeys.csv)
```

Set them at: GitHub repo → Settings → Secrets and variables → Actions

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `terraform init` fails — IPv6 timeout | Windows uses IPv6 for registry.terraform.io | Ensure `%APPDATA%\terraform.rc` has filesystem_mirror block |
| Lambda 500 error | Placeholder image / missing env vars | Check CloudWatch: `/aws/lambda/scanvault-staging-go-backend` |
| Lambda 404 for all routes | API Gateway stage prefix mismatch | Ensure stage is `$default`, not `v1` |
| DB auth failure | `scanvault_app` user doesn't exist | Use Option A above to connect and `CREATE ROLE scanvault_app WITH LOGIN PASSWORD '...'` |
| `terraform apply` — 409 stage exists | Manually created stage not in state | `terraform import module.api_gateway.aws_apigatewayv2_stage.default "API_ID/\$default"` |
| `terraform apply` — state drift | Manual changes made via AWS CLI/Console | `terraform refresh -var-file=terraform.tfvars` then re-apply |

---

## Key Resource IDs (Staging)

| Resource | ID / ARN |
|---|---|
| API Gateway | `4dbidumnq3` |
| API URL | `https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com` |
| VPC | `vpc-0ee370c25d06c7f10` |
| Aurora Cluster | `scanvault-staging-aurora` |
| Aurora Security Group | `sg-053529fe7d0d74d85` |
| Private Route Table | `rtb-0d7ff48b51c71ad89` |
| Internet Gateway | `igw-032ed358c3b15986b` |
| S3 Bucket | `scanvault-staging-vault-203a9e83` |
| Terraform State | `s3://scanvault-tfstate/scanvault/terraform.tfstate` |
| AWS Account | `345594608526` |
| Region | `ap-south-1` (Mumbai) |
