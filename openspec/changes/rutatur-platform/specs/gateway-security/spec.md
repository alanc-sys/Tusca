# Gateway Security Specification

**Capability**: `gateway-security`

## Purpose

Defines the single externally reachable ingress: routing to the functional services, JWT validation against Keycloak, role enforcement per endpoint, identity propagation, CORS, and isolation of internal paths. The gateway adds no business endpoints of its own.

## Requirements

### Requirement: Single ingress and route table

Only the gateway MUST be exposed outside the service network. It MUST route `/api/pois/**` to `poi-service`, `/api/itinerary/**` and `/api/itineraries/**` to `itinerary-service`, `/api/routes/**` to `route-match-service`, and `/api/admin/**` to the administrative surface. Any other business path MUST return 404 without forwarding.

#### Scenario: Direct service port closed

- GIVEN the deployed stack
- WHEN a client calls a functional service port from outside the network
- THEN the call MUST fail
- AND the same operation MUST succeed through the gateway

#### Scenario: Unknown business path

- GIVEN a path outside the owned prefixes
- WHEN a client calls it
- THEN the gateway MUST respond 404
- AND MUST NOT forward it

### Requirement: JWT validation

For every non-public path the gateway MUST validate the token signature against the realm's JWKS, the issuer `rutatur-realm`, and the expiration. A missing, malformed, expired, or wrongly signed token MUST produce 401.

#### Scenario: Missing token

- GIVEN `GET /api/itineraries` without `Authorization`
- WHEN the request reaches the gateway
- THEN the response MUST be 401

#### Scenario: Invalid or expired token

- GIVEN an expired token, or one signed by an unknown key
- WHEN it is validated
- THEN the response MUST be 401

### Requirement: Role enforcement

`ROLE_USER` paths MUST require the `ROLE_USER` authority and `/api/admin/**` MUST require `ROLE_ADMIN`. An authenticated caller lacking the required role MUST receive 403.

#### Scenario: Authenticated without role

- GIVEN a valid token without `ROLE_USER`
- WHEN the caller calls `/api/itineraries`
- THEN the response MUST be 403

#### Scenario: Admin path with user role

- GIVEN a valid token with `ROLE_USER` only
- WHEN the caller calls `/api/admin/**`
- THEN the response MUST be 403

### Requirement: Public endpoints

`GET /api/pois/search` and `POST /api/itinerary/generate` MUST be callable without a token. When a token is supplied there, it MUST be validated and the identity forwarded downstream.

#### Scenario: Anonymous access allowed

- GIVEN no token
- WHEN a public path is called
- THEN the request MUST be forwarded without 401

#### Scenario: Optional token still validated

- GIVEN an invalid token on a public path
- WHEN the request is evaluated
- THEN the response MUST be 401

### Requirement: Internal path isolation

`/internal/**` MUST NOT be routable. Requests to those paths MUST fail before reaching any service, regardless of token validity.

#### Scenario: Internal path blocked

- GIVEN a valid `ROLE_USER` token
- WHEN `/internal/pois/candidates` or `/internal/itineraries/{id}` is requested through the gateway
- THEN no service MUST receive it
- AND the response MUST be 404

### Requirement: Identity propagation

The gateway MUST forward the caller's subject and authorities downstream, and services MUST derive ownership from those claims rather than client-supplied identifiers.

#### Scenario: Subject reaches the service

- GIVEN an authenticated call to `/api/itineraries`
- WHEN the request is forwarded
- THEN the service MUST observe the JWT subject as caller identity

### Requirement: CORS and documentation

The gateway MUST apply a configured CORS policy, MUST answer preflight `OPTIONS` without a token, and MUST serve the aggregated Swagger surface without declaring endpoints it does not proxy.

#### Scenario: Preflight

- GIVEN an `OPTIONS` preflight from an allowed origin
- WHEN it reaches the gateway
- THEN the response MUST carry the CORS headers and MUST NOT be 401

#### Scenario: Aggregated docs

- GIVEN a running stack
- WHEN the Swagger UI path is requested
- THEN the aggregated documentation MUST be served
