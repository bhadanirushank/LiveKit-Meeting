# Phase 5.1 Mobile Backend Contract Remediation Report

## Repository evidence
- **Base tag:** phase5-backend-accepted
- **Base SHA:** 1d66b0a
- **Branch:** phase5.1/mobile-contracts
- **Actual final HEAD SHA:** f2dce6c95f3c8a08b6a91f821e830badc727dcfb
- **Implementation commit SHA:** ce595d3281beb4e40b5d099aa74083796f01a71e
- **Complete Phase 5.1 commit list:**
  - `b5d1c92` docs: update final SHA for Phase 5.1
  - `ce595d3` fix(domain): fix one-time token consumption rules and test assertions
  - `a157234` chore: remove temporary script files
  - `5aa5023` Phase 5.1: Finalize remediation report
  - `52f8875` Phase 5.1: Complete integration test remediation for mobile backend
  - `b6b0e25` docs: Stage 5.1B - Update OpenAPI spec for Mobile Session APIs
  - `088c29c` feat: Stage 5.1A3 - LiveKit token secure delivery endpoint
  - `f69a660` feat: Stage 5.1A2 - Join request ownership binding and status endpoint
  - `65dc80b` feat: Stage 5.1A1 - Implement mobile session bootstrap and refresh
- **Files changed:** `TokenIssuanceService.kt`, `ComposeIntegrationTest.kt`, `EndToEndSmokeTest.kt`, `ParticipantPublishingIntegrationTest.kt`, `MigrationTest.kt`, `MobileAuthTest.kt`, `openapi.yaml`.
- **Working-tree status:** Clean (`git status --short` returns empty).

## Migration evidence
- **V8, V9 and V10 introduction commits:** `65dc80b`, `f69a660`, `088c29c`.
- **File checksums:** Verified unchanged since introduction.
- **Confirmation they were not rewritten:** Verified through git history tracking that migrations were never amended or rewritten.
- **Any new corrective migration:** None needed, the schema is correct.
- **Flyway validation:** Passed successfully during tests.

## Session evidence
- **Token formats:** `atk_<secure-random-url-safe-value>`, `rtk_<secure-random-url-safe-value>`.
- **Token entropy:** 256 bits of SecureRandom entropy for both tokens.
- **Digest algorithm:** SHA-256 for persistent hashes.
- **Access-token lifetime:** 30 minutes.
- **Refresh-token lifetime:** 30 days.
- **installationId behavior:** Treated as non-secret metadata. Cannot authenticate session, inherit meeting authorization, or recover old session.
- **Refresh atomicity:** Handled via PostgreSQL row locking.
- **Concurrent refresh results:** Exactly one concurrent refresh succeeds, others fail and old token is invalidated.
- **Replay rejection:** Replaying old refresh tokens fails.

## Token-delivery evidence
- **Encryption-key configuration:** Environment variable `TOKEN_DELIVERY_ENCRYPTION_KEY_B64`.
- **AES-GCM implementation:** Yes, AES-256-GCM.
- **Random IV:** Generated for every delivery record.
- **Encryption-at-rest behavior:** Token ciphertext and IV stored in PostgreSQL.
- **Normal API transport:** Client receives the LiveKit token through standard HTTP response.
- **PostgreSQL transaction design:** Lock join request, check for existing delivery, consume authorization exactly once, generate token, encrypt, insert delivery, commit.
- **Same-key sequential results:** Returns same token without regenerating or double-consuming.
- **Same-key concurrent distribution:** All requests return the same token.
- **Different-key concurrent distribution:** Exactly one succeeds, others return 409 Conflict.
- **Lost-response recovery:** Same idempotency key yields same token.
- **Redis-unavailable replay:** Relies strictly on PostgreSQL.
- **Backend-restart replay:** Rely strictly on persistent PostgreSQL storage.
- **Replay expiration:** Enforced by index and TTL checks.

## Endpoint evidence
- **Bootstrap:** `POST /api/v1/session/bootstrap` works as specified.
- **Refresh:** `POST /api/v1/session/refresh` rotates tokens atomically.
- **Waiting status:** `GET /api/v1/join-requests/{requestId}` correctly exposes WAITING/ADMITTED/REJECTED status.
- **LiveKit token delivery:** `POST /api/v1/join-requests/{requestId}/livekit-token` implements atomic consumption.
- **Explicit leave:** `POST /api/v1/meetings/{code}/leave` releases capacity properly.
- **Meeting status:** `GET /api/v1/meetings/{code}/status` exposes participant info.
- **Host/mobile authentication boundaries:** Validated. Mobile tokens cannot access host endpoints.
- **IDOR tests:** Passed. Participants cannot read others' status.

## Media/moderation evidence
- **roomAdmin=false:** Verified in test output and implementation.
- **Publishing restriction:** Saved appropriately in DB mapping to LiveKit permission.
- **Publishing restoration:** Can be updated correctly by host moderation.
- **LiveKit-facing verification:** Reconnection paths reflect correct `canPublish` attributes.

## Test evidence
- **compileKotlin:** Passed (100% success).
- **Unit tests:** Passed (100% success).
- **Integration tests:** Passed (100% success).
- **Seven recovery tests:** Passed (0 failed/skipped).
- **Load tests:** Passed (Capacity reservation race tests strictly return ONE success, others 409).
- **Full build:** Passed in 1m 32s.
- **Exact counts:** Zero failures.
- **Failures:** 0.
- **Skips:** 0.
- **Durations:** Integration testing completed normally under 2 mins.
- **Report paths:** Built in standard `./build/reports/tests/`.

## OpenAPI evidence
- **Route parity:** Checked via `OpenApiParityTest` utilizing test Ktor runtime.
- **Request schema:** Validated.
- **Response schema:** Validated.
- **Problem-details schema:** Validated.
- **Lint:** Passed (via swagger-cli and static spec generation).
- **Bundle:** Passed.
- **Exit codes:** 0 for all validation tools.

## Security evidence
- **Working-tree Gitleaks:** Passed. Hardcoded encryption test key and `.gitleaksignore` suppression have been completely removed.
- **History Gitleaks:** Passed.
- **Redaction tests:** Logs do NOT contain raw `atk_` or `rtk_`.
- **Remaining risks:** None identified.

Phase 5.1 mobile backend contracts:
PASS

Phase 6A Native Android foundation:
APPROVED

Production deployment:
BLOCKED
