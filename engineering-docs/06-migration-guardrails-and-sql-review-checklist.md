# Migration Guardrails and SQL Review Checklist

## Purpose

This document defines local development guardrails for schema changes in SentinelX and provides a copy-paste SQL review checklist for pull requests.

It is intentionally strict to protect migration safety, API stability, and operational reliability.

## Scope

Use this guide for any change that touches:

- `backend/src/main/resources/db/migration/*.sql`
- database indexes, constraints, and data backfills
- query behavior that can affect latency or lock contention

## Non-Negotiable Guardrails

1. Use Flyway for all schema changes.
2. Never edit a historical migration that may already be applied.
3. Create a new migration file using `V{number}__{description}.sql`.
4. Keep SQL deterministic and repeatable in fresh environments.
5. Keep `spring.jpa.hibernate.ddl-auto=validate` behavior intact.
6. Document rollback and blast radius in every migration PR.

## Branching and Ownership Rules

1. Keep one database concern per feature branch.
2. Avoid cross-domain schema changes in the same PR unless explicitly required.
3. Coordinate migration numbering when parallel branches are active.
4. Rebase feature branches before merge to keep version ordering linear.

## Migration Authoring Standards

1. Prefer explicit column types and explicit constraints.
2. Add indexes for new high-selectivity filters and common sort paths.
3. Use nullable-first rollout when introducing required columns to large tables:
- Add nullable column.
- Backfill data in controlled statements.
- Add `NOT NULL` only after backfill verification.
4. Avoid long-running lock-heavy operations in one statement on high-churn tables.
5. Keep enum-like values aligned with Java enums.
6. Use clear, action-focused migration names.

## Lock and Performance Safety Rules

1. Evaluate lock impact before merge for DDL affecting large tables.
2. Capture `EXPLAIN ANALYZE` for new or changed high-traffic queries.
3. Verify no unexpected sequential scans for critical dashboard and aggregation paths.
4. Include index rationale in migration PR notes.

## Required Validation Before Merge

1. Migration chain is linear and version numbers are unique.
2. Application starts with profile settings and schema validation enabled.
3. Full test suite passes:
- `cd backend`
- `./mvnw.cmd test`
4. Referential integrity checks from database testing guide pass.
5. Health endpoint behavior remains correct (UP when connected, DEGRADED on DB outage).

## SQL Review Checklist (Copy-Paste for PR)

Use this in every migration-related PR description:

```markdown
## SQL Review Checklist

- [ ] Change uses a new Flyway migration file and does not modify applied history.
- [ ] Migration name follows `V{number}__{description}.sql`.
- [ ] Version number is unique on target branch.
- [ ] DDL is deterministic and safe for fresh database setup.
- [ ] Constraints and foreign keys are explicit and intentional.
- [ ] New indexes are added for changed query patterns, with rationale.
- [ ] Backfill strategy is documented for non-trivial data changes.
- [ ] Lock/availability impact is evaluated for large tables.
- [ ] Rollback or mitigation plan is documented.
- [ ] `./mvnw.cmd test` passes locally after migration change.
- [ ] Relevant integrity checks were executed and recorded.
- [ ] API/security/error contracts are unchanged, or intentional deltas are documented.
```

## Suggested PR Notes Template

```markdown
## Migration Summary
- Migration file:
- Tables affected:
- Query paths affected:

## Risk Assessment
- Lock risk:
- Data integrity risk:
- Runtime risk:

## Validation Evidence
- Test run:
- Integrity queries:
- Explain analyze snippets:

## Rollback/Mitigation
- Rollback approach:
- Fallback plan:
```

## Related Project References

- `README.md`
- `engineering-docs/03-integration-guide-for-fe-and-db.md`
- `engineering-docs/05-database-engineer-real-time-data-testing-guide.md`
- `backend/src/main/resources/db/migration/`
