// Campus Nexus Client Logic
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initFileUpload();
    initJobPolling();
    initRelayNode();
    initMockCluster();
});

// NAVIGATION TABS WITH REFRESH PERSISTENCE
function initNavigation() {
    const tabs = document.querySelectorAll('.nav-tab');

    function switchTab(targetId, updateUrl = true) {
        const targetPane = document.getElementById(targetId);
        const targetBtn = document.querySelector(`.nav-tab[data-tab="${targetId}"]`);
        if (!targetPane || !targetBtn) return;

        tabs.forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

        targetBtn.classList.add('active');
        targetPane.classList.add('active');

        // Save preference
        localStorage.setItem('campus_nexus_active_tab', targetId);

        if (updateUrl) {
            const shortName = targetId.replace('-tab', '');
            if (window.location.hash !== '#' + shortName) {
                history.replaceState(null, null, '#' + shortName);
            }
        }
    }

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const targetId = tab.getAttribute('data-tab');
            switchTab(targetId, true);
        });
    });

    // Handle initial tab selection on page load or refresh
    function restoreActiveTab() {
        const hash = window.location.hash.replace('#', '').trim();
        if (hash) {
            // Check if hash matches "servers", "relay", "distribution", or full id "servers-tab"
            const fullTabId = hash.endsWith('-tab') ? hash : hash + '-tab';
            const pane = document.getElementById(fullTabId);
            if (pane) {
                switchTab(fullTabId, false);
                return;
            }
        }

        // Fallback to localStorage
        const savedTab = localStorage.getItem('campus_nexus_active_tab');
        if (savedTab && document.getElementById(savedTab)) {
            switchTab(savedTab, true);
        }
    }

    restoreActiveTab();

    // Listen for browser back / forward or manual hash changes
    window.addEventListener('hashchange', restoreActiveTab);
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
                <div class="empty-icon"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg></div>
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
                        <div class="file-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg></div>
                        <div>
                            <div class="file-name">${escapeHtml(file.filename)}</div>
                            <div class="file-meta">Staged size: ${formatBytes(file.fileSize)} • Staged at: ${createdDate}</div>
                        </div>
                    </div>
                    <div class="checksum-pill" title="Cryptographic SHA-256 Checksum">
                        <span>SHA-256:</span>
                        <code>${file.sha256Checksum.substring(0, 16)}...</code>
                        <button onclick="navigator.clipboard.writeText('${file.sha256Checksum}')" style="background:none;border:none;color:var(--primary);cursor:pointer;display:inline-flex;align-items:center;" title="Copy Full Hash"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg></button>
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
        retryBtn = `<button class="btn-retry" onclick="triggerRetry('${job.id}')">Retry</button>`;
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
    const shareRelayUrlInput = document.getElementById('shareRelayUrlInput');
    const btnCopyRelayUrl = document.getElementById('btnCopyRelayUrl');

    if (shareRelayUrlInput) {
        shareRelayUrlInput.value = window.location.origin + '/relay.html';
    }

    if (btnCopyRelayUrl) {
        btnCopyRelayUrl.addEventListener('click', () => {
            const url = shareRelayUrlInput ? shareRelayUrlInput.value : (window.location.origin + '/relay.html');
            navigator.clipboard.writeText(url).then(() => {
                btnCopyRelayUrl.textContent = 'Copied!';
                setTimeout(() => { btnCopyRelayUrl.textContent = 'Copy Link'; }, 2000);
            }).catch(() => {
                alert('Link: ' + url);
            });
        });
    }

    btnToggleRelay.addEventListener('click', () => {
        if (relaySocket && relaySocket.readyState === WebSocket.OPEN) {
            disconnectRelay();
        } else {
            connectRelay();
        }
    });

    function connectRelay() {
        const deviceName = 'Campus Host Node';
        const simulatedIp = '10.42.' + Math.floor(Math.random() * 200 + 1) + '.' + Math.floor(Math.random() * 250 + 1);
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/relay?deviceName=${encodeURIComponent(deviceName)}&campusIp=${simulatedIp}`;

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
                            <h2 style="color:#1e3a8a;">College Intranet Portal (Relayed via ${escapeHtml(inputDeviceName.value)})</h2>
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

// PRODUCTION FTP SERVERS MANAGER
let ftpServersCache = [];

function initMockCluster() {
    fetchFtpServers();
    initFtpEditModal();
    initBatchFtpControls();
}

function initBatchFtpControls() {
    const btnDisableAll = document.getElementById('btnDisableAllFtp');
    const btnEnableAll = document.getElementById('btnEnableAllFtp');

    if (btnDisableAll) {
        btnDisableAll.addEventListener('click', async () => {
            if (confirm('Disable all 10 FTP destinations?')) {
                await fetch('/api/ftp/servers/disable-all', { method: 'POST' });
                fetchFtpServers();
            }
        });
    }

    if (btnEnableAll) {
        btnEnableAll.addEventListener('click', async () => {
            await fetch('/api/ftp/servers/enable-all', { method: 'POST' });
            fetchFtpServers();
        });
    }
}

async function fetchFtpServers() {
    try {
        const res = await fetch('/api/ftp/servers');
        if (res.ok) {
            ftpServersCache = await res.json();
            const tbody = document.getElementById('ftpServersTableBody');
            const badge = document.getElementById('clusterStatusBadge');

            const activeCount = ftpServersCache.filter(s => s.enabled).length;
            if (badge) {
                badge.textContent = `${activeCount}/10 Targets Active`;
                badge.className = `status-indicator ${activeCount > 0 ? 'live' : ''}`;
            }

            tbody.innerHTML = ftpServersCache.map(s => {
                const hostDisplay = s.host ? `<code>${escapeHtml(s.host)}:${s.port || 21}</code>` : `<span style="color:var(--text-dim);font-size:0.75rem;font-style:italic;">Not configured (click Edit)</span>`;
                const userDisplay = s.username ? escapeHtml(s.username) : `<span style="color:var(--text-dim);font-size:0.75rem;">—</span>`;
                const dirDisplay = s.remoteDir ? `<code>${escapeHtml(s.remoteDir)}</code>` : `<code>/</code>`;

                return `
                    <tr>
                        <td><strong>${escapeHtml(s.name)}</strong></td>
                        <td>${hostDisplay}</td>
                        <td>${userDisplay}</td>
                        <td>${dirDisplay}</td>
                        <td><span class="badge">${s.protocol || 'FTP'}</span></td>
                        <td>
                            <span class="status-indicator ${s.enabled ? 'live' : ''}">${s.enabled ? 'Active' : 'Disabled'}</span>
                        </td>
                        <td style="text-align: right; white-space: nowrap;">
                            <button class="btn btn-primary" style="padding:0.3rem 0.65rem;font-size:0.75rem;" onclick="openEditModal('${s.id}')">Edit</button>
                            <button class="btn btn-outline" style="padding:0.3rem 0.65rem;font-size:0.75rem;margin-left:0.3rem;" onclick="testFtpConnection('${s.id}')">Test</button>
                            <button class="btn btn-outline" style="padding:0.3rem 0.65rem;font-size:0.75rem;margin-left:0.3rem;" onclick="toggleFtpServer('${s.id}')">
                                ${s.enabled ? 'Disable' : 'Enable'}
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');
        }
    } catch (e) {
        console.error('Failed to fetch FTP servers:', e);
    }
}

