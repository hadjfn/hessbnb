# Technical debt register

Scope: personal maintenance of the HessBnb academic team project. Priority indicates the next engineering work, not a claim that the application is production-ready.

| Priority | Remaining issue | Consequence | Next verifiable step |
| --- | --- | --- | --- |
| P0 | Booking creation still receives listing owner and total price from the client | A forged request may claim another owner or an arbitrary price | Fetch/version an authoritative listing quote server-side; test forged owner, unavailable listing, capacity and price changes |
| P0 | Authorization and internal endpoints outside booking-service are not comprehensively audited | Read or mutation access may be too broad elsewhere | Add an endpoint/actor matrix and security integration tests for rental, messages, reviews, users and uploads |
| P1 | Listing deletion and booking cancellation cross service transactions | A crash can leave only part of the workflow completed; a new booking can race deletion | Introduce listing lifecycle/version checks plus an idempotent deletion saga, with crash recovery tests |
| P1 | Rental consumer handles confirmations only; cancellation/completion synchronization is incomplete | A cancelled booking can leave an active rental | Define and test valid lifecycle events and convergent consumer transitions |
| P1 | Outbox retries can duplicate downstream notification effects | Duplicate user notifications after a broker/database boundary crash | Deduplicate all consumers by event ID; add an inbox transaction and replay tests |
| P1 | No dead-letter queue or bounded backoff for rental failures | Persistent failures can requeue indefinitely; malformed rejected messages have no recovery queue | Introduce queue migration, retry/dead-letter topology and an explicit replay procedure |
| P1 | Broker behavior is mocked in outbox integration tests | Real connection recovery, broker topology, and crash windows remain unverified | Add RabbitMQ Testcontainers tests for confirmations, returned messages and restart recovery |
| P1 | Local development credentials and realm/sample data originate in a public teaching project | Unsuitable configuration for internet deployment | Use environment-specific secret management, restrict exposed ports and sanitize demo accounts before deployment; do not reuse historical values |
| P2 | Outbox has no age/depth metrics, retention alert or exponential backoff | Persistent delivery failure can accumulate rows silently | Export pending count/oldest age, alert and test recovery against an intentionally unavailable broker |
| P2 | Booking notification and other message contracts remain unversioned JSON | Producer changes can break consumers | Add versioned event contracts and consumer compatibility tests |
| P2 | Other services still mix transport, infrastructure and business rules | Changes are difficult to test independently | Refactor one use case at a time using the booking approach and add focused tests before moving code |
| P2 | No complete end-to-end browser/Keycloak/messaging scenario was run for this refactor | Passing module builds do not prove the whole user journey | Automate create listing → book → confirm → rent → cancel on an isolated Compose stack |
| P2 | Angular build reports existing optional-chain/CommonJS warnings | Warning noise masks future regressions | Remove unnecessary optional chains and assess ESM-compatible map dependencies |

## Reduced in this iteration

* Booking rules extracted into framework-free Java and protected by ArchUnit.
* Overlap race closed with a real PostgreSQL exclusion constraint; stale status writes rejected through optimistic locking.
* Booking write + event recording made atomic; failed deliveries retained in an outbox.
* Concurrent/replayed rental confirmation handled without duplicate rentals or resetting existing state.
* Booking read/bulk cancellation authorization added; listing deletion propagates identity and no longer ignores cancellation failure.
* Replaced starter booking/rental smoke tests with regression tests, introduced repeatable CI, fixed obsolete frontend starter tests.
* Added a root Maven reactor; removed overlapping PostgreSQL data mounts; stopped tracking `.env` in the new HEAD and provided explicit local defaults in `.env.example`.

## Validation log

Run dates and exact results are recorded in README after local verification. GitHub Actions is the repeatable verification source after publishing the fork. No production deployment or upstream pull request is implied.
