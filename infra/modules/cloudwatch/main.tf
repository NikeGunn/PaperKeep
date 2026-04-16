resource "aws_cloudwatch_log_group" "scanvault" {
  name              = "/scanvault/${var.environment}"
  retention_in_days = var.environment == "prod" ? 30 : 7
  tags              = { Name = "${var.name_prefix}-logs" }
}

resource "aws_cloudwatch_metric_alarm" "lambda_go_errors" {
  alarm_name          = "${var.name_prefix}-lambda-go-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Go backend Lambda errors > 5 in 1 minute"
  dimensions = {
    FunctionName = var.lambda_go_name
  }
  treat_missing_data = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "lambda_go_duration" {
  alarm_name          = "${var.name_prefix}-lambda-go-p99-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = 60
  extended_statistic  = "p99"
  threshold           = 25000 # 25 seconds
  alarm_description   = "Go backend p99 > 25s"
  dimensions = {
    FunctionName = var.lambda_go_name
  }
  treat_missing_data = "notBreaching"
}

resource "aws_cloudwatch_dashboard" "scanvault" {
  dashboard_name = "${var.name_prefix}-overview"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title   = "Lambda Invocations"
          region  = "ap-south-1"
          period  = 60
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", var.lambda_go_name],
            ["AWS/Lambda", "Invocations", "FunctionName", var.lambda_py_name],
          ]
          view = "timeSeries"
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Lambda Errors"
          region  = "ap-south-1"
          period  = 60
          metrics = [
            ["AWS/Lambda", "Errors", "FunctionName", var.lambda_go_name],
            ["AWS/Lambda", "Errors", "FunctionName", var.lambda_py_name],
          ]
          view = "timeSeries"
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Lambda Duration p99"
          region  = "ap-south-1"
          period  = 60
          metrics = [
            ["AWS/Lambda", "Duration", "FunctionName", var.lambda_go_name, { stat = "p99" }],
          ]
          view = "timeSeries"
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Custom: Request Count"
          region  = "ap-south-1"
          period  = 60
          metrics = [
            ["ScanVault", "request_count", "Environment", var.environment],
          ]
          view = "timeSeries"
        }
      }
    ]
  })
}
