# Interview Coach

A REST API backend for a mock-interview practice platform. Users run through **MCQ**, **coding**,
and **open-ended** interview questions across three session types (custom practice on a topic,
timed real-interview simulations, and free-form mock sessions), with coding answers graded
automatically against test cases via **Judge0** and open-ended answers graded by an **LLM**
(Anthropic API) - both asynchronously, off the request thread.

Built with Spring Boot 4.1 / Java 17 as a backend-focused portfolio project.

## Quickstart (Docker)

```bash
cp .env.example .env
# edit .env: at minimum set DB_PASSWORD and JWT_SECRET (see comments in the file)
docker compose up --build
```

That's it - `docker compose` starts MySQL (auto-creating the `interview_coach` schema), waits for
it to report healthy, then starts the app, which creates all tables via Hibernate and seeds demo
data on first boot.

Once it's up:

- **Swagger UI:** http://localhost:8080/swagger-ui.html - browse and call every endpoint from the
  browser. Log in via `POST /api/auth/login` with one of the seeded accounts below, then paste the
  returned token into the **Authorize** button.
- **Demo logins** (both pre-verified, so they can log in immediately):
  - `demo.user@example.com` / `Password123!` - `ROLE_USER`
  - `demo.admin@example.com` / `Password123!` - `ROLE_ADMIN`

> The compose file publishes the containerized MySQL on host port **3307**, not 3306, in case a
> local MySQL install is already using 3306. The app talks to it internally on the default port
> via the `mysql` service name either way.

## Tech stack

- **Spring Boot 4.1**, Java 17
- **Spring Security** with stateless JWT authentication
- **Spring Data JPA** / Hibernate, MySQL (runtime) / H2 (tests)
- **springdoc-openapi** (Swagger UI / OpenAPI 3)
- **Docker** / Docker Compose
- **Judge0** for sandboxed code execution and grading
- **Anthropic API** for AI grading of open-ended answers
- **Whisper (OpenAI-compatible speech-to-text API)** for transcribing voice answers before grading

## Architecture

```
controllers -> services/core (+ services/email) -> repositories (Spring Data JPA) -> entities
```

Request/response shapes live in `dto`; domain exceptions live in `exceptions` and carry
`@ResponseStatus` directly (e.g. `SessionNotFoundException` -> 404) rather than being routed
through a `@RestControllerAdvice` - see "Design decisions" below.

**Auth** is JWT-based and stateless: `JwtFilter` reads the `Authorization: Bearer` header,
validates the token, and - only if the user is verified and not currently banned - populates the
security context with a single `ROLE_<Role>` authority. `SecurityConfig` wires the filter, disables
CSRF (a pure stateless API needs no CSRF token), and defines route rules: `/api/auth/**` and the
Swagger routes are open, `/api/admin/**` requires `ROLE_ADMIN`, everything else requires
authentication - including starting a session, so an anonymous caller can never do that. There is
deliberately **no public question-browsing endpoint**: a regular user only ever sees a question as
part of an active session (`POST /api/sessions/start`); only an admin can list/inspect the raw
question bank (`GET /api/admin/questions`, answer key included).

**Interview sessions:** a `Session` (one of `CUSTOM_PRACTICE` / `REAL_INTERVIEW` / `FREE_MOCK`) owns
an ordered list of `SessionQuestion`s, each with at most one `Attempt`. `SessionService.startSession`
picks questions differently per type - a fixed count for custom practice, a count derived from a
time limit for real interviews (mixing coding and open-ended questions at a fixed ratio), and
either for free mock. `AttemptService` enforces ownership, session state, and "not already answered"
before accepting a submission.

**Session deadlines are real and server-enforced**, not just numbers stored for display. Every
session gets a deadline the moment it starts - 110% of the chosen time for Real Interview, 150%
for Free Mock, 200% of the questions' summed time budget for Custom Practice (multipliers
configurable). Once the deadline (plus a short grace period for network latency) passes, the
session is over: every endpoint re-checks the deadline before doing anything, so a late submission
is rejected, never accepted - there's no window where a request arriving after the deadline still
gets processed. A background sweep (`SessionSweepService`) separately marks the session closed in
the database even if the user never makes another request at all, so its stored status doesn't
stay stale forever. A session that hits its deadline (or
gets completed manually) while a coding/open-ended answer is still grading doesn't get stuck or
rejected - it moves to an `AWAITING_GRADING` state (202 response) and finishes on its own once the
last grade lands, with no client action required.