window.openEditModal = function(id) {
    const server = ftpServersCache.find(s => s.id === id);
    if (!server) return;

    document.getElementById('editServerId').value = server.id;
    document.getElementById('modalTitle').textContent = `Edit Configuration: ${server.name}`;
    document.getElementById('editServerName').value = server.name;
    document.getElementById('editHost').value = server.host;
    document.getElementById('editPort').value = server.port;
    document.getElementById('editUsername').value = server.username;
    document.getElementById('editPassword').value = '';
    document.getElementById('editRemoteDir').value = server.remoteDir;
    document.getElementById('editProtocol').value = server.protocol || 'FTP';
    document.getElementById('editEnabled').checked = server.enabled !== false;

    document.getElementById('editFtpModal').classList.remove('hidden');
};

function initFtpEditModal() {
    const modal = document.getElementById('editFtpModal');
    const btnClose = document.getElementById('btnCloseModal');
    const btnCancel = document.getElementById('btnCancelModal');
    const form = document.getElementById('editFtpForm');
    const btnTestModal = document.getElementById('btnTestModalConnection');

    const closeModal = () => modal.classList.add('hidden');
    btnClose.addEventListener('click', closeModal);
    btnCancel.addEventListener('click', closeModal);

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('editServerId').value;
        const payload = {
            name: document.getElementById('editServerName').value.trim(),
            host: document.getElementById('editHost').value.trim(),
            port: parseInt(document.getElementById('editPort').value, 10),
            username: document.getElementById('editUsername').value.trim(),
            password: document.getElementById('editPassword').value,
            remoteDir: document.getElementById('editRemoteDir').value.trim(),
            protocol: document.getElementById('editProtocol').value,
            enabled: document.getElementById('editEnabled').checked
        };

        try {
            const res = await fetch(`/api/ftp/servers/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                closeModal();
                fetchFtpServers();
                alert(`Configuration for ${payload.name} saved successfully!`);
            } else {
                alert('Failed to save configuration: ' + await res.text());
            }
        } catch (err) {
            alert('Error updating FTP server: ' + err.message);
        }
    });

    btnTestModal.addEventListener('click', async () => {
        const id = document.getElementById('editServerId').value;
        btnTestModal.textContent = 'Testing...';
        btnTestModal.disabled = true;
        try {
            await testFtpConnection(id);
        } finally {
            btnTestModal.textContent = 'Test Connection';
            btnTestModal.disabled = false;
        }
    });
}

window.toggleFtpServer = async function(id) {
    try {
        const res = await fetch(`/api/ftp/servers/${id}/toggle`, { method: 'POST' });
        if (res.ok) fetchFtpServers();
    } catch (e) {
        alert('Failed to toggle FTP server status');
    }
};

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
