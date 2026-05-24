data "aws_iam_policy_document" "github_deployment_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:production"]
    }
  }
}

resource "aws_iam_role" "github_deployment" {
  name                 = "${local.name}-github-deployment"
  description          = "Short-lived GitHub Actions role for PulseOps production releases."
  max_session_duration = 3600
  assume_role_policy   = data.aws_iam_policy_document.github_deployment_assume_role.json
}

data "aws_iam_policy_document" "github_deployment" {
  statement {
    sid       = "AuthenticateToEcr"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushReleaseImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = [for repository in aws_ecr_repository.application : repository.arn]
  }

  statement {
    sid = "ReadDeploymentState"
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTasks",
      "ecs:DescribeTaskDefinition"
    ]
    resources = ["*"]
  }

  statement {
    sid       = "RegisterTaskDefinitions"
    actions   = ["ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid     = "DeployApplicationServices"
    actions = ["ecs:UpdateService"]
    resources = [
      aws_ecs_service.backend.id,
      aws_ecs_service.frontend.id
    ]
  }

  statement {
    sid       = "RunDatabaseMigration"
    actions   = ["ecs:RunTask"]
    resources = ["${replace(aws_ecs_task_definition.migration.arn, "/:[0-9]+$/", "")}:*"]
  }

  statement {
    sid     = "PassEcsRoles"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.application_task.arn,
      aws_iam_role.ecs_execution.arn
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deployment" {
  name   = "${local.name}-release"
  role   = aws_iam_role.github_deployment.id
  policy = data.aws_iam_policy_document.github_deployment.json
}
