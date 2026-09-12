# Campus Nexus

> **Enterprise High-Throughput Concurrent File Distribution Engine & Campus Intranet Mesh Relay Gateway**  
> Built with Java 21, Spring Boot 3, MongoDB Atlas, Apache Commons Net, Docker, and WebSocket Reverse Tunneling.

---

## About

**Campus Nexus** is a dual-capability distributed networking platform engineered to solve two fundamental challenges in modern academic and enterprise infrastructure:

1. **High-Throughput Fan-Out File Distribution (1-to-10 Parallel Delivery)**:  
   Institutions and lab environments frequently need to distribute large archives, software packages, or examination datasets to multiple remote servers simultaneously. Traditional sequential transfers are slow and vulnerable to single-point failures. Campus Nexus streams incoming uploads into local staging storage, computes a real-time cryptographic **SHA-256 checksum**, and concurrently fans out the payload across **10 independent production FTP/FTPS endpoints** on standard port `21`. Transfers run inside a bounded thread pool with complete fault isolation—if one destination drops or throttles, the remaining 9 nodes proceed at line speed.

2. **Zero-VPN Campus Intranet Mesh Gateway (Reverse WebSocket Tunnel)**:  
   University portals, grade databases, and lab resources are frequently locked behind campus network firewalls (`10.x.x.x` or `.internal` subnets), inaccessible to students or faculty working remotely without clunky VPN clients. Campus Nexus provides a browser-based reverse tunnel: a trusted peer connected to campus Wi-Fi (e.g., a student's mobile phone) opens a lightweight, isolated relay page (`relay.html`). The peer establishes a persistent, bi-directional WebSocket reverse tunnel back to the gateway. External administrators can then query firewalled intranet resources on demand, routed seamlessly through the peer's browser.

3. **Cost-Optimized Cloud Architecture**:  
   Includes a ready-to-deploy serverless **Vercel Power Switch** (`ec2-controller`) that communicates directly with AWS EC2. Administrators can power up their AWS instance on-demand with one tap and power it down when done, slashing idle compute costs to **$0.00/hour**.

---

## Key Features

- **Concurrent Fan-Out Distribution**: Parallel transfers to 10 independent FTP/FTPS production targets.
- **Cryptographic File Integrity**: On-the-fly streaming SHA-256 digest computation during local staging.
- **Preflight Target Health Probing**: Target connectivity verification before triggering large fan-out payloads.
- **Staging Lifecycle Management**: Full control to inspect, download (retrieve), or delete staged files on demand.
- **Fault Isolation & Exponential Backoff**: Independent worker retry policies (5s, 30s) without cascading stalls.
- **Isolated Peer Relay Node Portal**: Clean, standalone mobile page (`relay.html`) featuring QR code scan and 1-click WhatsApp invite links.
- **Zero Mock Data & Masked Credentials**: Pure production defaults; saved passwords are write-only and never exposed in REST responses.
- **Containerized & Cloud-Ready**: Multi-stage Alpine JRE 21 `Dockerfile`, `docker-compose.yml`, and `/api/health` monitoring probe.
- **On-Demand EC2 Cloud Controller**: Serverless Next.js/Vercel micro-service with PIN authentication to toggle AWS instances on/off.

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
- Git & Docker (optional for containerized run)
- MongoDB Atlas cluster (free tier or dedicated)

### 1. Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Set your MongoDB Atlas connection string and staging preferences:
```env
SPRING_DATA_MONGODB_URI=mongodb+srv://<username>:<password>@cluster.mongodb.net/campus_nexus?retryWrites=true&w=majority
APP_STORAGE_STAGING_DIR=./staging
APP_STORAGE_WORKER_THREADS=10
```

### 2. Run Locally
```bash
# Windows:
.\mvnw.cmd spring-boot:run

# Linux / macOS:
./mvnw spring-boot:run
```

Access the host dashboard at `http://localhost:8080`.

### 3. Run with Docker Compose
```bash
docker compose up -d --build
```

---

## Operating the System

### 1. 10-Node File Distribution
1. Navigate to the **FTP Targets** tab:
   - Configure hosts, credentials, and remote storage paths (e.g. `ftp.amritanet.edu` is suggested by default).
   - Use **Check Readiness** or **Test Connection** to probe port 21 socket availability.
   - Toggle targets **Enabled**.
2. Switch to the **File Distribution** tab:
   - Upload any file (drag-and-drop up to 500MB).
   - Once staged with SHA-256, pick target FTP nodes from the modal and click **Send**.
   - Monitor real-time progress (`PENDING`, `UPLOADING`, `SUCCESS`, `FAILED`) and retry failed transfers with 1 click.
   - Staged files can be retrieved (downloaded) or deleted from disk storage at any time.

### 2. Campus Intranet Reverse Bridge
1. Open the **Intranet Relay** tab on the host dashboard.
2. Share the generated link or QR code with a peer on campus Wi-Fi (`/relay.html`).
3. On the peer's phone/laptop, they tap **Connect as Campus Relay**.
4. On your host dashboard, enter the desired intranet URL (e.g. `http://10.1.2.3/portal`) and click **Fetch via Peer**.

---

## Serverless AWS EC2 Controller (`ec2-controller`)

To keep hosting costs near zero, deploy the companion micro-service in `ec2-controller/` to **Vercel**:
1. Import `Kv-Logics/campus-nexus` into Vercel, setting root directory to `ec2-controller`.
2. Add environment variables:
   - `MY_AWS_REGION`: e.g. `ap-south-1`
   - `MY_AWS_ACCESS_KEY`: IAM access key with EC2 permissions
   - `MY_AWS_SECRET_KEY`: IAM secret key
   - `MY_EC2_INSTANCE_ID`: Your EC2 instance ID
   - `ADMIN_SECRET_KEY`: Your personal security PIN
3. Use the glassmorphic mobile web interface to click **Power ON** before distributions and **Power OFF** when finished.

---

## REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | System health probe (DB connectivity, active relay count, disk space) |
| `POST` | `/api/files/upload` | Ingests multipart file, computes SHA-256 digest, and stages locally |
| `GET` | `/api/files` | Lists all staged files and metadata |
| `GET` | `/api/files/staging/{filename}` | Downloads/retrieves a staged file from disk storage |
| `DELETE` | `/api/files/staging/{filename}` | Deletes a staged file and removes its database record |
| `POST` | `/api/files/{id}/distribute` | Fans out staged file to selected active FTP targets |
| `GET` | `/api/jobs` | Lists transfer jobs with live statuses (`?fileId={id}`) |
| `POST` | `/api/jobs/{id}/retry` | Triggers an immediate retry for a failed transfer job |
| `GET` | `/api/ftp/servers` | Lists configured FTP servers (passwords masked) |
| `PUT` | `/api/ftp/servers/{id}` | Updates configuration for an FTP target |
| `POST` | `/api/ftp/servers/{id}/toggle` | Enables or disables an individual FTP server |
| `POST` | `/api/ftp/servers/{id}/test` | Tests live socket connectivity and authentication |
| `GET` | `/api/proxy/nodes` | Lists active connected campus relay nodes |
| `GET` | `/api/proxy/fetch?url={url}` | Routes an intranet HTTP request through an active relay tunnel |

---

## Database Configuration

Campus Nexus persists all operational state in **MongoDB Atlas**:
- `files`: Staged file records, file sizes, timestamps, and SHA-256 checksums.
- `ftp_servers`: 10 production FTP destination profiles (port 21, credentials, remote folders, statuses).
- `transfer_jobs`: Transfer states, timestamps, retry attempt counts, and socket error messages.
- `relay_nodes`: Active session registry and heartbeats for connected campus peers.

---

## License
MIT License • Copyright (c) 2026 Kv-Logics
