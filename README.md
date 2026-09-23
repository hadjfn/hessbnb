# HessBnb — personal architecture work

[![Verify](https://github.com/hadjfn/hessbnb/actions/workflows/verify.yml/badge.svg)](https://github.com/hadjfn/hessbnb/actions/workflows/verify.yml)

An academic rental platform built with **Java 21, Spring Boot, Angular 21, PostgreSQL, RabbitMQ and Keycloak**. This personal fork concentrates on booking reliability, transaction boundaries and maintainability.

## Provenance and my work

The application comes from the EFREI team project [lfleury78/ALIF82-2526PSP01_Projet_Microservices](https://github.com/lfleury78/ALIF82-2526PSP01_Projet_Microservices). The original authors and history are retained. My initial contribution was the database schema, MySQL/Docker setup and sample data; the broader application is team work.

The personal refactoring in this fork adds:

* Plain Java booking policies, with ArchUnit rules protecting architectural boundaries.
* PostgreSQL constraints that prevent overlapping bookings even under concurrent requests, plus optimistic locking for status changes.
* A transactional outbox: booking changes and their events commit together; RabbitMQ delivery is retried separately.
* Idempotent rental creation that handles repeated or concurrent confirmations.
* Booking access checks, regression tests, a Maven workspace, and GitHub Actions for backend/Angular validation.

Read the [architecture](docs/architecture.md), [decision record](docs/adr/0001-booking-consistency.md) and [remaining technical debt](docs/technical-debt.md). This is a learning project; the debt register includes unresolved authorization and authoritative pricing work before any real deployment.

## Repository map

| Path | Responsibility |
| --- | --- |
| `app/frontend` | Angular interface, Keycloak client, navigation and route guards |
| `app/gateway` | HTTP entry point |
| `services/booking-service` | Booking policies, persistence, outbox and access control |
| `services/rental-service` | Rental lifecycle and idempotent confirmation consumer |
| `services/listing-service` | Property listings and deletion workflow |
| `services/{user,message,review,notification}-service` | Other original team services |
| `infra` | Local PostgreSQL/Keycloak material from the team project |

## Build and verify

Requirements: JDK **21**, Maven **3.9+**, Node **22**, npm and Docker for integration tests. Commands below run from the repository root.

```sh
# Compile/package all eight Java applications. This does not run their infrastructure-dependent starter tests.
mvn -DskipTests package

# Focused unit and architecture checks, no Docker required.
mvn -f services/booking-service/pom.xml test
mvn -f services/rental-service/pom.xml test

# Real PostgreSQL 16, Flyway/JPA and HTTP/security regression checks.
mvn -f services/booking-service/pom.xml verify -Pintegration
mvn -f services/rental-service/pom.xml verify -Pintegration

cd app/frontend
npm ci
npm test -- --watch=false
npm run build
```

Integration tests create disposable containers on random ports; they do not use or reset an existing local database. Outbox broker acknowledgements are mocked in this suite; real RabbitMQ restart/recovery and full user journeys remain future tests.

## Local application

The existing Compose stack is intended for development. Keep `.env` local and use distinct values outside this disposable demo:

```sh
cp .env.example .env
docker compose config --quiet
docker compose up -d --build
cd app/frontend
npm ci
npm start
```

Frontend: `http://localhost:4200`; gateway: `http://localhost:8080`; Keycloak: `http://localhost:8180`. The realm is imported from `infra/keycloak/realm-export.json`. Use the development accounts configured there; do not reuse them on a publicly exposed deployment. The frontend runs outside Compose.

PostgreSQL data uses the named `postgres_data` volume. Flyway V2 installs `btree_gist` and validates existing booking rows; resolve conflicting historical rows deliberately if migration fails. Never reset a volume containing data you want to retain.

The complete Compose/Keycloak/browser flow has not been validated in this refactoring. The isolated builds and tests above are the verified scope; see the debt register for the next checks.

## Validation

Locally checked on 23 September 2026 with JDK 21 and Docker:

| Check | Result |
| --- | --- |
| All eight Java applications: `mvn -DskipTests package` | Passed; compilation/packaging, not all service tests |
| Booking rules and ArchUnit | 16 tests passed |
| Booking PostgreSQL/Flyway/HTTP/outbox regression | 11 tests passed |
| Rental listener and ArchUnit | 4 tests passed |
| Rental repeated/concurrent event integration | 2 tests passed against PostgreSQL |
| Angular unit tests | 4 tests passed |
| Angular production build | Passed; existing optional-chain/CommonJS warnings remain |
| Compose configuration with `.env.example` | Valid |

The [Verify workflow](https://github.com/hadjfn/hessbnb/actions/workflows/verify.yml) runs the same focused checks on pushes and pull requests with read-only repository permissions and no deployment secrets.

## About

El Hadj Sylla · software engineering student at EFREI · interested in backend development, SOLID, testing and technical debt.

[CV](https://el-hadj-sylla-cv.vercel.app/) · [LinkedIn](https://www.linkedin.com/in/elhadj-sylla/) · [GitHub](https://github.com/hadjfn)
