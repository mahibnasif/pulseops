output "application_url" {
  description = "Public HTTPS URL."
  value       = local.application_url
}

output "load_balancer_dns_name" {
  description = "ALB DNS name used by the Route 53 alias."
  value       = aws_lb.main.dns_name
}

output "ecr_repository_urls" {
  description = "ECR repositories used by the release workflow."
  value = {
    for name, repository in aws_ecr_repository.application :
    name => repository.repository_url
  }
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "frontend_service_name" {
  value = aws_ecs_service.frontend.name
}

output "backend_service_name" {
  value = aws_ecs_service.backend.name
}

output "migration_task_definition_arn" {
  value = aws_ecs_task_definition.migration.arn
}

output "migration_subnet_ids" {
  value = aws_subnet.application[*].id
}

output "migration_security_group_id" {
  value = aws_security_group.backend.id
}

output "alarm_topic_arn" {
  description = "Subscribe an operator endpoint to this SNS topic."
  value       = aws_sns_topic.alarms.arn
}

output "rds_master_secret_arn" {
  description = "RDS-managed credential secret used by ECS."
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
  sensitive   = true
}