**Real Interview is strictly sequential.** Unlike the other two types, it reveals only the current
question at a time (`GET /api/sessions/{id}/current-question`) and rejects answering or giving up
out of turn - Free Mock and Custom Practice remain unordered, full question list up front.

**Voice-answer timing is server-recorded, not client-reported.** A pair of endpoints
(`.../voice/start`, `.../voice/stop`) let the client mark when a voice recording begins and ends;
the server computes `timeTakenSeconds` from its own clock rather than trusting whatever the device
sends. A plain typed answer (these endpoints never called) is unaffected.

**Async grading** is the part worth reading closely. `JudgeService` (code) and `AiGradingService`
(open-ended) both grade off the request thread, using:

```
ApplicationEventPublisher.publishEvent(...)   // inside the same @Transactional method that inserts the row
        |
        v
@TransactionalEventListener(phase = AFTER_COMMIT)   // only fires once that transaction has committed
        |
        v
@Async   // runs on a separate thread pool, calls the external API, writes the result
```

The event is published *inside* the transaction that creates the `CodeSubmission`/`VoiceAnswer`
row, but the listener only fires *after that transaction commits* - so grading can never race ahead
of the row it depends on and query a row that isn't there yet. Each grader is wrapped so a failure
(a timed-out external call, a malformed response) can never leave an `Attempt` stuck at `PENDING`
forever - it's marked `FAILED` and the grader moves on rather than propagating the exception. Each
grader also nudges its parent session toward completion afterward (`finalizeIfGradingComplete`),
so an `AWAITING_GRADING` session doesn't wait around once its last attempt has actually resolved;
`SessionSweepService` is the guaranteed backstop for the rare case both graders miss that handoff,
and separately reclaims any attempt that's been stuck `PENDING` too long (e.g. the app restarted
mid-grade).

**Voice answers get an extra hop first: transcription.** `POST /{sessionQuestionId}/voice/submit`
(multipart, after `voice/start`/`voice/stop`) uploads the actual recording. It's stored via
`AudioStorageService` - `LocalDiskAudioStorageService` by default, kept behind an interface
specifically so a later move to S3/R2 is a new implementation class, not a rewrite of every caller
- and publishes `VoiceAudioSubmittedEvent` rather than triggering grading directly, since there's
no transcript to grade yet. `TranscriptionService` (same `@Async` +
`@TransactionalEventListener(AFTER_COMMIT)` shape as the graders above) calls a
Whisper-compatible endpoint, saves the transcript, and then publishes the *existing*
`VoiceAnswerCreatedEvent` itself - so `AiGradingService` grades a transcribed voice answer exactly
the same way it grades a typed one, no special-casing needed downstream:

```
voice/submit -> store audio -> publish VoiceAudioSubmittedEvent
        |
        v
TranscriptionService (AFTER_COMMIT, async) -> Whisper API -> save transcript
        |
        v
publish VoiceAnswerCreatedEvent -> AiGradingService grades it like any typed answer
```

## Running without Docker

MySQL must be installed and running locally.

```sql
CREATE DATABASE interview_coach;
```

Then set the required env vars (see `.env.example` for the full list and format notes -
`JWT_SECRET` in particular must be Base64-encoded) and run:

```powershell
$env:DB_PASSWORD="..."
$env:JWT_SECRET="..."
$env:BREVO_API_KEY="..."
$env:SENDER_EMAIL="..."
$env:SEED_ENABLED="true"
$env:OPENAI_API_KEY="..."       # optional - voice-answer transcription; blank skips it
$env:AUDIO_STORAGE_DIR="..."    # optional - defaults to ./uploads/audio
.\mvnw.cmd spring-boot:run
```

Hibernate creates all tables from the JPA entities on first boot (`ddl-auto: update`); no manual
schema work beyond creating the empty database.

## Testing

137 tests across three layers, all against an in-memory H2 database:

- **Repository tests** (`src/test/java/interview_coach/repositorytests`) - full
  `@SpringBootTest` + `@Transactional` integration tests (auto-rollback), not sliced
  `@DataJpaTest`s, including the native `RAND()`-based random-question-selection queries.
