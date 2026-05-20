terraform {
  required_version = ">= 1.10.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Application = "pulseops"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  name               = "pulseops-${var.environment}"
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
  database_name      = "pulseops"
  database_username  = "pulseops_admin"
  application_url    = "https://${var.domain_name}"
}
