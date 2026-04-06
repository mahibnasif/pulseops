# ADR 001: Modular monolith

- Status: Accepted
- Date: 2026-07-30

## Decision

PulseOps begins as one Spring Boot deployment with domain-oriented packages and
one PostgreSQL database.

## Rationale

The product needs strong transactions between service status, incidents,
timeline events, and notifications. A modular monolith keeps those operations
understandable and deployable by a small team while preserving boundaries that
can be extracted later if measured load or ownership requires it.

## Consequences

Module boundaries require discipline because the compiler does not enforce a
network boundary. Deployments scale the whole backend together. In return,
local development, debugging, transactions, and operations remain simpler.
