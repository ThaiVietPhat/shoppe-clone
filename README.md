# shoppe-clone
High-performance Shopee Clone built with Spring Boot 4 and Spring Modulith.

## Bootstrap local PostgreSQL

Create the local database once with the PostgreSQL admin account:

```bash
psql -U postgres -f scripts/create-database.sql
```

Then run Spring Boot with the local profile. Flyway creates the tables:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
