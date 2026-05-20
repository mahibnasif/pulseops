resource "aws_security_group" "load_balancer" {
  name        = "${local.name}-load-balancer"
  description = "Public HTTPS entry point"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP redirect"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS application traffic"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Frontend target traffic"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${local.name}-load-balancer"
  }
}

resource "aws_security_group" "frontend" {
  name        = "${local.name}-frontend"
  description = "Frontend Fargate tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Traffic from the application load balancer"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.load_balancer.id]
  }

  egress {
    description = "HTTPS for task image pulls and log delivery"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "VPC DNS over UDP"
    from_port   = 53
    to_port     = 53
    protocol    = "udp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "VPC DNS over TCP"
    from_port   = 53
    to_port     = 53
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "${local.name}-frontend"
  }
}

resource "aws_security_group" "backend" {
  name        = "${local.name}-backend"
  description = "Backend and migration Fargate tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Traffic from the application load balancer"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.load_balancer.id]
  }

  # PulseOps monitors user-configured public HTTP and HTTPS endpoints. The
  # application-level SSRF policy is the primary destination guard, while VPC
  # Flow Logs and NAT metrics make this intentionally broad egress observable.
  egress {
    description = "Outbound monitoring and AWS service access"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name}-backend"
  }
}

resource "aws_security_group" "database" {
  name        = "${local.name}-database"
  description = "RDS PostgreSQL reachable only from backend tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from backend and migration tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  tags = {
    Name = "${local.name}-database"
  }
}
