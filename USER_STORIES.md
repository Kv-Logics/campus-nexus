# Campus Nexus — Software Requirements & User Stories Specification

This document defines the software engineering requirements, user personas, epics, and detailed user stories following the **INVEST** principle (Independent, Negotiable, Valuable, Estimable, Small, Testable) with **Gherkin (Given-When-Then)** Acceptance Criteria.

---

## 1. Personas

| Persona | Role | Context & Goals |
|---|---|---|
| **Alex (Distributor / Host)** | System Administrator / Host User | Operates the laptop/server. Wants to upload single large files once and have them concurrently distributed across 10 distinct FTP servers with real-time status and failure isolation. Also wants to access campus intranet resources from home/outside through peers. |
| **Sam (Campus Peer / Relay)** | Student on College Wi-Fi | Has physical/Wi-Fi access inside the campus. Opens the web app on their phone or laptop, clicks "Connect Relay", and provides an authenticated tunnel for Alex to reach intranet portals. |
| **DevOps / QA Engineer** | Verification & Maintenance | Requires automated mock clusters, deterministic retry policies, and clear database logs to verify and audit file integrity and network throughput. |

---

## 2. Epics Overview

- **EPIC-1: Staged File Ingestion & Cryptographic Integrity**
- **EPIC-2: Producer–Consumer Concurrent Fan-Out Distribution Engine**
- **EPIC-3: Resilience, Fault Isolation & Exponential Backoff Retry**
- **EPIC-4: Campus Intranet WebSocket Relay & Reverse Tunnel**
- **EPIC-5: Interactive Multi-Device Dashboard & Mobile Node Portal**
- **EPIC-6: Embedded Multi-Tenant Mock FTP Cluster for Automated Testing**

---

## 3. Detailed User Stories & Acceptance Criteria

### EPIC-1: Staged File Ingestion & Cryptographic Integrity

#### US-1.1: Local Staging with Non-Blocking Streaming
> **As a** file distribution server,  
> **I want to** save incoming files into a local staging storage (`./staging/`) before attempting any network transfer,  
> **So that** transient uploads are safely persisted and FTP failures can be retried without re-uploading from the client.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Successful File Staging**
  - **Given** an incoming multipart file upload `exam-results.pdf` of size 25MB
  - **When** the file is received by the File Ingestion Service
  - **Then** it should be saved into the configured local staging directory with a sanitized, collision-free filename
  - **And** the file size and metadata should match the original payload exactly.
- **Scenario 2: Storage Failure Handling**
  - **Given** the local staging disk is write-protected or full
  - **When** an upload is attempted
  - **Then** the service must return an HTTP 507 (Insufficient Storage) or 500 with a descriptive error
  - **And** no orphaned records should be committed to the database.

---

#### US-1.2: Cryptographic Checksum Computation (SHA-256)
> **As a** system administrator,  
> **I want** the system to compute a SHA-256 checksum during staging,  
> **So that** I have a cryptographic guarantee of file integrity throughout distribution and audits.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: SHA-256 Generation on Ingestion**
  - **Given** a file `syllabus.docx` is streamed to the staging service
  - **When** the file write completes
  - **Then** the SHA-256 hash must be calculated in the same stream pass
  - **And** stored in the `FILES` table alongside filename, size, and timestamp.

---

### EPIC-2: Producer–Consumer Concurrent Fan-Out Distribution Engine

#### US-2.1: Fan-Out Job Creation
> **As a** distribution manager,  
> **I want** each staged file to automatically generate 1 `TransferJob` for every active FTP server,  
> **So that** transfers to different destinations are tracked as independent stateful entities.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: 10 FTP Servers Fan-Out**
  - **Given** 10 FTP servers configured and enabled in the `FTP_SERVERS` table
  - **When** a new file `lecture10.mp4` is staged
  - **Then** exactly 10 distinct `TransferJob` records must be created with status `PENDING`
  - **And** all 10 jobs must be linked to the parent `file_id`.

---

#### US-2.2: Bounded Concurrent Thread Pool Execution
> **As a** system architect,  
> **I want** transfer jobs to be dispatched to a bounded `ExecutorService` worker pool,  
> **So that** 10 FTP transfers run simultaneously without unbounded thread exhaustion.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Parallel Upload Execution**
  - **Given** 10 `TransferJob`s queued for execution
  - **When** the bounded worker pool picks up the tasks
  - **Then** all 10 jobs transition to `UPLOADING` concurrently
  - **And** upload streams execute in parallel across independent FTP sockets.

---

### EPIC-3: Resilience, Fault Isolation & Exponential Backoff Retry

