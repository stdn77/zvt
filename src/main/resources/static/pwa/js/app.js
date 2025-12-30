// ZVIT PWA Application

// State
let currentUser = null;
let currentGroup = null;
let selectedReportResponse = 'OK';
let deferredPrompt = null;

// API Base URL
const API_BASE = '/api/v1';

// Phone utilities
function normalizePhone(phone) {
    // Видаляємо всі символи крім цифр
    let digits = phone.replace(/\D/g, '');

    // Нормалізуємо до формату 380XXXXXXXXX
    if (digits.startsWith('380')) {
        // Вже правильний формат
    } else if (digits.startsWith('80')) {
        digits = '3' + digits;
    } else if (digits.startsWith('0')) {
        digits = '38' + digits;
    } else if (digits.length === 9) {
        // 671111111 -> 380671111111
        digits = '380' + digits;
    }

    // Повертаємо з + для API (сервер очікує +380XXXXXXXXX)
    return '+' + digits;
}

function formatPhoneDisplay(phone) {
    const normalized = normalizePhone(phone);
    // normalized = +380XXXXXXXXX (13 символів)
    if (normalized.length === 13) {
        // +380671111111 -> +380 67 111 11 11
        const digits = normalized.slice(1); // remove +
        return `+${digits.slice(0,3)} ${digits.slice(3,5)} ${digits.slice(5,8)} ${digits.slice(8,10)} ${digits.slice(10,12)}`;
    }
    return phone;
}

// Password visibility toggle
function togglePassword(inputId, button) {
    const input = document.getElementById(inputId);
    const isPassword = input.type === 'password';

    input.type = isPassword ? 'text' : 'password';

    // Update icon
    const eyeOpen = `<path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>`;
    const eyeClosed = `<path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/>`;

    button.querySelector('svg').innerHTML = isPassword ? eyeClosed : eyeOpen;
}

// Forgot Password
function showForgotPasswordInfo() {
    document.getElementById('forgotPasswordModal').classList.add('active');
}

function setupPhoneInput(input) {
    input.addEventListener('input', (e) => {
        let value = e.target.value;

        // Якщо користувач почав вводити без +, додаємо +380
        if (value && !value.startsWith('+')) {
            const digits = value.replace(/\D/g, '');
            if (digits.length > 0) {
                if (digits.startsWith('380')) {
                    value = '+' + digits;
                } else if (digits.startsWith('80')) {
                    value = '+3' + digits;
                } else if (digits.startsWith('0')) {
                    value = '+38' + digits;
                } else {
                    value = '+380' + digits;
                }
            }
        }

        // Форматуємо для відображення
        const digits = value.replace(/\D/g, '');
        if (digits.length >= 3) {
            let formatted = '+' + digits.slice(0, 3);
            if (digits.length > 3) formatted += ' ' + digits.slice(3, 5);
            if (digits.length > 5) formatted += ' ' + digits.slice(5, 8);
            if (digits.length > 8) formatted += ' ' + digits.slice(8, 10);
            if (digits.length > 10) formatted += ' ' + digits.slice(10, 12);
            e.target.value = formatted.slice(0, 17); // +380 67 111 11 11 = 17 chars
        }
    });

    // Встановлюємо початкове значення
    if (!input.value) {
        input.value = '+380 ';
    }
}

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

    // Setup phone inputs with mask
    setupPhoneInput(document.getElementById('loginPhone'));
    setupPhoneInput(document.getElementById('registerPhone'));
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
    const mainScreens = ['mainScreen', 'reportsScreen', 'settingsScreen'];
    bottomNav.style.display = authScreens.includes(screenId) ? 'none' : 'flex';

    // Update nav active state based on data-screen attribute
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
        if (item.dataset.screen === screenId) {
            item.classList.add('active');
        }
    });
}

// Navigation with data loading
function navigateTo(screenId) {
    showScreen(screenId);

    // Load data for the screen
    if (screenId === 'mainScreen') {
        loadGroups();
    } else if (screenId === 'reportsScreen') {
        loadMyReports();
    } else if (screenId === 'settingsScreen') {
        updateSettingsScreen();
    }
}

function updateSettingsScreen() {
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
        document.getElementById('profileEmail').textContent = currentUser.email || 'Не вказано';
    }
    updateNotificationsToggle();
}

