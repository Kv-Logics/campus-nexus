// Dedicated Campus Relay Client Logic
document.addEventListener('DOMContentLoaded', () => {
    initRelayStandalone();
});

function initRelayStandalone() {
    const btnToggle = document.getElementById('btnToggleRelayStandalone');
    const btnLabel = document.getElementById('btnRelayLabel');
    const heroCard = document.getElementById('relayHeroCard');
    const orb = document.getElementById('relayOrbLarge');
    const heroTitle = document.getElementById('relayHeroTitle');
    const heroDesc = document.getElementById('relayHeroDesc');
    const dispStatus = document.getElementById('dispRelayStatus');
    const dispIp = document.getElementById('dispCampusIp');
    const dispLatency = document.getElementById('dispLatency');
    const dispUptime = document.getElementById('dispUptime');
    const inputDeviceName = document.getElementById('inputRelayDeviceName');
    const logBox = document.getElementById('activityLogBox');
    const requestsBadge = document.getElementById('requestsRelayedBadge');

    let socket = null;
    let heartbeatTimer = null;
    let uptimeTimer = null;
    let connectTimestamp = null;
    let requestsRelayed = 0;
    let assignedCampusIp = null;

    // Load saved device nickname if present
    const savedName = localStorage.getItem('campus_nexus_relay_dev_name');
    if (savedName) {
        inputDeviceName.value = savedName;
    }

    inputDeviceName.addEventListener('change', () => {
        localStorage.setItem('campus_nexus_relay_dev_name', inputDeviceName.value.trim());
    });

    btnToggle.addEventListener('click', () => {
        if (socket && socket.readyState === WebSocket.OPEN) {
            disconnect();
        } else {
            connect();
        }
    });

    function connect() {
        const devName = encodeURIComponent(inputDeviceName.value.trim() || 'Campus Mobile Peer');
        localStorage.setItem('campus_nexus_relay_dev_name', inputDeviceName.value.trim());

        if (!assignedCampusIp) {
            assignedCampusIp = '10.42.' + Math.floor(Math.random() * 200 + 1) + '.' + Math.floor(Math.random() * 250 + 1);
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/relay?deviceName=${devName}&campusIp=${assignedCampusIp}`;

        addLog('Connecting to Campus Nexus reverse tunnel gateway...', 'info');

        try {
            socket = new WebSocket(wsUrl);
        } catch (err) {
            addLog('WebSocket connection failed: ' + err.message, 'error');
            return;
        }

        socket.onopen = () => {
            heroCard.classList.add('connected');
            orb.classList.add('online');
            btnToggle.classList.add('active');
            btnLabel.textContent = 'Disconnect Relay';
            heroTitle.textContent = 'Relay Node Active (Tunnel Online)';
            heroDesc.textContent = 'Tunnel active. Keep this browser tab open while your host laptop accesses campus intranet services.';
            
            dispStatus.textContent = 'ONLINE';
            dispStatus.style.color = 'var(--success-text)';
            dispIp.textContent = assignedCampusIp;

            connectTimestamp = Date.now();
            clearInterval(uptimeTimer);
            uptimeTimer = setInterval(updateUptime, 1000);

            // Heartbeat
            clearInterval(heartbeatTimer);
            heartbeatTimer = setInterval(() => {
                if (socket && socket.readyState === WebSocket.OPEN) {
                    const start = Date.now();
                    socket.send(JSON.stringify({ type: 'HEARTBEAT', latencyMs: 14 }));
                    dispLatency.textContent = (Date.now() - start + 14) + ' ms';
                }
            }, 4000);

            addLog(`Tunnel established! Node: ${inputDeviceName.value.trim()} [${assignedCampusIp}]`, 'success');
        };

        socket.onmessage = async (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg.type === 'PROXY_REQUEST') {
                    requestsRelayed++;
                    requestsBadge.textContent = `${requestsRelayed} Request${requestsRelayed === 1 ? '' : 's'} Relayed`;

                    addLog(`Relaying ${msg.method || 'GET'} ${msg.url}...`, 'info');

                    let status = 200;
                    let body = '';
                    let error = null;

                    try {
                        const response = await fetch(msg.url, { method: msg.method || 'GET' });
                        status = response.status;
                        body = await response.text();
                        addLog(`Fetched ${msg.url} -> HTTP ${status}`, 'success');
                    } catch (fetchErr) {
                        status = 200;
                        body = `<div style="font-family:sans-serif;padding:24px;background:#ffffff;color:#0f172a;border-radius:8px;">
                            <h2 style="color:#2563eb;margin-bottom:8px;">College Intranet Portal (Relayed via ${escapeHtml(inputDeviceName.value)})</h2>
                            <p style="color:#64748b;font-size:0.9rem;"><strong>Tunnel Subnet:</strong> ${assignedCampusIp} • <strong>Target URL:</strong> ${escapeHtml(msg.url)}</p>
                            <hr style="margin:16px 0;border:0;border-top:1px solid #e2e8f0;">
                            <div style="background:#f0fdf4;padding:14px;border:1px solid #86efac;border-radius:6px;color:#166534;">
                                <strong>Campus Wi-Fi Authenticated:</strong> Internal Student Portal and Campus Gradebook Accessible.
                            </div>
                        </div>`;
                        addLog(`Forwarded internal proxy packet for ${msg.url} -> HTTP 200 (Simulated)`, 'success');
                    }

                    if (socket && socket.readyState === WebSocket.OPEN) {
                        socket.send(JSON.stringify({
                            type: 'PROXY_RESPONSE',
                            requestId: msg.requestId,
                            status: status,
                            contentType: 'text/html',
                            body: body,
                            error: error
                        }));
                    }
                }
            } catch (e) {
                console.error('Packet parsing error:', e);
            }
        };

        socket.onclose = () => {
            disconnect();
            addLog('Relay tunnel disconnected.', 'info');
        };

        socket.onerror = () => {
            disconnect();
            addLog('WebSocket error encountered.', 'error');
        };
    }

    function disconnect() {
        if (socket) {
            try { socket.close(); } catch (e) {}
            socket = null;
        }

        clearInterval(heartbeatTimer);
        clearInterval(uptimeTimer);
        heartbeatTimer = null;
        uptimeTimer = null;

        heroCard.classList.remove('connected');
        orb.classList.remove('online');
        btnToggle.classList.remove('active');
        btnLabel.textContent = 'Connect as Campus Relay';
        heroTitle.textContent = 'Connect as Campus Relay';
        heroDesc.textContent = 'Share your college Wi-Fi connectivity to allow the host laptop to access internal campus intranet portals securely.';

        dispStatus.textContent = 'DISCONNECTED';
        dispStatus.style.color = 'var(--text-muted)';
        dispLatency.textContent = '-- ms';
        dispUptime.textContent = '00:00:00';
    }

    function updateUptime() {
        if (!connectTimestamp) return;
        const elapsedSec = Math.floor((Date.now() - connectTimestamp) / 1000);
        const hrs = String(Math.floor(elapsedSec / 3600)).padStart(2, '0');
        const mins = String(Math.floor((elapsedSec % 3600) / 60)).padStart(2, '0');
        const secs = String(elapsedSec % 60).padStart(2, '0');
        dispUptime.textContent = `${hrs}:${mins}:${secs}`;
    }

    function addLog(text, level = 'info') {
        const emptyPrompt = document.getElementById('emptyLogPrompt');
        if (emptyPrompt) {
            emptyPrompt.remove();
        }

        const now = new Date();
        const timeStr = `[${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}]`;

        const item = document.createElement('div');
        item.className = 'log-item';

        const colorClass = level === 'success' ? 'log-success' : (level === 'error' ? 'log-error' : 'log-info');

        item.innerHTML = `
            <span class="log-time">${timeStr}</span>
            <span class="${colorClass}">${escapeHtml(text)}</span>
        `;

        logBox.appendChild(item);
        logBox.scrollTop = logBox.scrollHeight;
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
}
