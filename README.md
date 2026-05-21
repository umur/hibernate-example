# Hibernate & Spring Data JPA in Depth

> Everything between `@Entity` and a production database, explained line by line.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![Hibernate](https://img.shields.io/badge/Hibernate-7-59666C?logo=hibernate&logoColor=white) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for **Hibernate & Spring Data JPA in Depth** by [Umur Inan](https://umurinan.com).

## About the book

The persistence layer is where most Spring Boot applications quietly fail in production: N+1 queries, lost updates, unflushed sessions, mismatched cascades, Flyway migrations that lock for hours. This book walks through Hibernate's internals and Spring Data JPA's contract with the same example evolving across 28 chapters. **CineTrack**, a streaming platform's data layer. Building up from a single entity to a sharded, partitioned, multi-tenant production schema.

## Who this is for

- Spring Boot developers who use JPA daily but have hit their first N+1 query in production
- Engineers responsible for a growing schema who need to understand what Hibernate actually does
- Anyone who has seen `LazyInitializationException` or a 10-minute Flyway migration and wants to understand why

## Chapters

1. JPA, Hibernate 7, and the Contract
2. The Persistence Context
3. Transactions and the Unit of Work
4. Hibernate 7 Internals
5. Entity Mapping Deep Dive
6. Associations: Every Variant
7. Inheritance Strategies
8. Identifiers, Natural IDs, and Composite Keys
9. Repository Internals and Custom Implementations
10. JPQL, Native Queries, and @Query
11. Dynamic Queries: Specifications, QueryDSL, and Beyond
12. Projections, DTOs, and Result Mapping
13. Optimistic Locking
14. Pessimistic Locking
15. Fetch Strategies: LAZY, EAGER, EntityGraph, and Batch Fetching
16. The N+1 Problem: Diagnosis and All the Fixes
17. Batch Processing, Streaming, and StatelessSession
18. Second-Level Cache: Architecture, Providers, and Production
19. Schema Management: Flyway, hbm2ddl, and Validation
20. Multi-tenancy: Schema, Discriminator, and Separate Database
21. Auditing and Hibernate Envers
22. Interceptors, Event Listeners, and Row-Level Filters
23. Advanced HQL: Window Functions, CTEs, and Hibernate 7 Extensions
24. Custom Types: UserType 2.0, JavaType, and JdbcType
25. Hibernate Search: Full-Text Search with Lucene and Elasticsearch
26. Reactive Persistence: Hibernate Reactive and Spring Data R2DBC
27. Observability: Metrics, Tracing, and Slow Query Detection
28. Testing: @DataJpaTest, Testcontainers, and Repository Testing

## Prerequisites

- Java 21 LTS ([Temurin](https://adoptium.net))
- Maven 3.9+
- Docker & Docker Compose (PostgreSQL 16)

## Quick start

```bash
git clone https://github.com/umur/hibernate-example
cd hibernate-example/chapter-01
mvn spring-boot:run
```

## Layout

One Maven project per chapter:

- `chapter-01/ ... chapter-28/`: self-contained Spring Boot projects, each representing the cumulative CineTrack data layer at the end of that chapter
- Each directory has a `README.md` describing the chapter's focus and the Flyway migrations it adds

## Stack

- Java 21 (LTS)
- Spring Boot 4
- Hibernate 7 / JPA 3.2
- Spring Data JPA 4
- PostgreSQL 16
- Flyway for schema migrations
- Testcontainers for integration tests

## Related books

- [Spring Boot 4 in Practice](https://github.com/umur/spring-boot-example): introduces JPA; this book picks up where Chapter 5 leaves off
- [PostgreSQL: From MVCC to Production](https://github.com/umur/db-book-example): the database side of what Hibernate does at the Java level
- [Spring Boot 4 Performance in Practice](https://github.com/umur/spring-boot-performance-book-example): Hibernate performance patterns covered in Chapters 19 and 20 of that book

## About the author

I'm Umur Inan. I write production-focused books about Java, Spring Boot, distributed systems, and everything that makes software reliable at scale.

[umurinan.com](https://umurinan.com)

## License

MIT. See [LICENSE](LICENSE).
