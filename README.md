# Settlebook

[![CI](https://github.com/Lorcelmao/settlebook/actions/workflows/ci.yml/badge.svg)](https://github.com/Lorcelmao/settlebook/actions/workflows/ci.yml)

Payment callback ingestion and reconciliation service for a Vietnamese merchant that accepts payments through VNPay.

> **Status: early development.** Only the order API exists so far. Payment handling, the gateway simulator, the ledger and reconciliation are planned, not built. Nothing in this README claims a feature that the code does not have.

## The problem

A merchant taking online payments through VNPay has to get four things right:

1. **Callbacks arrive more than once.** VNPay calls the merchant's IPN URL up to 10 times, at 5-minute intervals, until it gets an accepted response. A payment must be applied exactly once however many times its callback arrives.
2. **Callbacks can be lost or late.** The buyer closes the browser, or the server is down. The merchant must recover the true status by querying VNPay.
3. **A customer can really be charged twice** for one order, through two separate payment attempts. That money must be recorded, not silently dropped or booked as a second sale.
4. **Money must reconcile.** At the end of each business day (Asia/Ho_Chi_Minh), what the gateway settled must match what the merchant recorded, and every difference must become a case someone resolves.

## Terminology: duplicate callback vs duplicate charge

These two situations look alike and are handled completely differently. The code and documentation keep them separate.

| | Duplicate callback (replay) | Duplicate charge (double payment) |
|---|---|---|
| What happened | The **same** gateway event was delivered again: a VNPay retry, a network resend, or a replay | Two **distinct** successful gateway transactions for the same order (different transaction references) |
| Money moved | Once | Twice: the customer really paid twice |
| Correct effect | Apply once; later deliveries change nothing and are acknowledged as already processed (`RspCode 02`) | Record both. The first pays the order. The second is held for the customer and opens a refund case. |
| Detected by | Deduplication of inbound gateway events | A database constraint allowing only one successful payment per order |

## Current scope

- `POST /api/orders` creates an order awaiting payment. The amount is whole VND and the payment window defaults to 15 minutes.
- `GET /api/orders/{id}` fetches an order.
- Errors are returned as RFC 9457 `application/problem+json`.
- PostgreSQL schema managed by Flyway, with CHECK constraints so invalid rows are rejected even if the API validation is bypassed.

## Run locally

Requires Java 21 and Docker.

```bash
docker compose up -d          # PostgreSQL on localhost:5432
./mvnw spring-boot:run        # API on http://localhost:8080
```

Alternatively, `./mvnw spring-boot:test-run` starts the app against a throwaway PostgreSQL container, with no Compose needed.

```bash
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -d '{"amountVnd": 150000, "description": "Order #1"}'
```

## Test

```bash
./mvnw verify    # integration tests start PostgreSQL in a Testcontainer
```

## Stack

Java 21, Spring Boot 4.1, Spring JDBC (`JdbcClient`), Flyway, PostgreSQL 17, Testcontainers, GitHub Actions.

Plain SQL is used on purpose: later parts of the design rely on PostgreSQL features (`ON CONFLICT`, `SELECT ... FOR UPDATE`, `SKIP LOCKED`, deferred constraints) that should stay visible in the code.