function showMainScreen() {
    // Після входу показуємо екран Звіти (як в Android)
    showScreen('reportsScreen');
    loadMyReports();

    // Update settings
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
        document.getElementById('profileEmail').textContent = currentUser.email || 'Не вказано';
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

    const phoneRaw = document.getElementById('loginPhone').value;
    const phone = normalizePhone(phoneRaw);
    const password = document.getElementById('loginPassword').value;
    const btn = document.getElementById('loginBtn');

    // phone = +380XXXXXXXXX (13 символів)
    if (phone.length !== 13) {
        showToast('Невірний формат телефону', 'error');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Вхід...';

    try {
        const response = await apiRequest('/pwa/login', 'POST', {
            phone: phone,
            password: password
        });

        if (response.success && response.data) {
            const loginData = response.data;

            // Store token and user data
            localStorage.setItem('zvit_token', loginData.token);
            localStorage.setItem('zvit_user', JSON.stringify({
                id: loginData.userId,
                name: loginData.name,
                phone: formatPhoneDisplay(phone)
            }));

            currentUser = {
                id: loginData.userId,
                name: loginData.name,
                phone: formatPhoneDisplay(phone)
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
    const phoneRaw = document.getElementById('registerPhone').value;
    const phone = normalizePhone(phoneRaw);
    const password = document.getElementById('registerPassword').value;
    const btn = document.getElementById('registerBtn');

    // phone = +380XXXXXXXXX (13 символів)
    if (phone.length !== 13) {
        showToast('Невірний формат телефону', 'error');
        return;
    }

    if (password.length < 6) {
        showToast('Пароль має бути мінімум 6 символів', 'error');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Реєстрація...';

    try {
        const response = await apiRequest('/pwa/register', 'POST', {
            name: name,
            phone: phone,
            password: password
        });

        if (response.success) {
            // Store phone for verification
            localStorage.setItem('zvit_pending_phone', phone);
            localStorage.setItem('zvit_pending_password', password);
            localStorage.setItem('zvit_pending_name', name);

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
    const name = localStorage.getItem('zvit_pending_name');

    try {
        // Try to login after verification
        const response = await apiRequest('/pwa/login', 'POST', {
            phone: phone,
            password: password,
            verificationCode: code
        });

        if (response.success && response.data) {
            localStorage.removeItem('zvit_pending_phone');
            localStorage.removeItem('zvit_pending_password');
            localStorage.removeItem('zvit_pending_name');

            const loginData = response.data;
            localStorage.setItem('zvit_token', loginData.token);
            localStorage.setItem('zvit_user', JSON.stringify({
                id: loginData.userId,
                name: loginData.name || name,
                phone: formatPhoneDisplay(phone)
            }));

            currentUser = {
                id: loginData.userId,
                name: loginData.name || name,
                phone: formatPhoneDisplay(phone)
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

// Profile Editing
function showEditNameDialog() {
    const currentName = currentUser?.name || '';
    document.getElementById('editNameInput').value = currentName;
    document.getElementById('editNameModal').classList.add('active');
}

function showEditEmailDialog() {
    const currentEmail = currentUser?.email || '';
    document.getElementById('editEmailInput').value = currentEmail;
    document.getElementById('editEmailModal').classList.add('active');
}

async function saveProfileName() {
    const name = document.getElementById('editNameInput').value.trim();

    if (!name) {
        showToast('Введіть ім\'я', 'error');
        return;
    }

    if (name.length < 2) {
        showToast('Ім\'я має бути мінімум 2 символи', 'error');
        return;
    }

    try {
        const response = await apiRequest('/pwa/profile', 'PUT', { name: name });

        if (response.success) {
            currentUser.name = name;
            localStorage.setItem('zvit_user', JSON.stringify(currentUser));
            document.getElementById('profileName').textContent = name;
            closeModal('editNameModal');
            showToast('Ім\'я оновлено', 'success');
        } else {
            showToast(response.message || 'Помилка оновлення', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка оновлення', 'error');
    }
}

async function saveProfileEmail() {
    const email = document.getElementById('editEmailInput').value.trim();

    if (email && !isValidEmail(email)) {
        showToast('Невірний формат email', 'error');
        return;
    }

    try {
        const response = await apiRequest('/pwa/profile', 'PUT', { email: email || null });

        if (response.success) {
            currentUser.email = email || null;
            localStorage.setItem('zvit_user', JSON.stringify(currentUser));
            document.getElementById('profileEmail').textContent = email || 'Не вказано';
            closeModal('editEmailModal');
            showToast('Email оновлено', 'success');
        } else {
            showToast(response.message || 'Помилка оновлення', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка оновлення', 'error');
    }
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// Groups
async function loadGroups() {
    const container = document.getElementById('groupsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        console.log('[PWA] Loading groups...');
        console.log('[PWA] Token:', localStorage.getItem('zvit_token') ? 'present' : 'missing');

        const response = await apiRequest('/pwa/groups', 'GET');

        console.log('[PWA] Groups response:', response);

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
        console.error('[PWA] Groups loading error:', error);
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження груп</p>
                <p style="font-size: 12px; margin-top: 5px; opacity: 0.7;">${error.message || 'Невідома помилка'}</p>
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

    container.innerHTML = groups.map(group => {
        const name = group.externalName || group.name || 'Група';
        const id = group.groupId || group.id;
        const members = group.currentMembers || group.memberCount || 0;
        return `
        <div class="card group-card" onclick="openGroup('${id}', '${escapeHtml(name)}')">
            <div class="group-icon">${name.charAt(0).toUpperCase()}</div>
            <div class="group-info">
                <div class="group-name">${escapeHtml(name)}</div>
                <div class="group-members">${members} учасників</div>
            </div>
            ${group.pendingReports > 0 ? `<div class="group-badge">${group.pendingReports}</div>` : ''}
        </div>
    `}).join('');
}

async function openGroup(groupId, groupName) {
    currentGroup = { id: groupId, name: groupName };
    document.getElementById('groupTitle').textContent = groupName;
    showScreen('groupScreen');
    loadReports(groupId);
}

// Group Actions Menu
function showGroupActionsMenu() {
    document.getElementById('actionMenu').classList.add('active');
    document.getElementById('actionMenuOverlay').classList.add('active');
}

function hideGroupActionsMenu() {
    document.getElementById('actionMenu').classList.remove('active');
    document.getElementById('actionMenuOverlay').classList.remove('active');
}

// Create Group
function showCreateGroupDialog() {
    hideGroupActionsMenu();
    document.getElementById('newGroupName').value = '';
    document.querySelector('input[name="reportType"][value="SIMPLE"]').checked = true;
    document.getElementById('createGroupModal').classList.add('active');
}

async function createGroup() {
    const name = document.getElementById('newGroupName').value.trim();
    const reportType = document.querySelector('input[name="reportType"]:checked').value;

    if (!name) {
        showToast('Введіть назву групи', 'error');
        return;
    }

    if (name.length < 3) {
        showToast('Назва групи має бути мінімум 3 символи', 'error');
        return;
    }

    try {
        const response = await apiRequest('/pwa/groups', 'POST', {
            externalName: name,
            reportType: reportType,
            maxMembers: 50
        });

        if (response.success) {
            closeModal('createGroupModal');
            showToast('Групу створено!', 'success');
            loadGroups();
        } else {
            showToast(response.message || 'Помилка створення групи', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка створення групи', 'error');
    }
}

// Join Group
function showJoinGroupDialog() {
    hideGroupActionsMenu();
    document.getElementById('accessCode').value = '';
    document.getElementById('joinGroupModal').classList.add('active');
}

async function joinGroup() {
    const accessCode = document.getElementById('accessCode').value.trim().toUpperCase();

    if (!accessCode) {
        showToast('Введіть код доступу', 'error');
        return;
    }

    try {
        const response = await apiRequest('/pwa/groups/join', 'POST', {
            accessCode: accessCode
        });

        if (response.success) {
            closeModal('joinGroupModal');
            showToast('Заявку відправлено!', 'success');
            loadGroups();
        } else {
            showToast(response.message || 'Помилка приєднання', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Невірний код або групу не знайдено', 'error');
    }
}

// Modal helpers
function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

// Leave Group
function showLeaveGroupConfirm() {
    if (!currentGroup) return;
    document.getElementById('leaveGroupName').textContent = currentGroup.name;
    document.getElementById('leaveGroupModal').classList.add('active');
}

async function confirmLeaveGroup() {
    if (!currentGroup) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/leave`, 'DELETE');

        if (response.success) {
            closeModal('leaveGroupModal');
            showToast('Ви вийшли з групи', 'success');
            currentGroup = null;
            showScreen('mainScreen');
            loadGroups();
        } else {
            showToast(response.message || 'Помилка виходу з групи', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка виходу з групи', 'error');
    }
}

// Reports
async function loadReports(groupId) {
    const container = document.getElementById('reportsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        console.log('[PWA] Loading reports for group:', groupId);
        const response = await apiRequest(`/pwa/groups/${groupId}/reports`, 'GET');
        console.log('[PWA] Reports response:', response);

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
        console.error('[PWA] Reports loading error:', error);
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження звітів</p>
                <p style="font-size: 12px; margin-top: 5px; opacity: 0.7;">${error.message || 'Невідома помилка'}</p>
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
                <p>Ви ще не надсилали звітів</p>
                <p style="font-size: 14px; opacity: 0.7; margin-top: 8px;">Натисніть + щоб надіслати звіт</p>
            </div>
        `;
        return;
    }

    container.innerHTML = `<div class="card">${reports.map(report => {
        const userName = report.userName || 'Невідомий';
        const response = report.simpleResponse || report.response || '';
        const time = report.submittedAt || report.createdAt;
        const isUrgent = response.toUpperCase().includes('ТЕРМІНОВ') || response.toUpperCase() === 'НЕ ОК';
        return `
        <div class="report-item">
            <div class="report-avatar">${userName.charAt(0).toUpperCase()}</div>
            <div class="report-content">
                <div class="report-header">
                    <span class="report-name">${escapeHtml(userName)}</span>
                    <span class="report-time">${formatTime(time)}</span>
                </div>
                <span class="report-status ${isUrgent ? 'urgent' : 'ok'}">${escapeHtml(response)}</span>
                ${report.comment ? `<div class="report-comment">${escapeHtml(report.comment)}</div>` : ''}
            </div>
        </div>
    `}).join('')}</div>`;
}

// My Reports (all user's reports)
async function loadMyReports() {
    const container = document.getElementById('myReportsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        console.log('[PWA] Loading my reports...');
        const response = await apiRequest('/pwa/reports', 'GET');
        console.log('[PWA] My reports response:', response);

        if (response.success && response.data && response.data.length > 0) {
            renderMyReports(response.data);
        } else {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
                    </svg>
                    <p>Ви ще не надсилали звітів</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('[PWA] My reports loading error:', error);
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження звітів</p>
                <p style="font-size: 12px; margin-top: 5px; opacity: 0.7;">${error.message || 'Невідома помилка'}</p>
                <button class="btn btn-secondary" style="margin-top: 10px;" onclick="loadMyReports()">Спробувати знову</button>
            </div>
        `;
    }
}

function renderMyReports(reports) {
    const container = document.getElementById('myReportsList');

    // Group reports by groupName
    const groupedReports = {};
    reports.forEach(report => {
        const groupName = report.groupName || 'Невідома група';
        if (!groupedReports[groupName]) {
            groupedReports[groupName] = [];
        }
        groupedReports[groupName].push(report);
    });

    let html = '';
    for (const [groupName, groupReports] of Object.entries(groupedReports)) {
        html += `<div class="card" style="margin-bottom: 16px;">
            <h3 style="margin-bottom: 12px; color: var(--primary);">${escapeHtml(groupName)}</h3>
            ${groupReports.map(report => {
                const response = report.simpleResponse || report.response || '';
                const time = report.submittedAt || report.createdAt;
                const isUrgent = response.toUpperCase().includes('ТЕРМІНОВ') || response.toUpperCase() === 'НЕ ОК' || response.toUpperCase() === 'NOT_OK';
                return `
                <div class="report-item">
                    <div class="report-content" style="width: 100%;">
                        <div class="report-header">
                            <span class="report-status ${isUrgent ? 'urgent' : 'ok'}">${escapeHtml(response)}</span>
                            <span class="report-time">${formatTime(time)}</span>
                        </div>
                        ${report.comment ? `<div class="report-comment">${escapeHtml(report.comment)}</div>` : ''}
                    </div>
                </div>
            `}).join('')}
        </div>`;
    }

    container.innerHTML = html;
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
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/reports/simple`, 'POST', {
            groupId: currentGroup.id,
            simpleResponse: selectedReportResponse,
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

    console.log(`[API] ${method} ${API_BASE}${endpoint}`);

    const response = await fetch(`${API_BASE}${endpoint}`, options);

    console.log(`[API] Response status: ${response.status}`);

    // Handle 401 - redirect to login
    if (response.status === 401) {
        console.log('[API] Unauthorized - logging out');
        logout();
        throw new Error('Сесія закінчилась');
    }

    const data = await response.json();
    console.log('[API] Response data:', data);

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
