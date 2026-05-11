# Hibernate & Spring Data JPA in Depth

> Everything between `@Entity` and a production database, explained line by line.

Companion code for the book **Hibernate & Spring Data JPA in Depth** by [Umur Inan](https://umurinan.com).

## About the book

The persistence layer is where most Spring Boot applications quietly fail in production: N+1 queries, lost updates, unflushed sessions, mismatched cascades, Flyway migrations that lock for hours. This book walks through Hibernate's internals and Spring Data JPA's contract with the same example evolving across 28 chapters — **CineTrack**, a streaming platform's data layer — building up from a single entity to a sharded, partitioned, multi-tenant production schema.

## Quick start

```bash
git clone https://github.com/umur/hibernate-example
cd hibernate-example/chapter-01
mvn spring-boot:run
```

## Layout

One Maven project per chapter:

- `chapter-01/ … chapter-28/` — self-contained Spring Boot projects, each representing the cumulative CineTrack data layer at the end of that chapter
- Each directory has a `README.md` describing the chapter's focus and the Flyway migrations it adds

## Stack

- Java 21 (LTS)
- Spring Boot 4
- Hibernate 7 / JPA 3.2
- Spring Data JPA 4
- PostgreSQL 16
- Flyway for schema migrations
- Testcontainers for integration tests

## About the author

I'm Umur Inan. I write books about Spring Boot, Java, distributed systems, and the practices that make production reliable.

📚 **More writing and books → [umurinan.com](https://umurinan.com)**

## License

MIT — see [LICENSE](LICENSE).
