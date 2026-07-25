# OWASP Dependency-Check Security Remediation Plan

This plan addresses the high-priority vulnerabilities identified by the OWASP Dependency-Check CI scan, upgrading affected libraries across the backend application.

## Deduplicated Vulnerability Assessment
*The complete assessment has been saved as part of the Batch 0 baseline in `batch0_baseline_report.md` and `batch0_assessment.json`.*

## Proposed Changes (Controlled Batches)

### Batch 0: Baseline and Dependency Graph (COMPLETED)
- Saved current dependencyInsight outputs for Netty, Kotlin, Jackson, PostgreSQL, Protobuf, Guava, and Okio.
- Saved existing test and security-scan metrics bound to explicit Phase 5 commit.
- Created dedicated remediation branch (`remediation/security-upgrades`).

### Batch 1: Kotlin Toolchain Assessment
We will conduct two separate experiments:

**Experiment 1 — Stable compatibility:**
- Upgrade to Kotlin `2.4.10` on the remediation branch.
- Compile and test.
- Do NOT claim the CVE-2026-53914 is fixed.

**Experiment 2 — Security-fix compatibility:**
- Create a child branch: `remediation/kotlin-2.4.20-beta2`
- Test Kotlin `2.4.20-Beta2`.
- Never merge the EAP version automatically.
- Record compatibility with Gradle, serialization, Ktor, tests and CI.
- Decide separately whether a pre-release build tool is acceptable.
- If Beta2 is not accepted, document the Kotlin finding as formally reviewed build-tool technical debt with interim cache-hardening controls, an exact expiry date, and stable `2.4.20` upgrade trigger.

### Batch 2: Frameworks (Ktor, Lettuce, Netty)
- Upgrade owning frameworks first (Ktor, Lettuce).
- Resolve all Netty modules to `4.1.135.Final` or later.
- A Netty BOM may be used only after Ktor, Lettuce and LiveKit ownership has been mapped and compatibility with one aligned 4.1.135.Final-or-later version has been demonstrated.

### Batch 3: Jackson & Logging
- Use Jackson `2.18.9` or later compatible fixed 2.x line for ALL Jackson components.
- Upgrade logstash encoder or other owners where appropriate.

### Batch 4: Infrastructure Libraries
- PostgreSQL JDBC: `42.7.11+`
- Protobuf Java: `3.25.5+`
- Guava: `33.2.1-jre`
- Okio: Identify introducing dependency. If 1.x required, try `1.17.6`. Use `3.x` only if supported by owner.

## Verification Plan

### Automated Tests (After Each Batch)
- `.\gradlew.bat compileKotlin --no-daemon --stacktrace`
- `.\gradlew.bat test --no-daemon --info --stacktrace`
- Run `dependencyInsight` for each changed dependency family.
- Record resolved versions, dependency paths, compilation result, test counts, new warnings, removed CVEs, remaining CVEs.
- Commit each successful batch separately. Stop immediately when a batch causes incompatibility.

### Final Regression Verification (After All Accepted Batches)
- `.\gradlew.bat clean test --no-daemon --info --stacktrace`
- `.\gradlew.bat composeIntegrationTest --rerun-tasks --no-daemon --info --stacktrace`
- `.\gradlew.bat dependencyRecoveryTest --rerun-tasks --no-daemon --info --stacktrace`
- `.\gradlew.bat loadTest --rerun-tasks --no-daemon --info --stacktrace`
- `.\gradlew.bat build --no-daemon --info --stacktrace`

Then:
- Rebuild the Docker image
- Rerun GitHub Dependency-Check
- Rerun pinned Trivy
- Rerun Git-history and working-tree Gitleaks scans
- Compare before-versus-after findings
