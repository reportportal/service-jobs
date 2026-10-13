# Service Jobs

ReportPortal jobs service. Scheduled maintenance for a ReportPortal instance: retention cleanup of launches, logs, attachments, and object storage; stale views and expired users; allocated storage calculation; and optional analytics plus log indexing.

Requires PostgreSQL, RabbitMQ, and object storage (filesystem, S3-compatible, SeaweedFS, or AWS S3). Listens on port **8686**. Health check: `GET /health`.

[Apache License 2.0](https://github.com/reportportal/service-jobs/blob/develop/LICENSE). Copyright EPAM Systems.
