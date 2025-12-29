// ZVIT PWA Application

// State
let currentUser = null;
let currentGroup = null;
let selectedReportResponse = 'OK';
let deferredPrompt = null;

// API Base URL
const API_BASE = '/api/v1';

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    initApp();
    registerServiceWorker();
    setupInstallPrompt();
});

async function initApp() {
    // Check if user is logged in
    const token = localStorage.getItem('zvit_token');
    const userData = localStorage.getItem('zvit_user');

    if (token && userData) {
        currentUser = JSON.parse(userData);
        showMainScreen();
    } else {
        showScreen('loginScreen');
    }

    // Setup form handlers
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('registerForm').addEventListener('submit', handleRegister);
    document.getElementById('verifyForm').addEventListener('submit', handleVerify);
}

// Service Worker
async function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        try {
            const registration = await navigator.serviceWorker.register('/service-worker.js');
            console.log('SW registered:', registration);

            // Listen for messages from SW
            navigator.serviceWorker.addEventListener('message', (event) => {
                if (event.data.type === 'NOTIFICATION_CLICK') {
                    handleNotificationClick(event.data.data);
                }
            });
        } catch (error) {
            console.error('SW registration failed:', error);
        }
    }
}

// Install PWA
function setupInstallPrompt() {
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        deferredPrompt = e;

        // Show install banner after login
        if (currentUser && !localStorage.getItem('zvit_install_dismissed')) {
            document.getElementById('installBanner').classList.add('show');
        }
    });

    window.addEventListener('appinstalled', () => {
        console.log('PWA installed');
        document.getElementById('installBanner').classList.remove('show');
        deferredPrompt = null;
    });
}

async function installPWA() {
    if (!deferredPrompt) {
        // iOS Safari
        showToast('Натисніть "Поділитися" → "На Початковий екран"', 'info');
        return;
    }

    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    console.log('Install outcome:', outcome);
    deferredPrompt = null;
    document.getElementById('installBanner').classList.remove('show');
}

function dismissInstallBanner() {
    document.getElementById('installBanner').classList.remove('show');
    localStorage.setItem('zvit_install_dismissed', 'true');
}

// Screen Navigation
function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(screenId).classList.add('active');

    // Show/hide bottom nav
    const bottomNav = document.getElementById('bottomNav');
    const authScreens = ['loginScreen', 'registerScreen', 'verifyScreen'];
    bottomNav.style.display = authScreens.includes(screenId) ? 'none' : 'flex';

    // Update nav active state
    document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
    if (screenId === 'mainScreen') {
        document.querySelector('.nav-item:first-child').classList.add('active');
    } else if (screenId === 'settingsScreen') {
        document.querySelector('.nav-item:last-child').classList.add('active');
    }
}

function showMainScreen() {
    showScreen('mainScreen');
    loadGroups();

    // Update settings
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
    }

    // Check notifications permission
    updateNotificationsToggle();

    // Show install banner if available
    if (deferredPrompt && !localStorage.getItem('zvit_install_dismissed')) {
        setTimeout(() => {
            document.getElementById('installBanner').classList.add('show');
        }, 2000);
    }
}

