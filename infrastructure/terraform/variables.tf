variable "aws_region" {
  description = "AWS Region for all PulseOps resources."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Short deployment environment identifier."
  type        = string
  default     = "production"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,14}$", var.environment))
    error_message = "environment must be 2-15 lowercase letters, digits, or hyphens."
  }
}

variable "domain_name" {
  description = "Public DNS name used for the HTTPS application endpoint."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$", var.domain_name))
    error_message = "domain_name must be a valid lowercase fully qualified domain name."
  }
}

variable "route53_zone_id" {
  description = "Route 53 public hosted-zone ID that owns domain_name."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the PulseOps VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "nat_gateway_count" {
  description = "One NAT gateway is cost-aware; two avoids a cross-AZ outbound dependency."
  type        = number
  default     = 1

  validation {
    condition     = contains([1, 2], var.nat_gateway_count)
    error_message = "nat_gateway_count must be 1 or 2."
  }
}

variable "image_tag" {
  description = "Immutable image tag initially assigned to ECS task definitions."
  type        = string
  default     = "bootstrap"
}

variable "jwt_secret_arn" {
  description = "ARN of a pre-populated Secrets Manager secret containing a Base64 JWT key."
  type        = string

  validation {
    condition     = can(regex("^arn:aws[a-z-]*:secretsmanager:[^:]+:[0-9]{12}:secret:", var.jwt_secret_arn))
    error_message = "jwt_secret_arn must be a Secrets Manager secret ARN."
  }
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy through the protected production environment."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must use owner/repository format."
  }
}

variable "github_oidc_provider_arn" {
  description = "ARN of the account-level token.actions.githubusercontent.com IAM OIDC provider."
  type        = string

  validation {
    condition     = can(regex("^arn:aws[a-z-]*:iam::[0-9]{12}:oidc-provider/token\\.actions\\.githubusercontent\\.com$", var.github_oidc_provider_arn))
    error_message = "github_oidc_provider_arn must identify the GitHub Actions OIDC provider."
  }
}

variable "frontend_desired_count" {
  description = "Number of frontend Fargate tasks."
  type        = number
  default     = 1
}

variable "backend_desired_count" {
  description = "Number of backend Fargate tasks. Keep at one until live events and rate limits use shared state."
  type        = number
  default     = 1

  validation {
    condition     = var.backend_desired_count == 1
    error_message = "The current in-memory live-event and rate-limit architecture requires backend_desired_count = 1."
  }
}

variable "database_instance_class" {
  description = "RDS PostgreSQL instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "database_multi_az" {
  description = "Enable an RDS standby in another availability zone."
  type        = bool
  default     = false
}

variable "database_deletion_protection" {
  description = "Protect the production database from accidental deletion."
  type        = bool
  default     = true
}

variable "alb_deletion_protection" {
  description = "Protect the public load balancer from accidental deletion."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention period."
  type        = number
  default     = 30

  validation {
    condition     = contains([14, 30, 60, 90, 120, 150, 180, 365], var.log_retention_days)
    error_message = "log_retention_days must be a supported CloudWatch retention value."
  }
}

variable "alarm_actions_enabled" {
  description = "Send CloudWatch alarms to the generated SNS topic."
  type        = bool
  default     = true
}
