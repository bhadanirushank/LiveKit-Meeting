# LiveKit Meeting Application

A Native Android Video-Meeting Application backed by a Kotlin Ktor server and self-hosted LiveKit.

## Architecture

* **Client**: Native Android, Kotlin, Jetpack Compose
* **Backend**: Kotlin Ktor, Exposed/jOOQ, PostgreSQL, Redis
* **Media Engine**: Self-hosted LiveKit

## Setup for Development

Please refer to the following documents for setting up your environment:
* [Architecture Overview](docs/architecture.md)

1. Copy `.env.example` to `.env` and fill in any necessary values (or use defaults for local dev).
2. To build and run the backend inside Docker along with the infrastructure:
   ```powershell
   docker compose build backend
   docker compose up -d
   ```
3. To run the Ktor backend locally for rapid development:
   ```powershell
   cd backend
   .\gradlew.bat run
   ```

## Android Emulator Network
* Backend: `http://10.0.2.2:8080`
* LiveKit: `ws://10.0.2.2:7880`

## Physical Device Network (Same WiFi)
* Backend: `http://<computer-lan-ip>:8080`
* LiveKit: `ws://<computer-lan-ip>:7880`
