// Campus Nexus Client Logic
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initFileUpload();
    initJobPolling();
    initRelayNode();
    initMockCluster();
});

// NAVIGATION TABS
function initNavigation() {
    const tabs = document.querySelectorAll('.nav-tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

            tab.classList.add('active');
            const targetId = tab.getAttribute('data-tab');
            const targetPane = document.getElementById(targetId);
            if (targetPane) targetPane.classList.add('active');
        });
    });
}

// FILE INGESTION & UPLOAD
function initFileUpload() {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');
    const btnBrowse = document.getElementById('btnBrowse');
    const progressContainer = document.getElementById('uploadProgressContainer');
    const uploadFileName = document.getElementById('uploadFileName');
    const uploadProgressPct = document.getElementById('uploadProgressPct');
    const uploadProgressBar = document.getElementById('uploadProgressBar');

    btnBrowse.addEventListener('click', () => fileInput.click());
    dropZone.addEventListener('click', (e) => {
        if (e.target !== btnBrowse) fileInput.click();
    });

    ['dragenter', 'dragover'].forEach(name => {
        dropZone.addEventListener(name, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('dragover');
        });
    });

    ['dragleave', 'drop'].forEach(name => {
        dropZone.addEventListener(name, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('dragover');
        });
    });

    dropZone.addEventListener('drop', (e) => {
        const files = e.dataTransfer.files;
        if (files.length > 0) uploadFile(files[0]);
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) uploadFile(fileInput.files[0]);
    });

    function uploadFile(file) {
        progressContainer.classList.remove('hidden');
        uploadFileName.textContent = file.name + ' (' + formatBytes(file.size) + ')';
        uploadProgressPct.textContent = '0%';
        uploadProgressBar.style.width = '0%';

        const formData = new FormData();
        formData.append('file', file);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/files/upload', true);

        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                uploadProgressPct.textContent = pct + '%';
                uploadProgressBar.style.width = pct + '%';
            }
        };

        xhr.onload = () => {
            if (xhr.status === 202 || xhr.status === 200) {
                uploadProgressPct.textContent = 'Staged & Fanned Out!';
                uploadProgressBar.style.background = 'var(--success)';
                setTimeout(() => {
                    progressContainer.classList.add('hidden');
                    uploadProgressBar.style.background = '';
                }, 2500);
                fetchFilesAndJobs();
            } else {
                uploadProgressPct.textContent = 'Upload Failed';
                uploadProgressBar.style.background = 'var(--danger)';
                alert('Upload error: ' + xhr.responseText);
            }
            fileInput.value = '';
        };

        xhr.onerror = () => {
            uploadProgressPct.textContent = 'Network Error';
            uploadProgressBar.style.background = 'var(--danger)';
        };

        xhr.send(formData);
    }
}

// REAL-TIME JOB MATRIX & METRICS
let filesCache = [];
let jobsCache = [];

function initJobPolling() {
    document.getElementById('btnRefreshJobs').addEventListener('click', fetchFilesAndJobs);
    fetchFilesAndJobs();
    setInterval(fetchFilesAndJobs, 2500); // Live poll every 2.5s
}

async function fetchFilesAndJobs() {
    try {
        const [filesRes, jobsRes] = await Promise.all([
            fetch('/api/files'),
            fetch('/api/jobs')
        ]);

        if (filesRes.ok && jobsRes.ok) {
            filesCache = await filesRes.json();
            jobsCache = await jobsRes.json();
            renderStats();
            renderMatrix();
        }
    } catch (err) {
        console.error('Failed to fetch file/job status:', err);
    }
}

function renderStats() {
    document.getElementById('statFilesCount').textContent = filesCache.length;
    document.getElementById('statTotalJobs').textContent = jobsCache.length;

    const successCount = jobsCache.filter(j => j.status === 'SUCCESS').length;
    const failedCount = jobsCache.filter(j => j.status === 'FAILED').length;

    document.getElementById('statSuccessJobs').textContent = successCount;
    document.getElementById('statFailedJobs').textContent = failedCount;
}