// Authentication
async function handleLogin(e) {
    e.preventDefault();

    const phone = document.getElementById('loginPhone').value;
    const password = document.getElementById('loginPassword').value;
    const btn = document.getElementById('loginBtn');

    btn.disabled = true;
    btn.textContent = 'Вхід...';

    try {
        const response = await apiRequest('/auth/login', 'POST', {
            phone: phone,
            password: password
        });

        if (response.success) {
            // Handle encrypted response
            const loginData = response.data;

            // Store token and user data
            // Note: In production, you'd need to decrypt the response
            // For PWA, we'll use a simplified flow
            localStorage.setItem('zvit_token', loginData.token || loginData.encryptedPayload);
            localStorage.setItem('zvit_user', JSON.stringify({
                id: loginData.userId,
                name: loginData.name,
                phone: phone
            }));

            currentUser = {
                id: loginData.userId,
                name: loginData.name,
                phone: phone
            };

            showToast('Успішний вхід!', 'success');
            showMainScreen();
        } else {
            showToast(response.message || 'Помилка входу', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка підключення', 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Увійти';
    }
}

async function handleRegister(e) {
    e.preventDefault();

    const name = document.getElementById('registerName').value;
    const phone = document.getElementById('registerPhone').value;
    const password = document.getElementById('registerPassword').value;
    const btn = document.getElementById('registerBtn');

    if (password.length < 6) {
        showToast('Пароль має бути мінімум 6 символів', 'error');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Реєстрація...';

    try {
        const response = await apiRequest('/auth/register', 'POST', {
            name: name,
            phone: phone,
            password: password
        });

        if (response.success) {
            // Store phone for verification
            localStorage.setItem('zvit_pending_phone', phone);
            localStorage.setItem('zvit_pending_password', password);

            showToast('Код відправлено на ваш телефон', 'success');
            showScreen('verifyScreen');
        } else {
            showToast(response.message || 'Помилка реєстрації', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка підключення', 'error');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Зареєструватися';
    }
}

async function handleVerify(e) {
    e.preventDefault();

    const code = document.getElementById('verifyCode').value;
    const phone = localStorage.getItem('zvit_pending_phone');
    const password = localStorage.getItem('zvit_pending_password');

    try {
        // Try to login after verification
        const response = await apiRequest('/auth/login', 'POST', {
            phone: phone,
            password: password,
            verificationCode: code
        });

        if (response.success) {
            localStorage.removeItem('zvit_pending_phone');
            localStorage.removeItem('zvit_pending_password');

            const loginData = response.data;
            localStorage.setItem('zvit_token', loginData.token || loginData.encryptedPayload);
            localStorage.setItem('zvit_user', JSON.stringify({
                id: loginData.userId,
                name: loginData.name,
                phone: phone
            }));

            currentUser = {
                id: loginData.userId,
                name: loginData.name,
                phone: phone
            };

            showToast('Реєстрація успішна!', 'success');
            showMainScreen();
        } else {
            showToast(response.message || 'Невірний код', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка підключення', 'error');
    }
}

function logout() {
    localStorage.removeItem('zvit_token');
    localStorage.removeItem('zvit_user');
    currentUser = null;
    currentGroup = null;
    showScreen('loginScreen');
    showToast('Ви вийшли з акаунту', 'success');
}

// Groups
async function loadGroups() {
    const container = document.getElementById('groupsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        const response = await apiRequest('/groups', 'GET');

        if (response.success && response.data) {
            renderGroups(response.data);
        } else {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 6c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2m0 10c2.7 0 5.8 1.29 6 2H6c.23-.72 3.31-2 6-2m0-12C9.79 4 8 5.79 8 8s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 10c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                    </svg>
                    <p>У вас ще немає груп</p>
                    <p style="margin-top: 10px;">Створіть нову групу або приєднайтесь до існуючої</p>
                </div>
            `;
        }
    } catch (error) {
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження груп</p>
                <button class="btn btn-secondary" style="margin-top: 10px;" onclick="loadGroups()">Спробувати знову</button>
            </div>
        `;
    }
}

function renderGroups(groups) {
    const container = document.getElementById('groupsList');

    if (!groups || groups.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 6c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2m0 10c2.7 0 5.8 1.29 6 2H6c.23-.72 3.31-2 6-2m0-12C9.79 4 8 5.79 8 8s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm0 10c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                </svg>
                <p>У вас ще немає груп</p>
            </div>
        `;
        return;
    }

    container.innerHTML = groups.map(group => `
        <div class="card group-card" onclick="openGroup('${group.id}', '${escapeHtml(group.name)}')">
            <div class="group-icon">${group.name.charAt(0).toUpperCase()}</div>
            <div class="group-info">
                <div class="group-name">${escapeHtml(group.name)}</div>
                <div class="group-members">${group.memberCount || 0} учасників</div>
            </div>
            ${group.pendingReports > 0 ? `<div class="group-badge">${group.pendingReports}</div>` : ''}
        </div>
    `).join('');
}

async function openGroup(groupId, groupName) {
    currentGroup = { id: groupId, name: groupName };
    document.getElementById('groupTitle').textContent = groupName;
    showScreen('groupScreen');
    loadReports(groupId);
}

// Reports
async function loadReports(groupId) {
    const container = document.getElementById('reportsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        const response = await apiRequest(`/reports/group/${groupId}`, 'GET');

        if (response.success && response.data) {
            renderReports(response.data);
        } else {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 9h-2v2H9v-2H7v-2h2V7h2v2h2v2zm-1-8.5L17.5 8H13V3.5z"/>
                    </svg>
                    <p>Немає звітів</p>
                </div>
            `;
        }
    } catch (error) {
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження звітів</p>
            </div>
        `;
    }
}

function renderReports(reports) {
    const container = document.getElementById('reportsList');

    if (!reports || reports.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 9h-2v2H9v-2H7v-2h2V7h2v2h2v2zm-1-8.5L17.5 8H13V3.5z"/>
                </svg>
                <p>Немає звітів</p>
            </div>
        `;
        return;
    }

    container.innerHTML = `<div class="card">${reports.map(report => `
        <div class="report-item">
            <div class="report-avatar">${(report.userName || 'U').charAt(0).toUpperCase()}</div>
            <div class="report-content">
                <div class="report-header">
                    <span class="report-name">${escapeHtml(report.userName || 'Невідомий')}</span>
                    <span class="report-time">${formatTime(report.createdAt)}</span>
                </div>
                <span class="report-status ${report.response === 'ТЕРМІНОВО' ? 'urgent' : 'ok'}">${escapeHtml(report.response)}</span>
                ${report.comment ? `<div class="report-comment">${escapeHtml(report.comment)}</div>` : ''}
            </div>
        </div>
    `).join('')}</div>`;
}

// Report Modal
function openReportModal() {
    if (!currentGroup && !currentUser) {
        showToast('Спочатку оберіть групу', 'error');
        return;
    }
    document.getElementById('reportModal').classList.add('active');
    document.getElementById('reportComment').value = '';
    selectedReportResponse = 'OK';

    // Reset selection
    document.querySelectorAll('.report-option').forEach(opt => {
        opt.classList.remove('selected');
        if (opt.dataset.response === 'OK') {
            opt.classList.add('selected');
        }
    });
}

function closeReportModal() {
    document.getElementById('reportModal').classList.remove('active');
}

function selectReportOption(element) {
    document.querySelectorAll('.report-option').forEach(opt => opt.classList.remove('selected'));
    element.classList.add('selected');
    selectedReportResponse = element.dataset.response;
}

async function sendReport() {
    if (!currentGroup) {
        showToast('Оберіть групу', 'error');
        return;
    }

    const comment = document.getElementById('reportComment').value;

    try {
        const response = await apiRequest('/reports', 'POST', {
            groupId: currentGroup.id,
            response: selectedReportResponse,
            comment: comment || null
        });

        if (response.success) {
            showToast('Звіт відправлено!', 'success');
            closeReportModal();
            loadReports(currentGroup.id);
        } else {
            showToast(response.message || 'Помилка відправки', 'error');
        }
    } catch (error) {
        // Try background sync if offline
        if (!navigator.onLine) {
            await saveReportForSync({
                groupId: currentGroup.id,
                response: selectedReportResponse,
                comment: comment
            });
            showToast('Звіт збережено, буде відправлено при підключенні', 'info');
            closeReportModal();
        } else {
            showToast(error.message || 'Помилка відправки', 'error');
        }
    }
}

async function saveReportForSync(reportData) {
    if ('serviceWorker' in navigator && 'SyncManager' in window) {
        const cache = await caches.open('zvit-pending-reports');
        const token = localStorage.getItem('zvit_token');

        await cache.put(
            new Request(`/pending-report-${Date.now()}`),
            new Response(JSON.stringify({ report: reportData, token: token }))
        );

        const registration = await navigator.serviceWorker.ready;
        await registration.sync.register('sync-reports');
    }
}

// Notifications
async function toggleNotifications() {
    const toggle = document.getElementById('notificationsToggle');

    if (toggle.classList.contains('active')) {
        // Disable
        toggle.classList.remove('active');
        // Unsubscribe logic here
    } else {
        // Enable
        const permission = await requestNotificationPermission();
        if (permission === 'granted') {
            toggle.classList.add('active');
            await subscribeToPush();
        }
    }
}

async function requestNotificationPermission() {
    if (!('Notification' in window)) {
        showToast('Браузер не підтримує сповіщення', 'error');
        return 'denied';
    }

    const permission = await Notification.requestPermission();

    if (permission === 'granted') {
        showToast('Сповіщення увімкнено', 'success');
    } else if (permission === 'denied') {
        showToast('Сповіщення заблоковані в налаштуваннях браузера', 'error');
    }

    return permission;
}

async function subscribeToPush() {
    try {
        const registration = await navigator.serviceWorker.ready;

        // Get VAPID public key from server
        // For now, we'll skip this as it requires backend setup
        console.log('Push subscription would be set up here');
    } catch (error) {
        console.error('Push subscription failed:', error);
    }
}

function updateNotificationsToggle() {
    const toggle = document.getElementById('notificationsToggle');
    if ('Notification' in window && Notification.permission === 'granted') {
        toggle.classList.add('active');
    } else {
        toggle.classList.remove('active');
    }
}

function handleNotificationClick(data) {
    if (data.groupId) {
        openGroup(data.groupId, data.groupName || 'Група');
    }
}

// API Helper
async function apiRequest(endpoint, method = 'GET', body = null) {
    const token = localStorage.getItem('zvit_token');
    const headers = {
        'Content-Type': 'application/json'
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const options = {
        method,
        headers
    };

    if (body) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(`${API_BASE}${endpoint}`, options);

    // Handle 401 - redirect to login
    if (response.status === 401) {
        logout();
        throw new Error('Сесія закінчилась');
    }

    const data = await response.json();

    if (!response.ok && !data.success) {
        throw new Error(data.message || 'Помилка сервера');
    }

    return data;
}

// Helpers
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = `toast show ${type}`;

    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatTime(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;

    // Less than 1 hour
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return `${minutes} хв тому`;
    }

    // Today
    if (date.toDateString() === now.toDateString()) {
        return date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
    }

    // This week
    if (diff < 604800000) {
        return date.toLocaleDateString('uk-UA', { weekday: 'short', hour: '2-digit', minute: '2-digit' });
    }

    // Older
    return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short' });
}

// Handle back button
window.addEventListener('popstate', () => {
    const currentScreen = document.querySelector('.screen.active');
    if (currentScreen.id === 'groupScreen') {
        showScreen('mainScreen');
    } else if (currentScreen.id === 'settingsScreen') {
        showScreen('mainScreen');
    }
});

// Prevent zoom on input focus (iOS)
document.addEventListener('gesturestart', (e) => e.preventDefault());
