# Staging stack: the same resources as production, with -staging names, its own state
# (key go3d-staging/terraform.tfstate) and the write routes enabled. Production stays read-only
# until the cutover (#125). Plan and apply it with the Terraform workflow's `environment` input.
environment             = "staging"
active_games_table_name = "go3d-active-games-staging"
players_table_name      = "go3d-players-staging"
lambda_function_name    = "go3d-staging"
lambda_role_name        = "go3d-lambda-staging-role"
api_name                = "go3d-staging"
enable_write_routes     = true

# the billing alarm watches the whole account, and production already has it
billing_alarm_enabled = false
