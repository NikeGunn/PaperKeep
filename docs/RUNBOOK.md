# ScanVault — Operations Runbook

> **Audience:** On-call engineers and the solo dev (Nikhil).
> **Stack:** AWS Lambda + Aurora Serverless v2 + ElastiCache Serverless + S3 (ap-south-1)
> **API:** `https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com/v1`

---

## 1. Lambda Cold Start Troubleshooting

**Symptoms:** First request after idle > 5 min takes 2–10s. Subsequent requests are fast.

**Checks:**
```bash
# View Lambda init duration in CloudWatch
aws logs filter-log-events \
  --log-group-name /aws/lambda/scanvault-staging-go-backend \
  --filter-pattern "INIT_START" \
  --query "events[*].message" \
  --output text \
  --profile scanvault

# Check if provisioned concurrency is needed
aws lambda get-function-concurrency \
  --function-name scanvault-staging-go-backend \
  --profile scanvault
```

**Fixes:**
- Enable Provisioned Concurrency (1 unit) if cold starts > 3s: `aws lambda put-provisioned-concurrency-config`
- Reduce image size: ensure Go binary is stripped (`-ldflags="-s -w"`)
- Arm64 cold starts are faster — confirm Lambda uses arm64 architecture

**Thresholds:** Cold start < 2s = acceptable. > 5s = investigate image size.

---

## 2. Aurora Serverless v2 Scaling

**Min/Max ACU:** 0.5 / 2.0 (staging). Scales to near-zero when idle.

**Symptoms of scaling issue:** Connection timeouts, "connection refused" errors after idle.

**Checks:**
```bash
# Check current ACU usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name ServerlessDatabaseCapacity \
  --dimensions Name=DBClusterIdentifier,Value=scanvault-staging-aurora \
  --start-time $(date -u -d '1 hour ago' +%FT%TZ) \
  --end-time $(date -u +%FT%TZ) \
  --period 60 \
  --statistics Average \
  --profile scanvault

# View slow query log
aws logs filter-log-events \
  --log-group-name /aws/rds/cluster/scanvault-staging-aurora/postgresql \
  --filter-pattern "duration" \
  --profile scanvault
```

**Fixes:**
- If Aurora takes > 30s to resume from idle, set min ACU to 1.0 in Terraform
- If connections exhausted: Lambda uses pgx/v5 pool — verify `max_connections` (Aurora default ~max_connections based on ACU)
- Connection string format: `postgres://user:pass@host:5432/dbname?sslmode=require`

**Thresholds:** P99 query < 100ms. If > 500ms, add index or increase ACU.

---

## 3. ElastiCache Serverless (Redis) Issues

**Endpoint:** `scanvault-staging-redis-wuu4iy.serverless.aps1.cache.amazonaws.com:6379`

**Symptoms:** Rate limiting not working, session cache misses, Redis timeout errors.

**Checks:**
```bash
# Test connectivity from Lambda (use Lambda test invoke)
aws lambda invoke \
  --function-name scanvault-staging-go-backend \
  --payload '{"rawPath":"/v1/health/deep","requestContext":{"http":{"method":"GET","path":"/v1/health/deep"}}}' \
  --profile scanvault \
  output.json && cat output.json

# Check ElastiCache metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ElastiCache \
  --metric-name CurrConnections \
  --dimensions Name=CacheClusterId,Value=scanvault-staging-redis \
  --start-time $(date -u -d '1 hour ago' +%FT%TZ) \
  --end-time $(date -u +%FT%TZ) \
  --period 60 \
  --statistics Average \
  --profile scanvault
```

**Fixes:**
- If Redis unreachable: check Lambda VPC security group allows port 6379 to ElastiCache SG
- If timeouts: increase `dial_timeout` in REDIS_URL (default 5s)
- If memory eviction: ElastiCache Serverless auto-scales; check MaxCacheSize in Terraform

**REDIS_URL format:** `redis://host:6379` (no password for ElastiCache Serverless with IAM auth)

---

## 4. S3 Access Problems

**Bucket:** `scanvault-staging-vault-203a9e83`

**Symptoms:** 403 Forbidden on presigned URLs, upload failures, missing objects.

**Checks:**
```bash
# Verify Lambda execution role has S3 permissions
aws iam simulate-principal-policy \
  --policy-source-arn arn:aws:iam::345594608526:role/scanvault-staging-lambda-go-role \
  --action-names s3:GetObject s3:PutObject s3:DeleteObject \
  --resource-arns "arn:aws:s3:::scanvault-staging-vault-203a9e83/*" \
  --profile scanvault

# List recent objects
aws s3 ls s3://scanvault-staging-vault-203a9e83/vault/ --recursive --profile scanvault | tail -20

# Check bucket policy
aws s3api get-bucket-policy --bucket scanvault-staging-vault-203a9e83 --profile scanvault
```

**Fixes:**
- If 403 on presigned URL: URL may have expired (5 min TTL). Client must request a new URL.
- If Lambda can't write: check `S3_BUCKET_NAME` env var matches actual bucket name
- If objects missing: check `processing/` prefix lifecycle — objects auto-deleted after 1 day

**Processing prefix cleanup:** EventBridge rule triggers daily at 02:00 IST (UTC+5:30).

---

## 5. Rollback Procedure

