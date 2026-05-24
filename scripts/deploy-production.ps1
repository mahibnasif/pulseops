[CmdletBinding()]
param(
    [string]$Cluster = $env:ECS_CLUSTER,
    [string]$BackendService = $env:ECS_BACKEND_SERVICE,
    [string]$FrontendService = $env:ECS_FRONTEND_SERVICE,
    [string]$MigrationTaskDefinition = $env:ECS_MIGRATION_TASK_DEFINITION,
    [string]$MigrationSubnetIds = $env:ECS_MIGRATION_SUBNETS,
    [string]$MigrationSecurityGroupId = $env:ECS_MIGRATION_SECURITY_GROUP,
    [string]$BackendImage = $env:BACKEND_IMAGE,
    [string]$FrontendImage = $env:FRONTEND_IMAGE,
    [string]$MigrationImage = $env:MIGRATION_IMAGE
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

function Assert-Value {
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [AllowEmptyString()]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "Required deployment value '$Name' is missing."
    }
}

function Invoke-AwsJson {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = & aws @Arguments --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed with exit code $LASTEXITCODE."
    }
    return $output | ConvertFrom-Json
}

function Get-LatestTaskDefinition {
    param(
        [Parameter(Mandatory)]
        [string]$TaskDefinition
    )

    $family = ($TaskDefinition -split '/')[-1] -replace ':\d+$', ''
    return (Invoke-AwsJson -Arguments @(
            'ecs', 'describe-task-definition',
            '--task-definition', $family
        )).taskDefinition
}

function Register-ImageRevision {
    param(
        [Parameter(Mandatory)]
        [object]$TaskDefinition,
        [Parameter(Mandatory)]
        [string]$ContainerName,
        [Parameter(Mandatory)]
        [string]$Image
    )

    $container = $TaskDefinition.containerDefinitions |
        Where-Object { $_.name -eq $ContainerName } |
        Select-Object -First 1

    if ($null -eq $container) {
        throw "Container '$ContainerName' was not found in task definition '$($TaskDefinition.family)'."
    }

    $container.image = $Image
    @(
        'taskDefinitionArn',
        'revision',
        'status',
        'requiresAttributes',
        'compatibilities',
        'registeredAt',
        'registeredBy',
        'deregisteredAt'
    ) | ForEach-Object {
        $TaskDefinition.PSObject.Properties.Remove($_)
    }

    $definitionPath = Join-Path ([System.IO.Path]::GetTempPath()) (
        "pulseops-task-definition-{0}.json" -f [guid]::NewGuid()
    )

    try {
        $definitionJson = $TaskDefinition | ConvertTo-Json -Depth 100
        [System.IO.File]::WriteAllText(
            $definitionPath,
            $definitionJson,
            [System.Text.UTF8Encoding]::new($false)
        )

        $registered = Invoke-AwsJson -Arguments @(
            'ecs', 'register-task-definition',
            '--cli-input-json', "file://$definitionPath"
        )
        return $registered.taskDefinition.taskDefinitionArn
    }
    finally {
        Remove-Item -LiteralPath $definitionPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-DatabaseMigration {
    $task = Get-LatestTaskDefinition -TaskDefinition $MigrationTaskDefinition
    $taskArn = Register-ImageRevision `
        -TaskDefinition $task `
        -ContainerName 'migration' `
        -Image $MigrationImage

    $subnets = @(
        $MigrationSubnetIds -split ',' |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ }
    )
    if ($subnets.Count -lt 2) {
        throw 'ECS_MIGRATION_SUBNETS must contain at least two comma-separated private subnet IDs.'
    }

    $networkConfiguration = @{
        awsvpcConfiguration = @{
            subnets        = $subnets
            securityGroups = @($MigrationSecurityGroupId)
            assignPublicIp = 'DISABLED'
        }
    } | ConvertTo-Json -Depth 5 -Compress

    Write-Host "Running database migration task $taskArn"
    $run = Invoke-AwsJson -Arguments @(
        'ecs', 'run-task',
        '--cluster', $Cluster,
        '--launch-type', 'FARGATE',
        '--task-definition', $taskArn,
        '--network-configuration', $networkConfiguration,
        '--started-by', 'github-actions'
    )

    if (@($run.failures).Count -gt 0) {
        throw "ECS rejected the migration task: $($run.failures | ConvertTo-Json -Compress)"
    }

    $taskRunArn = @($run.tasks)[0].taskArn
    & aws ecs wait tasks-stopped `
        --cluster $Cluster `
        --tasks $taskRunArn `
        --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "Timed out waiting for migration task '$taskRunArn' to stop."
    }

    $completed = (Invoke-AwsJson -Arguments @(
            'ecs', 'describe-tasks',
            '--cluster', $Cluster,
            '--tasks', $taskRunArn
        )).tasks[0]
    $migration = $completed.containers |
        Where-Object { $_.name -eq 'migration' } |
        Select-Object -First 1

    if ($null -eq $migration -or $migration.exitCode -ne 0) {
        $reason = if ($null -ne $migration) {
            "$($migration.reason) (exit $($migration.exitCode))"
        }
        else {
            $completed.stoppedReason
        }
        throw "Database migration failed: $reason"
    }

    Write-Host 'Database migration completed successfully.'
}

function Update-ApplicationService {
    param(
        [Parameter(Mandatory)]
        [string]$Service,
        [Parameter(Mandatory)]
        [string]$ContainerName,
        [Parameter(Mandatory)]
        [string]$Image
    )

    $serviceState = (Invoke-AwsJson -Arguments @(
            'ecs', 'describe-services',
            '--cluster', $Cluster,
            '--services', $Service
        )).services[0]

    if ($null -eq $serviceState) {
        throw "ECS service '$Service' was not found in cluster '$Cluster'."
    }

    $task = Get-LatestTaskDefinition -TaskDefinition $serviceState.taskDefinition
    $taskArn = Register-ImageRevision `
        -TaskDefinition $task `
        -ContainerName $ContainerName `
        -Image $Image

    Write-Host "Deploying $Image to ECS service $Service"
    $null = Invoke-AwsJson -Arguments @(
        'ecs', 'update-service',
        '--cluster', $Cluster,
        '--service', $Service,
        '--task-definition', $taskArn
    )

    & aws ecs wait services-stable `
        --cluster $Cluster `
        --services $Service `
        --no-cli-pager
    if ($LASTEXITCODE -ne 0) {
        throw "ECS service '$Service' did not become stable before the waiter timed out."
    }

    Write-Host "ECS service $Service is stable on $taskArn"
}

@{
    ECS_CLUSTER                      = $Cluster
    ECS_BACKEND_SERVICE              = $BackendService
    ECS_FRONTEND_SERVICE             = $FrontendService
    ECS_MIGRATION_TASK_DEFINITION    = $MigrationTaskDefinition
    ECS_MIGRATION_SUBNETS            = $MigrationSubnetIds
    ECS_MIGRATION_SECURITY_GROUP     = $MigrationSecurityGroupId
    BACKEND_IMAGE                    = $BackendImage
    FRONTEND_IMAGE                   = $FrontendImage
    MIGRATION_IMAGE                  = $MigrationImage
}.GetEnumerator() | ForEach-Object {
    Assert-Value -Name $_.Key -Value $_.Value
}

Invoke-DatabaseMigration
Update-ApplicationService `
    -Service $BackendService `
    -ContainerName 'backend' `
    -Image $BackendImage
Update-ApplicationService `
    -Service $FrontendService `
    -ContainerName 'frontend' `
    -Image $FrontendImage

Write-Host 'PulseOps production deployment completed.'