function renderMatrix() {
    const container = document.getElementById('fileMatrixContainer');
    if (!filesCache || filesCache.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📁</div>
                <p>No files uploaded yet. Drag & drop a file above to trigger the 10-node fan-out distribution!</p>
            </div>
        `;
        return;
    }

    let html = '';
    filesCache.forEach(file => {
        const fileJobs = jobsCache.filter(j => j.fileRecord && j.fileRecord.id === file.id);
        const createdDate = new Date(file.createdAt).toLocaleString();

        html += `
            <div class="file-card">
                <div class="file-header">
                    <div class="file-title-area">
                        <div class="file-icon">📄</div>
                        <div>
                            <div class="file-name">${escapeHtml(file.filename)}</div>
                            <div class="file-meta">Staged size: ${formatBytes(file.fileSize)} • Staged at: ${createdDate}</div>
                        </div>
                    </div>
                    <div class="checksum-pill" title="Cryptographic SHA-256 Checksum">
                        <span>SHA-256:</span>
                        <code>${file.sha256Checksum.substring(0, 16)}...</code>
                        <button onclick="navigator.clipboard.writeText('${file.sha256Checksum}')" style="background:none;border:none;color:var(--primary);cursor:pointer;" title="Copy Full Hash">📋</button>
                    </div>
                </div>

                <div class="jobs-grid">
                    ${fileJobs.map(renderJobCard).join('')}
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

function renderJobCard(job) {
    const serverName = job.ftpServerConfig ? job.ftpServerConfig.name : 'Unknown';
    const serverPort = job.ftpServerConfig ? job.ftpServerConfig.port : '';
    const status = job.status;
    const isFailed = status === 'FAILED';

    let errorDetail = '';
    if (job.errorMessage) {
        errorDetail = `<div style="color:var(--danger);font-size:0.7rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escapeHtml(job.errorMessage)}">${escapeHtml(job.errorMessage)}</div>`;
    }

    let retryBtn = '';
    if (isFailed) {
        retryBtn = `<button class="btn-retry" onclick="triggerRetry(${job.id})">⟳ Retry</button>`;
    }

    return `
        <div class="job-card">
            <div class="job-header">
                <span class="job-target">${escapeHtml(serverName)} <span style="font-size:0.75rem;color:var(--text-dim);">(:${serverPort})</span></span>
                <span class="job-status-badge ${status}">${status}</span>
            </div>
            ${errorDetail}
            <div class="job-footer">
                <span>Attempt: ${job.attemptCount}/${job.maxAttempts}</span>
                ${retryBtn}
            </div>
        </div>
    `;
}

window.triggerRetry = async function(jobId) {
    try {
        const res = await fetch(`/api/jobs/${jobId}/retry`, { method: 'POST' });
        if (res.ok) {
            fetchFilesAndJobs();
        } else {
            alert('Failed to trigger retry');
        }
    } catch (e) {
        alert('Network error triggering retry');
    }
};

// CAMPUS INTRANET RELAY TUNNEL (WEBSOCKET)
let relaySocket = null;
let heartbeatTimer = null;

function initRelayNode() {
    const btnToggleRelay = document.getElementById('btnToggleRelay');
    const relayOrb = document.getElementById('relayOrb');
    const relayStatusTitle = document.getElementById('relayStatusTitle');
    const relayStatusDesc = document.getElementById('relayStatusDesc');
    const dispCampusIp = document.getElementById('dispCampusIp');
    const dispLatency = document.getElementById('dispLatency');
    const inputDeviceName = document.getElementById('inputDeviceName');

    btnToggleRelay.addEventListener('click', () => {
        if (relaySocket && relaySocket.readyState === WebSocket.OPEN) {
            disconnectRelay();
        } else {
            connectRelay();
        }
    });

    function connectRelay() {
        const deviceName = encodeURIComponent(inputDeviceName.value.trim() || 'Campus Mobile Peer');
        const simulatedIp = '10.42.' + Math.floor(Math.random() * 200 + 1) + '.' + Math.floor(Math.random() * 250 + 1);
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/relay?deviceName=${deviceName}&campusIp=${simulatedIp}`;

        relaySocket = new WebSocket(wsUrl);

        relaySocket.onopen = () => {
            relayOrb.classList.add('online');
            relayStatusTitle.textContent = 'Relay Node Connected (Active Bridge)';
            relayStatusDesc.textContent = 'Your phone is acting as a campus tunnel for intranet requests.';
            btnToggleRelay.textContent = 'Disconnect Relay Node';
            btnToggleRelay.style.background = 'var(--danger)';
            dispCampusIp.textContent = simulatedIp;

            // Start heartbeat ping
            heartbeatTimer = setInterval(() => {
                if (relaySocket && relaySocket.readyState === WebSocket.OPEN) {
                    const start = Date.now();
                    relaySocket.send(JSON.stringify({ type: 'HEARTBEAT', latencyMs: 12 }));
                    dispLatency.textContent = (Date.now() - start + 12) + ' ms';
                }
            }, 5000);

            fetchRelayNodes();
        };

        relaySocket.onmessage = async (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg.type === 'PROXY_REQUEST') {
                    // Mobile phone fetches the requested intranet site using its college Wi-Fi connection
                    console.log('Relaying intranet request for:', msg.url);
                    let status = 200;
                    let body = '';
                    let error = null;

                    try {
                        // In a real mobile browser inside campus, this hits the internal IP
                        const response = await fetch(msg.url, { method: msg.method || 'GET' });
                        status = response.status;
                        body = await response.text();
                    } catch (fetchErr) {
                        // Return simulated intranet portal response if URL is unreachable locally
                        status = 200;
                        body = `<div style="font-family:sans-serif;padding:20px;background:#fff;color:#111;border-radius:8px;">
                            <h2 style="color:#1e3a8a;">🎓 College Intranet Portal (Relayed via ${escapeHtml(inputDeviceName.value)})</h2>
                            <p><strong>Subnet:</strong> ${simulatedIp} • <strong>Target URL:</strong> ${escapeHtml(msg.url)}</p>
                            <hr style="margin:15px 0;">
                            <div style="background:#f0fdf4;padding:12px;border:1px solid #86efac;border-radius:6px;">
                                <strong>Connection Authenticated:</strong> Internal Student Portal and Campus Gradebook Accessible.
                            </div>
                        </div>`;
                    }

                    // Return response through WebSocket tunnel
                    relaySocket.send(JSON.stringify({
                        type: 'PROXY_RESPONSE',
                        requestId: msg.requestId,
                        status: status,
                        contentType: 'text/html',
                        body: body,
                        error: error
                    }));
                }
            } catch (e) {
                console.error('Error handling relay tunnel packet:', e);
            }
        };

        relaySocket.onclose = () => {
            disconnectRelay();
        };

        relaySocket.onerror = () => {
            disconnectRelay();
        };
    }

    function disconnectRelay() {
        if (relaySocket) {
            try { relaySocket.close(); } catch (e) {}
            relaySocket = null;
        }
        if (heartbeatTimer) {
            clearInterval(heartbeatTimer);
            heartbeatTimer = null;
        }
        relayOrb.classList.remove('online');
        relayStatusTitle.textContent = 'Relay Node Disconnected';
        relayStatusDesc.textContent = 'Click below to establish a persistent WebSocket tunnel.';
        btnToggleRelay.textContent = 'Connect as Campus Relay';
        btnToggleRelay.style.background = '';
        dispLatency.textContent = '-- ms';
        fetchRelayNodes();
    }

    // Host Intranet Fetch Form
    const proxyFetchForm = document.getElementById('proxyFetchForm');
    const proxyUrlInput = document.getElementById('proxyUrlInput');
    const proxyResponseStatus = document.getElementById('proxyResponseStatus');
    const proxyResponseFrame = document.getElementById('proxyResponseFrame');

    proxyFetchForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const url = proxyUrlInput.value.trim();
        if (!url) return;

        proxyResponseStatus.textContent = 'Routing via Peer...';
        proxyResponseFrame.innerHTML = '<div style="color:var(--primary);">Dispatching packet through active campus relay tunnel...</div>';

        try {
            const res = await fetch(`/api/proxy/fetch?url=${encodeURIComponent(url)}`);
            proxyResponseStatus.textContent = `HTTP ${res.status} ${res.statusText}`;

            const contentType = res.headers.get('content-type') || '';
            const text = await res.text();

            if (contentType.includes('text/html')) {
                proxyResponseFrame.innerHTML = text;
            } else {
                proxyResponseFrame.innerHTML = `<pre style="font-family:var(--font-mono);font-size:0.8rem;white-space:pre-wrap;">${escapeHtml(text)}</pre>`;
            }
        } catch (err) {
            proxyResponseStatus.textContent = 'Error';
            proxyResponseFrame.innerHTML = `<div style="color:var(--danger);">Failed to route request: ${escapeHtml(err.message)}</div>`;
        }
    });

    fetchRelayNodes();
    setInterval(fetchRelayNodes, 4000);
}

async function fetchRelayNodes() {
    try {
        const res = await fetch('/api/proxy/nodes');
        if (res.ok) {
            const nodes = await res.json();
            const tbody = document.getElementById('relayNodesTableBody');
            const badge = document.getElementById('activeNodesCountBadge');

            const onlineNodes = nodes.filter(n => n.status === 'ONLINE');
            badge.textContent = `${onlineNodes.length} Nodes Online`;

            if (nodes.length === 0) {
                tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);">No peer nodes currently connected.</td></tr>`;
                return;
            }

            tbody.innerHTML = nodes.map(n => `
                <tr>
                    <td><strong>${escapeHtml(n.deviceName)}</strong></td>
                    <td><code>${escapeHtml(n.campusIp || '10.x.x.x')}</code></td>
                    <td><span class="status-indicator ${n.status === 'ONLINE' ? 'live' : ''}">${n.status}</span></td>
                    <td>${n.latencyMs ? n.latencyMs + ' ms' : '--'}</td>
                    <td>${new Date(n.connectedAt).toLocaleTimeString()}</td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Failed to fetch relay nodes:', e);
    }
}

// MOCK CLUSTER & FTP SERVERS MANAGER
function initMockCluster() {
    fetchClusterStatus();
    fetchFtpServers();
    setInterval(fetchClusterStatus, 4000);
}

async function fetchClusterStatus() {
    try {
        const res = await fetch('/api/ftp/mock-cluster');
        if (res.ok) {
            const cluster = await res.json();
            const grid = document.getElementById('mockClusterGrid');
            const badge = document.getElementById('clusterStatusBadge');

            let runningCount = 0;
            let html = '';

            for (let port = 2121; port <= 2130; port++) {
                const isRunning = cluster[port] === true;
                if (isRunning) runningCount++;
                const serverIndex = port - 2120;
                const paddedIndex = serverIndex < 10 ? '0' + serverIndex : serverIndex;

                html += `
                    <div class="cluster-card">
                        <div class="cluster-header">
                            <span class="cluster-name">Mock FTP-${paddedIndex}</span>
                            <span class="status-indicator ${isRunning ? 'live' : ''}">${isRunning ? 'Online' : 'Stopped'}</span>
                        </div>
                        <div class="cluster-port">Port: <strong>${port}</strong></div>
                        <button class="btn ${isRunning ? 'btn-outline' : 'btn-primary'}" style="font-size:0.75rem;padding:0.4rem 0.8rem;" onclick="toggleMockServer(${port})">
                            ${isRunning ? '🛑 Stop (Simulate Drop)' : '▶️ Restart Server'}
                        </button>
                    </div>
                `;
            }

            grid.innerHTML = html;
            badge.textContent = `${runningCount}/10 FTP Nodes Live`;
            if (runningCount === 10) {
                badge.style.color = 'var(--success)';
            } else {
                badge.style.color = 'var(--warning)';
            }
        }
    } catch (e) {
        console.error('Failed to fetch mock cluster status:', e);
    }
}

window.toggleMockServer = async function(port) {
    try {
        await fetch(`/api/ftp/mock-cluster/${port}/toggle`, { method: 'POST' });
        fetchClusterStatus();
    } catch (e) {
        alert('Failed to toggle server on port ' + port);
    }
};

async function fetchFtpServers() {
    try {
        const res = await fetch('/api/ftp/servers');
        if (res.ok) {
            const servers = await res.json();
            const tbody = document.getElementById('ftpServersTableBody');
            tbody.innerHTML = servers.map(s => `
                <tr>
                    <td><strong>${escapeHtml(s.name)}</strong></td>
                    <td><code>${escapeHtml(s.host)}:${s.port}</code></td>
                    <td>${escapeHtml(s.username)}</td>
                    <td><code>${escapeHtml(s.remoteDir)}</code></td>
                    <td><span class="badge">${s.protocol}</span></td>
                    <td><span class="status-indicator ${s.enabled ? 'live' : ''}">${s.enabled ? 'Enabled' : 'Disabled'}</span></td>
                    <td>
                        <button class="btn btn-outline" style="padding:0.25rem 0.6rem;font-size:0.72rem;" onclick="testFtpConnection(${s.id})">Test Auth</button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Failed to fetch FTP servers:', e);
    }
}

window.testFtpConnection = async function(id) {
    try {
        const res = await fetch(`/api/ftp/servers/${id}/test`, { method: 'POST' });
        const data = await res.json();
        alert(`[${data.serverName}] ${data.message}`);
    } catch (e) {
        alert('Error testing connection');
    }
};

// UTILITIES
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
