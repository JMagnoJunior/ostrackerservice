# OS Tracker Service - GEMINI Mandates

## Tech Stack
- **Database:** PostgreSQL
- **Language:** Kotlin
- **Framework:** Spring Boot
- **Deployment:** Docker
- **Observability:** Micrometer
- **Integration Testing:** WireMock and TestContainers
- **Linting:** Ktlint

## Development Standards
- All changes must adhere to the idiomatic Kotlin style.
- New features or bug fixes must include integration tests using TestContainers and WireMock (if external APIs are involved).
- Run `ktlint` to ensure code quality before finishing any task.
- Ensure proper Micrometer instrumentation for any new service logic.
