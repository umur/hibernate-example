# Hibernate & Spring Data JPA in Depth — Companion Code

Source code for all 28 chapters of **Hibernate & Spring Data JPA in Depth**.

Each chapter is a self-contained Maven project built around a fictional streaming platform called **CineTrack**.

---

## Stack

| Layer | Technology |
|-------|-----------|
| ORM | Hibernate 7 |
| Data Access | Spring Data JPA 4 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Testing | JUnit 5 · Testcontainers · AssertJ |
| Build | Maven 3.9+ |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for Testcontainers)

---

## Running a Chapter

```bash
cd chapter-05
mvn clean verify
```

Each project spins up a real PostgreSQL 16 container via Testcontainers — no in-memory database, no mocks.

---

## Chapters

| # | Topic |
|---|-------|
| 01 | First entity — mapping a `Movie` with Hibernate |
| 02 | Persistence context, entity lifecycle, and flush modes |
| 03 | Transactions, propagation, and event listeners |
| 04 | Type mappings — JSONB, arrays, enums, and UUID generators |
| 05 | Embeddables, formulas, and `@DynamicUpdate` |
| 06 | Collections — `@OneToMany`, `@ManyToMany`, orphan removal |
| 07 | Inheritance strategies — `SINGLE_TABLE`, `JOINED`, `TABLE_PER_CLASS` |
| 08 | Natural IDs, composite keys, and UUID v7 |
| 09 | Auditing, soft deletes, and projections |
| 10 | JPQL, named queries, keyset pagination, and bulk operations |
| 11 | Specifications and QueryDSL |
| 12 | Projections — interfaces, DTOs, and tuple queries |
| 13 | Optimistic locking and concurrent update handling |
| 14 | Pessimistic locking and `SKIP LOCKED` queue pattern |
| 15 | Fetch strategies — lazy, eager, `@BatchSize`, subselect |
| 16 | N+1 problem — detection and solutions |
| 17 | Batch inserts and `StatelessSession` |
| 18 | Second-level cache with Caffeine |
| 19 | Flyway migrations in production |
| 20 | Multi-tenancy with schema-per-tenant |
| 21 | Audit history with Hibernate Envers |
| 22 | Filters, interceptors, and entity listeners |
| 23 | HQL window functions and CTEs |
| 24 | Custom Hibernate types |
| 25 | Full-text search with Hibernate Search 8 |
| 26 | Reactive data access with R2DBC |
| 27 | Observability — Micrometer, statistics, and slow query logging |
| 28 | Putting it all together |

---

## Test Coverage

Every chapter enforces **80% line coverage** via JaCoCo. Tests use real PostgreSQL 16 — no in-memory databases, no mocks for the persistence layer.

```
Total: 452 @Test methods across 76 test files
```

---

## License

MIT
