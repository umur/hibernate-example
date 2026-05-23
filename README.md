# Hibernate & Spring Data JPA in Depth

> Everything between `@Entity` and a production database, explained line by line.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![Hibernate](https://img.shields.io/badge/Hibernate-7-59666C?logo=hibernate&logoColor=white) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for **Hibernate & Spring Data JPA in Depth: Mastering Persistence with Hibernate 7, Spring Data JPA 3, and PostgreSQL** by [Umur Inan](https://umurinan.com).

## About the book

The persistence layer is where most Spring Boot applications quietly fail in production: N+1 queries, lost updates, unflushed sessions, mismatched cascades, Flyway migrations that lock for hours. This book walks through Hibernate 7 internals and the Spring Data JPA 3 contract using **CinéTrack**, a movie-tracking data layer that grows over 28 chapters from a single entity to a sharded, partitioned, multi-tenant production schema on PostgreSQL 16.

## Who this is for

- Spring Boot developers who use JPA daily and have hit their first N+1 query in production
- Engineers responsible for a growing schema who need to understand what Hibernate actually does
- Anyone who has seen `LazyInitializationException` or a 10-minute Flyway migration and wants the underlying reason

## Prerequisites

- Java 21 LTS ([Temurin](https://adoptium.net))
- Maven 3.9+ (or use the bundled `./mvnw` wrapper)
- Docker and Docker Compose
- PostgreSQL 16 client tools for inspection (optional but useful)

## Quick start

```bash
git clone https://github.com/umur/hibernate-spring-data-example
cd hibernate-spring-data-example/chapter-01
docker compose up -d
mvn spring-boot:run
```

## Chapters

Each `chapter-NN/` directory is a self-contained, runnable project. Each chapter is a cumulative snapshot: it builds on the previous chapter's state plus that chapter's specific Flyway migrations and mapping changes. Each chapter directory has its own `README.md` with the delta and run instructions.

- `chapter-01`: JPA spec, Hibernate 7, and the boot process
- `chapter-02`: the persistence context, identity map, and dirty checking
- `chapter-03`: transactions and the unit of work
- `chapter-04`: Hibernate 7 internals, SQM, and the new type system
- `chapter-05`: entity mapping deep dive with converters, formulas, and column transformers
- `chapter-06`: every association variant, owning sides, and cascade types
- `chapter-07`: inheritance strategies and the CinéTrack media hierarchy
- `chapter-08`: identifiers, natural IDs, composite keys, ULID, and TSID
- `chapter-09`: repository internals and custom Spring Data implementations
- `chapter-10`: JPQL, native queries, `@Query`, and keyset pagination
- `chapter-11`: dynamic queries with Specifications, QueryDSL, jOOQ, and Blaze-Persistence
- `chapter-12`: projections, DTOs, and result mapping
- `chapter-13`: optimistic locking and version-based concurrency control
- `chapter-14`: pessimistic locking, FOR UPDATE, SKIP LOCKED, and advisory locks
- `chapter-15`: fetch strategies, EntityGraph, and batch fetching
- `chapter-16`: the N+1 problem, diagnosis, and every fix pattern
- `chapter-17`: batch processing, streaming, and StatelessSession
- `chapter-18`: second-level cache with Ehcache and Redis
- `chapter-19`: schema management with Flyway and hbm2ddl validation
- `chapter-20`: multi-tenancy across schema, discriminator, and separate-database strategies
- `chapter-21`: auditing with Spring Data and Hibernate Envers
- `chapter-22`: interceptors, event listeners, and row-level filters
- `chapter-23`: advanced HQL, window functions, CTEs, and lateral joins
- `chapter-24`: custom types with UserType 2.0, JavaType, and JdbcType
- `chapter-25`: Hibernate Search with Lucene and Elasticsearch
- `chapter-26`: reactive persistence with Hibernate Reactive and Spring Data R2DBC
- `chapter-27`: observability, Micrometer metrics, and slow query detection
- `chapter-28`: testing with `@DataJpaTest`, Testcontainers, and Envers-aware assertions

## Stack

- Java 21 (LTS)
- Spring Boot 4.0.6
- Hibernate 7.x
- Spring Data JPA 3.x
- PostgreSQL 16
- Flyway for schema migrations
- Testcontainers for integration tests

## Related books

- [Spring Boot 4 in Practice](https://github.com/umur/spring-boot-example): introduces JPA. This book picks up where its persistence chapter leaves off.
- [PostgreSQL: From MVCC to Production](https://github.com/umur/postgres-example): the database side of what Hibernate does at the Java level
- [Spring Boot Performance](https://github.com/umur/spring-boot-performance-example): Hibernate performance patterns from the application-tier angle

## About the author

I'm Umur Inan, a Principal Software Engineer with 15 years of experience building backend systems across enterprise, government, and high-growth environments. I specialize in microservices architecture, distributed systems, and cloud-native development, with deep expertise in Spring Boot, Kafka, and Kubernetes. Based in New York City, I've shipped products across five countries and hold a Master's in Computer Science and a Bachelor's in Computer Engineering.

[umurinan.com](https://umurinan.com)

## License

MIT. See [LICENSE](LICENSE).
