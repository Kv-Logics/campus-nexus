# Campus Nexus

> **Enterprise High-Throughput Concurrent File Distribution Engine & Campus Intranet Mesh Relay Gateway**  
> Built with Java 21, Spring Boot 3, MongoDB Atlas, Apache Commons Net, and WebSocket Reverse Tunneling.

---

## Overview & Architecture

Campus Nexus is a production networking and distributed delivery system designed for high concurrency, fault isolation, and cross-network intranet accessibility:

1. **Producer-Consumer + Fan-Out File Distribution**:
   - Ingests files via HTTP multipart streaming upload or REST API.
   - Stages files into local disk storage while computing streaming **SHA-256 cryptographic checksums**.
   - Concurrently fans out distribution tasks to **10 independent FTP/FTPS production servers** (default port `21`) via a bounded `ExecutorService` thread pool.
   - **Fault Isolation**: Connection dropouts or timeouts on any individual FTP node never stall or interrupt transfers on the other 9 nodes.
   - **Exponential Backoff & Retries**: Automated retry scheduling (5s, 30s) alongside instant manual retry triggers.

2. **Campus Intranet Mesh Relay & Reverse Tunnel**:
   - Enables secure access to campus-only internal intranet portals (`10.x.x.x` or `.campus.internal`) from external laptops without a VPN.
   - A peer device connected to campus Wi-Fi opens Campus Nexus and authenticates as an active relay node.
   - Establishes a persistent bi-directional WebSocket reverse tunnel (`/ws/relay`).
   - Intranet requests from the host laptop are tunneled through the connected peer node and rendered live.

3. **Production Database**:
   - Powered by **MongoDB Atlas** cloud database cluster (`campus_nexus`).
   - Persists staged file metadata, transfer job states, relay node heartbeats, and FTP server configurations with zero mock data.

---

## System Architecture

```
                                CLIENT / USER
                                      │
                              Upload / Drag & Drop
                                      │
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
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │ Bounded Worker Pool │
                           │  (ExecutorService)  │
                           └──────────┬──────────┘
                                      │
          ┌──────────────┬────────────┼────────────┬──────────────┐
          │              │            │            │              │
          ▼              ▼            ▼            ▼              ▼
        FTP 01         FTP 02       FTP 03       FTP 04        FTP 10
       (Port 21)      (Port 21)    (Port 21)    (Port 21)     (Port 21)
```

### Campus Intranet Relay Workflow

```
  Host (Laptop Outside)                   Peer Device (On College Wi-Fi)
┌──────────────────────┐                 ┌────────────────────────────────┐
│  Requests Intranet   │                 │ Connected to Campus Wi-Fi      │
│  URL via Gateway     │                 │ Direct access to 10.x.x.x      │
└──────────┬───────────┘                 └───────────────┬────────────────┘
           │                                             │
           ▼                                             ▲
┌────────────────────────────────────────────────────────┴────────────────┐
│                       Campus Nexus Central Hub                          │
│               WebSocket Reverse Tunnel Relay Engine                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites
- Java 21+ (OpenJDK / Eclipse Temurin)
- Git
- MongoDB Atlas cluster (configured in `application.properties`)

### 1. Clone & Run
```bash
git clone https://github.com/Kv-Logics/campus-nexus.git
cd campus-nexus

# Windows:
.\mvnw.cmd spring-boot:run

# Linux / macOS:
./mvnw spring-boot:run
```

### 2. Access the SaaS Dashboard
Open your browser and navigate to:
```
http://localhost:8080
```

---

## Operating the 10-Node Distribution Engine

1. Open `http://localhost:8080`.
2. Navigate to the **FTP Targets** tab:
   - All 10 destination FTP servers (FTP-01 through FTP-10) default to standard port `21` and are initially set to disabled.
   - Click **Edit** on any server to specify real target hosts, ports, credentials, and remote storage paths.
   - Click **Test Connection** to verify live connectivity and credentials.
   - Toggle the server status to **Enable** when ready.
3. Switch to the **File Distribution** tab:
   - Drag and drop or browse to upload any file.
   - The engine automatically stages the file, calculates the cryptographic SHA-256 hash, and fans out parallel transfer jobs to all active FTP targets.
   - Monitor live progress, statuses (`PENDING`, `UPLOADING`, `SUCCESS`, `FAILED`), and retry counts in real-time.

---

## Using the Campus Intranet Bridge

1. Share your Campus Nexus URL with a peer connected to the campus Wi-Fi network.
2. On their mobile device browser, have them open the **Intranet Relay** tab and click:
   > **"Connect as Campus Relay"**
3. On your host laptop, enter any intranet URL (e.g. `http://10.1.2.3/student-portal`) into the **Laptop Intranet Gateway** and click **"Fetch via Peer"**.
4. The request will route through the peer's WebSocket tunnel and return the internal campus page content.

---

## REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/files/upload` | Ingests file, computes streaming SHA-256, stages, and fans out jobs |
| `GET` | `/api/files` | Lists staged files with metadata and SHA-256 checksums |
| `GET` | `/api/jobs` | Lists all transfer jobs or filtered by `?fileId={id}` |
| `POST` | `/api/jobs/{id}/retry` | Triggers an immediate manual retry for a failed job |
| `GET` | `/api/ftp/servers` | Lists all 10 production FTP server targets |
| `PUT` | `/api/ftp/servers/{id}` | Updates configuration for a specific FTP server target |
| `POST` | `/api/ftp/servers/{id}/toggle` | Enables or disables an individual FTP server |
| `POST` | `/api/ftp/servers/{id}/test` | Tests live socket connectivity and authentication for an FTP target |
| `POST` | `/api/ftp/servers/disable-all` | Disables all 10 FTP destinations at once |
| `POST` | `/api/ftp/servers/enable-all` | Enables all 10 FTP destinations at once |
| `GET` | `/api/proxy/nodes` | Lists active connected campus relay nodes |
| `GET` | `/api/proxy/fetch?url={url}` | Routes an intranet HTTP request through an active relay tunnel |

---

## Database Configuration

Campus Nexus uses MongoDB Atlas:
- `files`: Staged file records, metadata, and cryptographic SHA-256 checksums.
- `ftp_servers`: 10 production FTP destination configurations (host, port 21, credentials, remote directory, status).
- `transfer_jobs`: Transfer states (`PENDING`, `UPLOADING`, `SUCCESS`, `FAILED`), attempt tracking, and error logs.
- `relay_nodes`: Real-time session registry for connected peer devices on campus Wi-Fi.

Configure your MongoDB URI in `src/main/resources/application.properties`:
```properties
spring.data.mongodb.uri=mongodb+srv://<username>:<password>@cluster.mongodb.net/campus_nexus?retryWrites=true&w=majority
```

---

## License
MIT License • Copyright (c) 2026 Kv-Logics
