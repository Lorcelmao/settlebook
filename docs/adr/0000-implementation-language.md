# ADR 0000: Implementation language (Java/Spring Boot go/no-go)

- **Status:** Accepted: Java/Spring Boot (GO), decided by the author on 2026-09-28.
- **Date opened:** 2026-09-28

## Context

The project exists partly to prove Java/Spring Boot backend skill. Before this project, the author's own Spring Boot code was minimal, so productivity in Java was the largest unknown in the plan.

The original go/no-go measured how long the author took to build the skeleton and first slice. At the author's request, that skeleton (the order API, Flyway, Testcontainers, CI) was generated with an AI coding assistant. Its build time therefore says nothing about the author's Java productivity, and **cannot be used for this decision**.

## Replacement measurement: a timed follow-up slice written alone

Implement the slice below **without AI-generated code**:
- AI coding assistants and autocompletion are switched off.
- Official documentation, Spring guides, Stack Overflow and books are allowed.
- Log any conceptual question asked to an AI in the time log, and never paste back code from it.

**Slice: list orders.** `GET /api/orders`
- Optional `status` filter.
- Newest first, with keyset pagination: `limit` 1-100 (default 20), and an opaque `cursor` encoding `(created_at, id)` of the last row.
- Response contains `items` and `nextCursor` (null on the last page).
- Invalid `status`, `limit` or `cursor` gives 400 `application/problem+json`.
- Flyway `V2` adds an index that supports the query.
- A Testcontainers integration test covers:
  - ordering across at least 3 pages with ties on `created_at`;
  - the status filter;
  - each invalid parameter.

Before starting, read the existing code under `src/main/java/dev/settlebook/order`. Reading time is logged separately and does not count toward the threshold.

## Decision criteria

| Signal | GO | CAUTION | NO-GO |
|---|---|---|---|
| Hours to finish the slice (CI green) | ≤ 4 h | 4-6 h | > 6 h |
| Can explain every annotation, bean and SQL statement in the slice **and** the existing order code without looking it up | yes | mostly | no |
| Share of time stuck on Spring/Java plumbing rather than the problem | < 30% | 30-50% | > 50% |
| Confidence writing the next slice | fine | uneasy | dreading it |

- **GO:** continue in Java.
- **CAUTION:** continue in Java, but cut the ops console to 3 screens and defer property-based tests.
- **NO-GO:** rebuild in FastAPI with the same design.

The thresholds are the original phase-0 limits scaled to the size of this slice. The original was a whole skeleton at 14 h / 20 h; a single slice gets 4 h / 6 h.

## Result

**GO: continue in Java.** The author decided this before implementing the timed slice, so the measurement above was not taken and there is no timing evidence behind the decision.

The remaining safeguard is the plan's checkpoint at the end of the correctness-core phase: if work on the skeleton through that phase exceeds about 55 hours of the author's time, cut scope (the ops console to 3 screens) rather than continue at the original size.