- **Service tests** (`.../servicetests`) - Mockito unit tests, notably covering the session
  question-selection algorithm and deadline math (`SessionServiceStartTest`,
  `SessionServiceDeadlineTest`), the REAL_INTERVIEW ordering guard and voice-timing sequencing
  (`AttemptServiceOwnershipTest`, `AttemptServiceVoiceTimingTest`), the scheduled sweep
  (`SessionSweepServiceTest` plus a real-Spring-context `SessionSweepServiceIntegrationTest` for
  the cross-repository finalization behavior a mock can't prove), and the async event-publishing
  contract.
- **Controller tests** (`.../controllertests`) - `@WebMvcTest` slices (with `JwtFilter` excluded)
  for HTTP-contract behavior, plus a couple of full-context `@SpringBootTest` +
  `@AutoConfigureMockMvc` tests where the real filter chain matters (role enforcement, JWT
  expiry/malformed-token handling).

```bash
.\mvnw.cmd test                                  # full suite
.\mvnw.cmd test -Dtest=SessionServiceStartTest    # a single class
```

## Design decisions & known tradeoffs

Written down deliberately rather than left implicit - these are choices, not oversights:

- **`ddl-auto: update`, no Flyway/Liquibase.** Appropriate for a portfolio project seeded fresh on
  every demo; a real deployment with data worth preserving would need real migrations instead.
- **JWT subject is the user's email, not `userId`** - a known gap given email is user-editable
  post-registration. Fine for the current scope; `userId` would be the correct long-term subject.
- **No `@RestControllerAdvice`.** Domain exceptions carry `@ResponseStatus` directly, which is
  simpler for this project's size but means error response *bodies* are Spring's default shape
  rather than a consistent custom envelope. A larger API would want the advice layer for that.
- **`@Valid`/bean validation is only wired up on `AttemptController` and `AdminController`
  so far** - `AuthController`, `UserController`, and `SessionController`'s request DTOs don't yet
  carry constraint annotations. Planned alongside the endpoints the frontend will need.
- **Question moderation is soft-delete only** (`Question.active`) - a deleted question is never
  physically removed, since past `Attempt`s and `Session`s reference it.
- **DTOs, never entities, cross the controller boundary**, and deliberately exclude
  `User.passwordHash`, `Option.correctOption`, `CodingChallenge.referenceSolution`, and any
  `TestCase` where `isHidden = true`.
- **No public question-browsing endpoint, by design.** The question bank is only ever exposed
  through an active session (safe projection, no answer key) or to an admin (full projection,
  answer key included via `GET /api/admin/questions`) - never to an anonymous or merely-logged-in
  caller browsing the raw bank.
- **AI grading prompt has no injection-hardening yet** (no delimiters or "treat this as untrusted
  data" framing around the user's submitted answer). A known next step before this touches
  anything beyond demo data.
- **No public topic-listing endpoint yet** - deliberately deferred until the Vite frontend exists
  to design it against. Session history, by contrast, is implemented: `GET /api/sessions/me` lists
  every session belonging to the caller (newest first), and a single session's own status/summary
  (`GET /api/sessions/{id}`) now also includes the caller's own answer/code/text per question and,
  for MCQ, the correct option once the session is no longer in progress.
- **Voice-answer audio storage is local disk, not cloud, for now.** Upload, storage, and
  transcription are all implemented (see "Async grading" above) - `LocalDiskAudioStorageService`
  is the only `AudioStorageService` implementation, chosen deliberately for dev/demo simplicity.
  Swapping in S3/R2 later is a new implementation class behind the same interface, not a rewrite
  of `AttemptService`/`TranscriptionService`. There's also no playback/download endpoint for the
  stored audio yet - only the transcript is ever exposed back to the client.
- **A started-but-never-submitted voice recording** leaves an orphan `VoiceAnswer` row (no
  `Attempt` ever gets attached to it). Harmless - nothing references it, so it can't block a
  session from completing - but the scheduled sweep doesn't clean these up yet.

## Status

**Implemented:** registration/login/email verification, user profile management, full session
lifecycle (start/complete/read-status) across all three session types with server-enforced,
per-type deadlines and automatic expiry (lazy check-on-access plus a scheduled sweep);
MCQ/coding/open-ended attempt submission with async grading and a polling endpoint; server-side
voice-answer timing plus full audio upload -> storage -> transcription -> AI grading; Real
Interview's strict in-order question flow with a one-at-a-time current-question endpoint; per-user
session history (`GET /api/sessions/me`) plus a richer per-session review (own answers, coding
test results, and the correct MCQ option once a session is no longer in progress); and a full
admin surface (user moderation, topic CRUD, question/option/coding-challenge/test-case CRUD -
including full-detail question browsing with the answer key, admin-only).

**Deliberately deferred**, see above: public topics list and a cross-session history *list*
(frontend not built yet), request validation on three controllers, `@RestControllerAdvice`, AI
prompt injection hardening (applies to both typed and transcribed voice answers), database
migrations, cloud audio storage and an audio playback endpoint.

## License

[MIT](LICENSE)
