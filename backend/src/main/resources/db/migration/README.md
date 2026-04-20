# Flyway Migration Guardrails

This folder is the source of truth for SentinelX schema evolution.

## Rules

1. Add new migration files only.
2. Never edit old migration files that may already be applied.
3. Use file naming format: `V{number}__{description}.sql`.
4. Keep migration SQL deterministic and environment-safe.
5. Keep schema compatible with `spring.jpa.hibernate.ddl-auto=validate`.

## Before Opening a Migration PR

1. Verify version number uniqueness on target branch.
2. Include index and constraint rationale.
3. Include rollback or mitigation notes.
4. Run:
- `cd backend`
- `./mvnw.cmd test`

## References

- `engineering-docs/06-migration-guardrails-and-sql-review-checklist.md`
- `engineering-docs/05-database-engineer-real-time-data-testing-guide.md`
