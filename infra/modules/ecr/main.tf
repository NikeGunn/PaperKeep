resource "aws_ecr_repository" "go_backend" {
  name                 = "${var.name_prefix}-go-backend"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = { Name = "${var.name_prefix}-go-backend" }
}

resource "aws_ecr_repository" "python_intelligence" {
  name                 = "${var.name_prefix}-python-intelligence"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = { Name = "${var.name_prefix}-python-intelligence" }
}

# Lifecycle policy: keep last 5 images to save storage costs
resource "aws_ecr_lifecycle_policy" "go_backend" {
  repository = aws_ecr_repository.go_backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecr_lifecycle_policy" "python_intelligence" {
  repository = aws_ecr_repository.python_intelligence.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}