#### US-3.1: Isolated Fault Domain
> **As a** system user,  
> **I want** a failure on one FTP server (e.g. FTP-4 timeout) not to abort or interrupt the other 9 transfers,  
> **So that** network instability is isolated to the faulty endpoint.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Single Node Network Failure**
  - **Given** 10 concurrent uploads running
  - **When** FTP Server #4 drops the TCP connection or times out
  - **Then** FTP-4's `TransferJob` transitions to `FAILED` with the exact error message recorded
  - **And** FTP-1, 2, 3, and 5 through 10 continue uninterrupted and transition to `SUCCESS`.

---

#### US-3.2: Automated Exponential Backoff Retry
> **As an** operator,  
> **I want** failed transfers to automatically retry with exponential backoff (e.g. 5s, 30s) up to a max attempt count,  
> **So that** temporary network glitches recover without human intervention.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Exponential Delay Progression**
  - **Given** a `TransferJob` failed on attempt 1
  - **When** the Retry Service schedules a retry
  - **Then** attempt 2 is executed after a 5-second backoff
  - **And** if attempt 2 fails, attempt 3 is executed after a 30-second backoff
  - **And** if attempt 3 fails, the job transitions to terminal `FAILED`.

---

#### US-3.3: Manual On-Demand Retry Trigger
> **As an** administrator viewing the dashboard,  
> **I want** a "Retry" button next to any failed FTP job,  
> **So that** I can re-trigger transfer after fixing credentials or server downtime.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Manual Retry of Failed Node**
  - **Given** a `TransferJob` in `FAILED` status
  - **When** the user clicks "Retry" on the web dashboard
  - **Then** the job status resets to `PENDING` -> `UPLOADING`
  - **And** a new worker task is submitted to the thread pool immediately.

---

### EPIC-4: Campus Intranet WebSocket Relay & Reverse Tunnel

#### US-4.1: Peer Node Registration via Mobile
> **As a** campus peer (Sam) connected to college Wi-Fi,  
> **I want to** open the web app on my phone and tap "Connect Relay Node",  
> **So that** my phone registers as an active proxy bridge with the server.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Mobile Relay Connection**
  - **Given** Sam opens the Campus Nexus web app on a smartphone connected to campus Wi-Fi
  - **When** Sam toggles "Connect Relay"
  - **Then** a persistent WebSocket connection is established with `/ws/relay`
  - **And** a `RelayNode` entry is created with status `ONLINE`, phone device model, and ping latency.

---

#### US-4.2: Intranet Reverse Tunnel Proxying
> **As a** host user (Alex) on a laptop,  
> **I want to** request internal campus intranet URLs through an active peer relay,  
> **So that** I can browse campus-only portals from outside without a university VPN.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Intranet Web Request Relaying**
  - **Given** an active relay node registered on campus Wi-Fi
  - **When** Alex requests `GET /api/proxy/fetch?url=http://intranet.campus.local`
  - **Then** the server dispatches a request packet over the WebSocket to Sam's phone
  - **And** Sam's phone fetches the internal site through its campus Wi-Fi connection
  - **And** the HTML/assets are streamed back through the WebSocket and served to Alex's browser.

---

### EPIC-5: Interactive Multi-Device Dashboard & Mobile Node Portal

#### US-5.1: Real-Time Job Progress Matrix
> **As a** user,  
> **I want** a unified matrix showing all files and their 10 FTP destination statuses in real-time,  
> **So that** I have full visibility of the fan-out distribution pipeline.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Real-Time Visual Feedback**
  - **Given** an active file upload
  - **When** transfers progress from `PENDING` to `UPLOADING` to `SUCCESS`
  - **Then** the dashboard displays color-coded badges (Blue = Pending, Orange = Uploading, Green = Success, Red = Failed)
  - **And** updates in real-time without requiring a manual browser refresh.

---

### EPIC-6: Embedded Multi-Tenant Mock FTP Cluster

#### US-6.1: Zero-Config Local Testing
> **As a** developer or tester,  
> **I want** an embedded 10-node mock FTP server cluster accessible via `localhost:2121..2130`,  
> **So that** the entire 10-server fan-out system can be tested immediately on any machine without installing external FTP software.

**Acceptance Criteria (Gherkin):**
- **Scenario 1: Embedded Mock Cluster Initialization**
  - **Given** the application starts up
  - **When** `MockFtpClusterService` initializes
  - **Then** 10 in-memory FTP servers bind to ports 2121 through 2130
  - **And** the default `FTP_SERVERS` seed data points to these 10 mock nodes.

---

## 4. Definition of Done (DoD)
1. Code builds cleanly with `./mvnw clean compile`.
2. Automated integration tests pass.
3. Database entities and relations are persisted and queryable.
4. Concurrency limits are strictly enforced (bounded worker pool).
5. All Git stages are committed with semantic commit messages.
