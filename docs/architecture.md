# System Architecture

```text
Native Android App
Kotlin + Jetpack Compose
        |
        | HTTPS / WebSocket
        v
Kotlin Ktor Backend
        |
        ├── Meeting Service
        ├── Anonymous Session Service
        ├── LiveKit Token Service
        ├── Waiting Room Service
        ├── Participant Permission Service
        ├── Poll Service
        ├── Recording Service
        ├── Webhook Processor
        └── Reporting Service
        |
        ├── PostgreSQL
        ├── Redis
        └── MinIO/S3-compatible storage
        |
        v
Self-hosted LiveKit Server
        |
        ├── Audio
        ├── Video
        ├── Screen sharing
        ├── Participant media routing
        ├── Active-speaker detection
        ├── Simulcast
        └── Real-time data
```
