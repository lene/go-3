# Security Model

This document describes the security measures implemented in the Go 3D server.

## Authentication

Move requests (`/set`, `/pass`) require a Bearer token in the `Authentication` header:

```
Authentication: Bearer <token>
```

Tokens are issued at player registration (`GET /register/{gameId}/{color}`) and are:
- Random 32-character alphanumeric strings
- Scoped to a specific game and color (cannot be used across games)
- Valid for 24 hours after issuance
- Automatically cleaned up when the game is archived or deleted

Unauthenticated move attempts return HTTP 401 Unauthorized.

## Rate Limiting

The game-creation and player-registration endpoints are rate-limited per client IP
to prevent automated abuse:

- Endpoint: `GET /new/{size}` — create a new game
- Endpoint: `GET /register/{gameId}/{color}` — register a player

Limit: **10 requests per 60-second sliding window per IP address**.

Requests that exceed this limit receive HTTP 429 Too Many Requests.

The IP address is determined from (in order of preference):
1. `X-Forwarded-For` header (first entry, for reverse-proxy deployments)
2. `X-Real-IP` header
3. Direct connection remote address

Read-only endpoints (`/status`, `/openGames`, `/health`) and move endpoints
(`/set`, `/pass`) are not rate-limited.

## Transport Security

The server itself does not implement TLS. In production, it should be deployed
behind a TLS-terminating reverse proxy (e.g., nginx, Caddy, or a cloud load
balancer) to encrypt traffic in transit.

## Input Validation

- Board size: validated against `MinBoardSize` and `MaxBoardSize` constants
- Game IDs: validated to match the 6-character alphanumeric format
- Color: validated against the allowed color values (`@` for Black, `O` for White)
- Coordinates: validated to be within the board bounds on move submission
- Request path length: capped at 100 characters (HTTP 414 for longer paths)

## Known Limitations

- No support for user accounts or persistent identity across sessions
- Move endpoints use GET (not POST/PUT) for client simplicity; this is a
  deliberate design trade-off and not a security issue given token auth
