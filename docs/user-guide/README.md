# Consumer user guide

Instructions for people setting up and using ForgeFlow belong here.

Planned topics: installation, connecting a repository, approving work, following execution and reviewing results. Further setup instructions will be added as those capabilities become available.

- [Local control-plane bootstrap](local-control-plane.md) — migrate the database, run the local health endpoint and optionally register the approved workflow.

- [GitHub webhook inbox](github-webhooks.md) — enable signed delivery ingestion and inspect processing status.

- [OAuth2 worker authentication](worker-authentication.md) — Spring Boot OAuth2 settings and principal mapping; job endpoints remain unimplemented.

- [Manual plan approval](manual-plan-approval.md) — configure one epic/story pair, issue approval/invalidation commands and understand internal parent preparation.
