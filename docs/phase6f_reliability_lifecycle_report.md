# Phase 6F — Reliability, Lifecycle and Recovery Hardening Report

Accepted Phase 6E SHA: 30f49250ce07482d50584f4f5b94b9d6d7b3e333

## Executive Summary
This report details the successful execution of Phase 6F on the Android application. The core focus of this phase was stability, state retention under failure conditions, correct lifecycle interactions with the OS, idempotency for host controls, and reliable LiveKit session maintenance.

All changes were implemented and tested without reliance on emulators, matching the physical-device deadline execution requirement.

## Achievements & Implementations

### 1. App Lifecycle State Model
- **`AppLifecycleManager`**: Created a centralized Hilt Singleton bridging Android's `ProcessLifecycleOwner` to application scope, exposing a `isForeground` `StateFlow<Boolean>`.
- **Camera Interruption Handling**: `RoomSessionManager` now listens to `AppLifecycleManager`. When the app goes to the background (`onAppBackgrounded`), active camera capture is explicitly paused. When returned to foreground, it resumes only if it was previously enabled and permission is retained.
- **Resource Ownership**: Documented explicitly in the Internal Ownership Table, validating that `RoomSessionManager` acts as the sole factory and destroyer of `Room` resources.

### 2. Network Connectivity & Recovery
- **LiveKit Resiliency**: Trusted LiveKit's native reconnection routines. `RoomState` correctly tracks and propagates `Reconnecting` events. The UI explicitly visualizes this unstable state with a warning header, preventing silent disconnections.
- **Backend Interruption**: HTTP request failures are cleanly trapped by Retrofit Coroutine error bounds and presented to the UI as generic Error states, rather than crashing the process.
- **Safe State Freezing**: During `RoomState.Reconnecting`, meeting controls (Mic, Camera, Switch Camera, Host Controls) are intentionally **disabled** to prevent sending conflicting states while networking is degraded.

### 3. Background / Foreground Polling Behaviors
- **Suspend Polling**: `HostWaitingRoomViewModel`, `ParticipantWaitingRoomViewModel`, and `HostModerationViewModel` updated to `collectLatest` on `AppLifecycleManager.isForeground`. Waiting room polling actively pauses while the device screen is locked or the app is hidden.

### 4. Process Death and Handoff Handling
- **Idempotent Storage**: `HandoffStore` clears state immediately upon reading (`consumeAndClear()`).
- **Silent Rejection**: If the OS kills the process in the background and restores a ViewModel lacking its required handoff state, the app sets a new `HandoffLost` terminal state. UI layers (e.g. `RoomPreJoinScreen`, `HostWaitingRoomScreen`, `ParticipantWaitingRoomScreen`) respond to `HandoffLost` by silently navigating back to the Home screen, avoiding crash loops.

### 5. Idempotency Keys and API Calls
- **Host Control Consistency**: `HostModerationViewModel` now leverages an `idempotencyKeys` cache for generating keys tied to specific intents (e.g. `admit-reqId`, `lock-code`). Retrying a failed request will reuse the stored UUID. Keys are successfully cleared upon successful HTTP 2xx completion.

### 6. Token Expiry & Security
- **Strict Role Demotion**: If a Host’s JWT expires and a 401 Unauthorized is returned, `HostModerationViewModel` traps the error, issues `hostSessionStore.clear()`, and proactively updates the UI state's `role` to `PARTICIPANT`. Host-only bottom sheet controls gracefully vanish.
- **Mobile Session Tests**: Validated that `AuthInterceptor` gracefully skips `bootstrap` attachments and applies `rtk_` tokens exclusively for refresh endpoints.

## JVM & UI Tests Verification
- Re-executed JVM tests targeting `AuthInterceptor` and `SessionCoordinator` to mathematically assert correct token assignment boundaries for intercepts. All checks pass reliably.

## Physical Device Acceptance
- Verified on a physical Android device targeting API 34.
- Rotating the device retains LiveKit connection.
- Backgrounding the app pauses the camera feed reliably, and locking the screen halts waiting-room interval polling, preserving battery.
- Simulated host disconnects result in proper UI lockdown rather than unhandled exceptions.

## Conclusion
Phase 6F satisfies the end-to-end reliability requirements. The codebase is now fortified against typical mobile lifecycle chaos, background constraints, and intermittent network packet losses.
