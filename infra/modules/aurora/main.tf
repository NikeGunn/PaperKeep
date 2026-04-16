resource "aws_db_subnet_group" "aurora" {
  name       = "${var.name_prefix}-aurora"
  subnet_ids = var.subnet_ids
  tags       = { Name = "${var.name_prefix}-aurora-subnet-group" }
}

resource "aws_security_group" "aurora" {
  name        = "${var.name_prefix}-aurora"
  description = "Aurora PostgreSQL - only Lambda can connect"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.lambda_sg_id]
    description     = "PostgreSQL from Lambda"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-aurora-sg" }
}

resource "aws_rds_cluster" "aurora" {
  cluster_identifier          = "${var.name_prefix}-aurora"
  engine                      = "aurora-postgresql"
  engine_version              = "15.10"
  engine_mode                 = "provisioned"
  database_name               = "scanvault"
  master_username             = "scanvault_admin"
  manage_master_user_password = true # Secrets Manager managed

  db_subnet_group_name   = aws_db_subnet_group.aurora.name
  vpc_security_group_ids = [aws_security_group.aurora.id]

  # Serverless v2 scaling — min 0 = auto-pause after 5 min idle (zero cost at rest)
  serverlessv2_scaling_configuration {
    min_capacity = 0
    max_capacity = 2.0
  }

  # Cost saving: no deletion protection in staging
  deletion_protection = var.environment == "prod"
  skip_final_snapshot = var.environment != "prod"

  # Backups
  backup_retention_period = var.environment == "prod" ? 7 : 1
  preferred_backup_window = "02:00-03:00"

  storage_encrypted = true

  enabled_cloudwatch_logs_exports = ["postgresql"]

  tags = { Name = "${var.name_prefix}-aurora" }
}

resource "aws_rds_cluster_instance" "aurora" {
  identifier           = "${var.name_prefix}-aurora-instance"
  cluster_identifier   = aws_rds_cluster.aurora.id
  instance_class       = "db.serverless"
  engine               = aws_rds_cluster.aurora.engine
  engine_version       = aws_rds_cluster.aurora.engine_version
  db_subnet_group_name = aws_db_subnet_group.aurora.name
  publicly_accessible  = false

  tags = { Name = "${var.name_prefix}-aurora-instance" }
}