### Lambda Rollback (Go backend)
```bash
# List available versions
aws lambda list-versions-by-function \
  --function-name scanvault-staging-go-backend \
  --profile scanvault \
  --query 'Versions[*].{Version:Version,Modified:LastModified}' \
  --output table

# Roll back to previous ECR image
# Get previous image digest from ECR
aws ecr describe-images \
  --repository-name scanvault-staging-go-backend \
  --profile scanvault \
  --query 'imageDetails[*].{Digest:imageDigest,Pushed:imagePushedAt}' \
  --output table

# Update Lambda to previous image
aws lambda update-function-code \
  --function-name scanvault-staging-go-backend \
  --image-uri 345594608526.dkr.ecr.ap-south-1.amazonaws.com/scanvault-staging-go-backend@sha256:<previous-digest> \
  --profile scanvault
```

### Database Rollback
```bash
# Aurora automated backups: restore to point in time
aws rds restore-db-cluster-to-point-in-time \
  --db-cluster-identifier scanvault-staging-aurora-restored \
  --source-db-cluster-identifier scanvault-staging-aurora \
  --restore-to-time $(date -u -d '1 hour ago' +%FT%TZ) \
  --profile scanvault

# Run down migrations (goose)
goose -dir backend/db/migrations postgres "$DATABASE_URL" down
```

### Terraform Rollback
```bash
cd infra
git checkout HEAD~1 -- .
terraform plan -var="environment=staging"
terraform apply -var="environment=staging" -auto-approve
```

---

## 6. Daily Monitoring Checklist

Run this every morning (takes < 5 minutes):

```bash
#!/usr/bin/env bash
# Daily health check

REGION="ap-south-1"
PROFILE="scanvault"
API="https://4dbidumnq3.execute-api.ap-south-1.amazonaws.com/v1"

echo "=== ScanVault Daily Health ==="
echo ""

# 1. API health
echo "1. API health..."
curl -sf "$API/health" | python -m json.tool || echo "  ❌ API health FAILED"

# 2. Deep health
echo "2. Deep health..."
curl -sf "$API/v1/health/deep" | python -m json.tool || echo "  ❌ Deep health FAILED"

# 3. Lambda error rate (last 24h)
echo "3. Lambda errors (last 24h)..."
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/Lambda \
  --metric-name Errors \
  --dimensions Name=FunctionName,Value=scanvault-staging-go-backend \
  --start-time $(date -u -d '24 hours ago' +%FT%TZ) \
  --end-time $(date -u +%FT%TZ) \
  --period 86400 \
  --statistics Sum \
  --profile $PROFILE \
  --query 'Datapoints[0].Sum' --output text

# 4. Aurora connection count
echo "4. Aurora connections..."
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --dimensions Name=DBClusterIdentifier,Value=scanvault-staging-aurora \
  --start-time $(date -u -d '1 hour ago' +%FT%TZ) \
  --end-time $(date -u +%FT%TZ) \
  --period 3600 \
  --statistics Maximum \
  --profile $PROFILE \
  --query 'Datapoints[0].Maximum' --output text

echo ""
echo "=== Done ==="
```

**Items to check:**
- [ ] `/health` returns `{"status":"ok"}`
- [ ] `/v1/health/deep` all components "up"
- [ ] Lambda error count < 10 in 24h
- [ ] No CloudWatch alarms in ALARM state
- [ ] Aurora ACU < 1.5 during off-peak
- [ ] S3 bucket size trending as expected

---

## 7. Crash Rate Thresholds

| Metric | Green | Yellow (investigate) | Red (page) |
|--------|-------|---------------------|------------|
| Lambda error rate | < 0.1% | 0.1% – 1% | > 1% |
| API P99 latency | < 500ms | 500ms – 2s | > 2s |
| 5xx responses (1h) | < 5 | 5 – 50 | > 50 |
| Aurora CPU | < 40% | 40% – 70% | > 70% |
| Aurora ACU | < 1.5 | 1.5 – 1.9 | = 2.0 (at max) |
| Android crash-free sessions | > 99.5% | 99% – 99.5% | < 99% |

**CloudWatch Alarms already configured:**
- `scanvault-staging-high-lambda-errors` — triggers at > 10 errors in 5 min
- `scanvault-staging-high-latency` — triggers at P99 > 2000ms

**Response escalation:**
1. Yellow → check CloudWatch logs, identify root cause
2. Red → rollback immediately, then investigate
3. Data loss risk → stop writes, snapshot Aurora, page Nikhil

---

## 8. Useful Commands Reference

```bash
# Tail Lambda logs live
aws logs tail /aws/lambda/scanvault-staging-go-backend \
  --follow --format short --profile scanvault

# Invoke Lambda directly (test)
aws lambda invoke \
  --function-name scanvault-staging-go-backend \
  --payload file://test-event.json \
  --profile scanvault output.json

# Update Lambda env vars
aws lambda update-function-configuration \
  --function-name scanvault-staging-go-backend \
  --environment "Variables={KEY=value}" \
  --profile scanvault

# Force Lambda cold start (useful for testing)
aws lambda update-function-configuration \
  --function-name scanvault-staging-go-backend \
  --description "force-cold-start-$(date +%s)" \
  --profile scanvault

# Check ECR image sizes
aws ecr describe-images \
  --repository-name scanvault-staging-go-backend \
  --profile scanvault \
  --query 'sort_by(imageDetails,&imagePushedAt)[-5:].{Size:imageSizeInBytes,Pushed:imagePushedAt}' \
  --output table
```
