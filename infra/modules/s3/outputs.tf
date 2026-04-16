output "bucket_name" {
  value = aws_s3_bucket.vault.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.vault.arn
}
