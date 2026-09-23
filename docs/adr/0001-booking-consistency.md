# ADR 0001 — Incrementally harden booking consistency

Status: accepted for this personal fork.

## Problem

The original booking service checked overlap then saved, sent RabbitMQ messages before database commit, and embedded business rules beside broker code. Parallel requests could overlap and an event could outlive a rolled-back booking. The rental listener swallowed failures, while repeated confirmations raised duplicate errors.

## Decision

1. Keep the existing service/API split and team implementation; extract only booking policy into plain Java.
2. Enforce active-date exclusivity in PostgreSQL and stale-write protection with JPA versioning. Keep the readable application precheck as a friendly early rejection, not the guarantee.
3. Persist an outbox row in the booking transaction. Publish separately with correlated confirmations, mandatory routing and stable message IDs.
4. Accept at-least-once delivery and implement rental deduplication atomically on the existing unique booking ID.
5. Test PostgreSQL-specific invariants on PostgreSQL rather than substituting H2. Enforce architectural direction with ArchUnit.

## Alternatives and trade-offs

A Java lock cannot coordinate multiple service instances. Serializable transactions need retries and are less explicit than an exclusion constraint for this interval rule. An after-commit callback avoids early publication but loses work if the process stops between commit and send. A distributed transaction would add operational coupling; the outbox deliberately accepts duplicates instead.

The PostgreSQL extension and SQL are a portability cost. Polling adds latency (default two seconds per batch; multiple events for one booking can need successive polls), load and operational debt. There is no universal event framework or full hexagonal rewrite: introduce further abstractions only where a new testable boundary is needed.

## Verification and follow-up

Regression tests cover concurrent conflicts, adjacent dates, stale status updates, rollback atomicity, event order/retry outcomes and concurrent rental deduplication. Real RabbitMQ failure recovery, authoritative listing quotes and a deletion saga remain explicitly tracked in [technical debt](../technical-debt.md).
