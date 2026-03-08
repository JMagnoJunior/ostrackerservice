# OS Tracker Service - Agent Mandates

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

## Multi-Agent Role
- This agent acts as `Arquiteto Dev Backend` in the multi-agent workflow described at `/Users/magnojunior/projects/os-tracker/agent.md`.
- The backend architect does not update Notion directly. Status updates are emitted to the orchestrator agent only.

## Backend Workflow
1. Receive backend subtask assignment from orchestrator (`SPEC_BACKEND` or `IMPL_BACKEND`).
2. Emit `SUBTASK_STARTED` event.
3. Before writing tech spec, read current backend code and existing docs to capture actual state.
4. Write tech spec using `$backend-tech-spec`, including integration contract when mobile/frontend depends on backend changes.
5. Emit `SUBTASK_DONE` event with spec artifact path.
6. Emit `SUBTASK_STARTED` for implementation.
7. Implement backend changes in service code.
8. Run validations: `./gradlew test` and `./gradlew ktlintCheck`.
9. Emit `SUBTASK_DONE` for implementation with test summary.

## Tech Spec Location Rule
- Backend tech specs must be created/updated in `/Users/magnojunior/projects/os-tracker/ostracker-docs/specs/`.
- Do not save tech specs inside `ostrackerservice`.
- Naming convention: `TECH_SPEC_<TICKET_ID>_BACKEND.md`.

## Event Emission Command
- Generate event json:
- `python3 -m automation.orchestrator new-event --event-type SUBTASK_STARTED --ticket-id <TICKET_ID> --subtask-type SPEC_BACKEND --agent backend-architect --output /tmp/<ticket>-spec-backend-started.json`
- Send event to orchestrator:
- `python3 -m automation.orchestrator apply-event --event-file /tmp/<ticket>-spec-backend-started.json`
- Async mode (recommended):
- write event files to `automation/inbox/` and read orchestrator notifications from `automation/outbox/backend-architect/`.

## Integration Contract Rule
- For tickets involving backend + mobile/frontend, backend tech spec must define endpoint/interface contract before mobile/frontend implementation starts.
- Contract must include endpoint path, method, request schema, response schema, auth rules, and error cases.
