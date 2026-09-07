# ⚡ Campus Nexus

> **High-Throughput Concurrent File Distribution Engine & Campus Intranet Mesh Relay Hub**  
> Built with Java 21, Spring Boot 3, Apache Commons Net, and WebSocket Reverse Tunneling.

---

## 🌟 Overview & Capabilities

Campus Nexus is a dual-capability networking and distribution system engineered for high concurrency, fault isolation, and cross-network accessibility:

1. **Producer–Consumer + Fan-Out File Distribution**:
   - Ingests files via HTTP multipart upload or REST API.
   - Stages files into local disk storage with streaming **SHA-256 cryptographic checksum** calculation.
   - Fans out distribution jobs to **10 independent FTP/FTPS servers** simultaneously using a bounded `ExecutorService` thread pool.
   - **Fault Isolation**: A failure or timeout on one FTP server (e.g. FTP-04) never interrupts or slows down the other 9 servers.
   - **Resilience & Exponential Backoff**: Automatic retry scheduling (5s, 30s) + interactive manual retry controls.

2. **Campus Intranet Mesh Relay & Reverse Tunnel**:
   - Solves the problem of accessing campus-only internal intranet portals (attendance, gradebooks, internal file servers) from outside without a university VPN.
   - A friend on campus Wi-Fi opens Campus Nexus on their smartphone and taps **"Connect as Campus Relay"**.
   - A persistent WebSocket reverse tunnel is established.
   - The host on their laptop can request any internal intranet URL (`http://10.x.x.x` or `http://*.campus.internal`) through the peer's connection.

3. **Zero-Configuration Embedded 10-Node Mock FTP Cluster**:
   - Comes pre-configured with 10 in-memory Mock FTP servers running on ports `2121` through `2130`.
   - Allows instant testing on any laptop out of the box with zero external FTP server installation!
   - Supports simulating server crashes and network drops with one click in the web UI.

---

## 🏗️ System Architecture

```
                                CLIENT / USER
                                     │
                             Upload / Drag & Drop
                                     ▼
                          ┌─────────────────────┐
                          │   File Receiver     │
                          │   (Spring Boot)     │
                          └──────────┬──────────┘
                                     │
                                     ▼
                          ┌─────────────────────┐
                          │    Local Staging    │
                          │   (SHA-256 Digest)  │
                          └──────────┬──────────┘
                                     │
                                     ▼
                          ┌─────────────────────┐
                          │ Distribution Manager│
                          └──────────┬──────────┘
                                     │
                         Creates 10 Independent Jobs
                                     ▼
                          ┌─────────────────────┐
                          │ Bounded Worker Pool │
                          │  (ExecutorService)  │
                          └──────────┬──────────┘
                                     │
         ┌──────────────┬────────────┼────────────┬──────────────┐
         ▼              ▼            ▼            ▼              ▼
       FTP 01         FTP 02       FTP 03       FTP 04        FTP 10
      (:2121)        (:2122)      (:2123)      (:2124)       (:2130)
```

### Campus Intranet Relay Architecture

```
  Host (Laptop Outside)                   Friend's Phone (On College Wi-Fi)
┌──────────────────────┐                 ┌────────────────────────────────┐
│  • Requests Intranet │                 │ • Connected to Campus Wi-Fi    │
│    URL via Dashboard │                 │ • Has access to 10.x.x.x       │
└──────────┬───────────┘                 └───────────────┬────────────────┘
           │                                             │
           ▼                                             ▲
┌────────────────────────────────────────────────────────┴────────────────┐
│                       Campus Nexus Central Hub                          │
│               WebSocket Reverse Tunnel Relay Engine                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- Java 21+ (OpenJDK / Temurin / Adoptium)
- Git

### 1. Clone & Run
```bash
git clone https://github.com/Kv-Logics/campus-nexus.git
cd campus-nexus

# On Windows:
.\mvnw.cmd spring-boot:run

# On Linux / macOS:
./mvnw spring-boot:run
```

### 2. Access the Web Dashboard
Open your browser and navigate to:
```
http://localhost:8080
```

---

## 🧪 Testing the 10-Node Fan-Out Pipeline

1. Open `http://localhost:8080`.
2. Drag and drop any file (e.g. PDF, ZIP, TXT) into the ingestion box.
3. Watch all 10 mock FTP targets switch from **`PENDING`** to **`UPLOADING`** and finally to **`SUCCESS`** concurrently!
4. **Simulate a Failure**:
   - Go to the **FTP Targets & Cluster** tab.
   - Click **"🛑 Stop (Simulate Drop)"** on port **2124 (Mock FTP-04)**.
   - Upload another file in the **File Distribution** tab.
   - Notice: FTP-01..03 and FTP-05..10 succeed immediately, while FTP-04 shows **`FAILED`** with the exact error details.
   - Click **"▶️ Restart Server"** on port 2124, then click **"⟳ Retry"** on the failed job card to verify automated recovery!

---

## 📡 Using the Campus Intranet Bridge

1. Share your server URL with your friend connected to the college Wi-Fi.
2. On their smartphone browser, have them open the **Campus Intranet Bridge** tab and tap:
   > **"Connect as Campus Relay"**
3. On your laptop, enter any intranet URL (e.g. `http://10.1.2.3/student-portal`) in the **Laptop Intranet Gateway** form and click **"Fetch via Peer"**.
4. The request will route through their device and display the internal campus site on your laptop!

---

## 🔌 REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/files/upload` | Ingests multipart file, computes SHA-256, stages, and fans out 10 jobs |
| `GET` | `/api/files` | Lists all staged files with sizes and checksums |
| `GET` | `/api/jobs` | Lists all transfer jobs or filtered by `?fileId={id}` |
| `POST` | `/api/jobs/{id}/retry` | Triggers immediate manual retry for a failed job |
| `GET` | `/api/ftp/servers` | Lists all configured FTP server targets |
| `POST` | `/api/ftp/servers/{id}/test` | Tests connectivity and credentials for an FTP server |
| `GET` | `/api/ftp/mock-cluster` | Returns status of the 10 embedded mock FTP ports |
| `POST` | `/api/ftp/mock-cluster/{port}/toggle` | Toggles a mock FTP node on/off to simulate network failure |
| `GET` | `/api/proxy/nodes` | Lists active connected campus relay nodes |
| `GET` | `/api/proxy/fetch?url={url}` | Routes an intranet HTTP request through an active relay tunnel |

---

## 🗄️ Database Schema

Campus Nexus uses an embedded H2 database (persisted to `./data/campusnexus.mv.db`):
- `FILES`: Primary file metadata, staged paths, and SHA-256 checksums.
- `FTP_SERVERS`: 10 FTP target configurations (host, port, credentials, remote directory).
- `TRANSFER_JOBS`: Independent job state (`PENDING`, `UPLOADING`, `SUCCESS`, `FAILED`), retry attempts, start/completion timestamps, and error messages.
- `RELAY_NODES`: Real-time registry of connected peer devices on campus Wi-Fi.

Access the H2 Console at `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:file:./data/campusnexus`.

---

## 📜 License
MIT License • Copyright (c) 2026 Kv-Logics
