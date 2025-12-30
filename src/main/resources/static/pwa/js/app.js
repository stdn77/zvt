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

        // Show install modal after login
        if (currentUser && !sessionStorage.getItem('zvit_install_dismissed')) {
            document.getElementById('installModal').classList.add('show');
        }
    });

    window.addEventListener('appinstalled', () => {
        console.log('PWA installed');
        document.getElementById('installModal').classList.remove('show');
        deferredPrompt = null;
    });
}

async function installPWA() {
    if (!deferredPrompt) {
        // iOS Safari
        showToast('Натисніть "Поділитися" → "На Початковий екран"', 'info');
        document.getElementById('installModal').classList.remove('show');
        return;
    }

    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    console.log('Install outcome:', outcome);
    deferredPrompt = null;
    document.getElementById('installModal').classList.remove('show');
}

function dismissInstallModal(event) {
    // Закрити тільки при кліку на overlay (не на modal)
    if (event.target.id === 'installModal') {
        document.getElementById('installModal').classList.remove('show');
        // Зберігаємо в sessionStorage - закрито до наступного відкриття екрану
        sessionStorage.setItem('zvit_install_dismissed', 'true');
    }
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
        loadReportsScreen();
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
    loadReportsScreen();

    // Update settings
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
        document.getElementById('profileEmail').textContent = currentUser.email || 'Не вказано';
    }

    // Check notifications permission
    updateNotificationsToggle();

    // Show install modal if available (reset dismissed state on screen change)
    sessionStorage.removeItem('zvit_install_dismissed');
    if (deferredPrompt) {
        setTimeout(() => {
            document.getElementById('installModal').classList.add('show');
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

    // Завантажуємо деталі групи (функція сама вирішить що показати - учасників чи звіти)
    await loadGroupDetails(groupId);
}

async function loadGroupDetails(groupId) {
    try {
        console.log('[PWA] Loading group details:', groupId);
        const response = await apiRequest(`/pwa/groups/${groupId}`, 'GET');
        console.log('[PWA] Group details:', response);

        if (response.success && response.data) {
            const group = response.data;
            currentGroup = {
                id: group.groupId || groupId,
                name: group.externalName || currentGroup.name,
                isAdmin: group.userRole === 'ADMIN',
                accessCode: group.accessCode,
                reportType: group.reportType,
                membersCount: group.currentMembers || 0,
                maxMembers: group.maxMembers || 10,
                scheduleType: group.scheduleType,
                fixedTimes: group.fixedTimes,
                intervalMinutes: group.intervalMinutes,
                intervalStartTime: group.intervalStartTime,
                positiveWord: group.positiveWord || 'ОК',
                negativeWord: group.negativeWord || 'НЕ ОК'
            };

            // Показуємо/ховаємо адмін-панель та вид для членів
            const adminInfo = document.getElementById('groupAdminInfo');
            const memberView = document.getElementById('groupMemberView');
            const settingsBtn = document.getElementById('groupSettingsBtn');

            if (currentGroup.isAdmin) {
                // Адмін бачить учасників
                adminInfo.style.display = 'block';
                memberView.style.display = 'none';
                settingsBtn.style.display = 'flex';

                // Оновлюємо інформацію
                document.getElementById('groupMembersInfo').textContent =
                    `${group.currentMembers || 0}/${group.maxMembers || 10}`;
                document.getElementById('groupReportType').textContent =
                    group.reportType === 'SIMPLE' ? 'Простий' : 'Розширений';
                document.getElementById('groupAccessCode').textContent = group.accessCode || '-';

                // Розклад
                const scheduleRow = document.getElementById('groupScheduleRow');
                if (group.scheduleType && (group.fixedTimes || group.intervalMinutes)) {
                    scheduleRow.style.display = 'flex';
                    if (group.scheduleType === 'FIXED_TIMES' && group.fixedTimes) {
                        document.getElementById('groupSchedule').textContent = 'о ' + group.fixedTimes.join('; ');
                    } else if (group.scheduleType === 'INTERVAL') {
                        const hours = Math.floor(group.intervalMinutes / 60);
                        const mins = group.intervalMinutes % 60;
                        const interval = hours > 0 ? `${hours} год${mins > 0 ? ` ${mins} хв` : ''}` : `${mins} хв`;
                        document.getElementById('groupSchedule').textContent = `з ${group.intervalStartTime}, кожні ${interval}`;
                    }
                } else {
                    scheduleRow.style.display = 'none';
                }

                // Слова звіту (для простих)
                const wordsRow = document.getElementById('groupWordsRow');
                if (group.reportType === 'SIMPLE') {
                    wordsRow.style.display = 'flex';
                    document.getElementById('groupWords').textContent =
                        `${group.positiveWord || 'ОК'} / ${group.negativeWord || 'НЕ ОК'}`;
                } else {
                    wordsRow.style.display = 'none';
                }

                // Завантажуємо учасників
                loadGroupMembersForAdmin();
            } else {
                // Член групи бачить свої звіти
                adminInfo.style.display = 'none';
                memberView.style.display = 'block';
                settingsBtn.style.display = 'none';

                // Завантажуємо звіти
                loadReports(groupId);
            }
        }
    } catch (error) {
        console.error('[PWA] Error loading group details:', error);
    }
}

function refreshGroupReports() {
    if (currentGroup && currentGroup.id) {
        loadReports(currentGroup.id);
    }
}

function copyAccessCode() {
    if (currentGroup && currentGroup.accessCode) {
        navigator.clipboard.writeText(currentGroup.accessCode).then(() => {
            showToast('Код скопійовано!', 'success');
        }).catch(() => {
            showToast('Не вдалося скопіювати', 'error');
        });
    }
}

// Додати учасника
function showAddMemberDialog() {
    if (!currentGroup) return;
    document.getElementById('addMemberCode').textContent = currentGroup.accessCode || '-';
    document.getElementById('addMemberModal').classList.add('active');
}

function copyAccessCodeFromModal() {
    copyAccessCode();
    closeModal('addMemberModal');
}

function shareAccessCode() {
    if (!currentGroup || !currentGroup.accessCode) return;

    const shareData = {
        title: 'ZVIT - Код групи',
        text: `Приєднуйтесь до групи "${currentGroup.name}" в ZVIT!\nКод: ${currentGroup.accessCode}`,
        url: window.location.origin + '/app'
    };

    if (navigator.share) {
        navigator.share(shareData).catch(() => {
            copyAccessCode();
        });
    } else {
        copyAccessCode();
    }
    closeModal('addMemberModal');
}

// Період звітування
function showScheduleDialog() {
    if (!currentGroup) return;

    // Встановлюємо поточні значення
    const isInterval = currentGroup.scheduleType === 'INTERVAL';
    document.getElementById('scheduleFixed').checked = !isInterval;
    document.getElementById('scheduleInterval').checked = isInterval;

    toggleScheduleType();

    // Заповнюємо значення
    if (currentGroup.fixedTimes) {
        const times = currentGroup.fixedTimes;
        if (times[0]) document.getElementById('fixedTime1').value = times[0];
        if (times[1]) document.getElementById('fixedTime2').value = times[1];
        if (times[2]) document.getElementById('fixedTime3').value = times[2];
    }

    if (currentGroup.intervalStartTime) {
        document.getElementById('intervalStart').value = currentGroup.intervalStartTime;
    }
    if (currentGroup.intervalMinutes) {
        document.getElementById('intervalMinutes').value = currentGroup.intervalMinutes;
    }

    document.getElementById('scheduleModal').classList.add('active');
}

function toggleScheduleType() {
    const isInterval = document.getElementById('scheduleInterval').checked;
    document.getElementById('fixedTimesSection').style.display = isInterval ? 'none' : 'block';
    document.getElementById('intervalSection').style.display = isInterval ? 'block' : 'none';
}

// Слухачі для перемикання типу розкладу
document.addEventListener('DOMContentLoaded', () => {
    const scheduleFixed = document.getElementById('scheduleFixed');
    const scheduleInterval = document.getElementById('scheduleInterval');
    if (scheduleFixed) scheduleFixed.addEventListener('change', toggleScheduleType);
    if (scheduleInterval) scheduleInterval.addEventListener('change', toggleScheduleType);
});

async function saveSchedule() {
    if (!currentGroup) return;

    const isInterval = document.getElementById('scheduleInterval').checked;

    const request = {
        scheduleType: isInterval ? 'INTERVAL' : 'FIXED_TIMES'
    };

    if (isInterval) {
        request.intervalStartTime = document.getElementById('intervalStart').value;
        request.intervalMinutes = parseInt(document.getElementById('intervalMinutes').value);
    } else {
        const times = [];
        const t1 = document.getElementById('fixedTime1').value;
        const t2 = document.getElementById('fixedTime2').value;
        const t3 = document.getElementById('fixedTime3').value;
        if (t1) times.push(t1);
        if (t2) times.push(t2);
        if (t3) times.push(t3);
        request.fixedTimes = times;
    }

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}`, 'PUT', request);

        if (response.success) {
            closeModal('scheduleModal');
            showToast('Розклад оновлено', 'success');
            loadGroupDetails(currentGroup.id);
        } else {
            showToast(response.message || 'Помилка збереження', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка збереження', 'error');
    }
}

function showGroupSettings() {
    if (!currentGroup || !currentGroup.isAdmin) return;

    // Оновлюємо назву в модальному вікні
    document.getElementById('settingsGroupName').textContent = currentGroup.name || '-';

    document.getElementById('groupSettingsModal').classList.add('active');
}

function showChangeReportTypeDialog() {
    if (!currentGroup || !currentGroup.isAdmin) return;
    showChangeReportTypeModal();
}

function showChangeReportTypeModal() {
    if (!currentGroup) return;

    // Set current type
    const isSimple = currentGroup.reportType === 'SIMPLE';
    document.getElementById('reportTypeSimple').checked = isSimple;
    document.getElementById('reportTypeExtended').checked = !isSimple;

    closeModal('groupSettingsModal');
    document.getElementById('changeReportTypeModal').classList.add('active');
}

async function saveReportType() {
    if (!currentGroup) return;

    const newType = document.querySelector('input[name="newReportType"]:checked').value;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}`, 'PUT', {
            reportType: newType
        });

        if (response.success) {
            currentGroup.reportType = newType;
            const typeText = newType === 'SIMPLE' ? 'Простий' : 'Розширений';

            document.getElementById('groupReportType').textContent = typeText;

            closeModal('changeReportTypeModal');
            showToast('Тип звіту змінено', 'success');
        } else {
            showToast(response.message || 'Помилка зміни типу', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка зміни типу', 'error');
    }
}

function showEditGroupNameDialog() {
    if (!currentGroup) return;
    document.getElementById('editGroupNameInput').value = currentGroup.name || '';
    closeModal('groupSettingsModal');
    document.getElementById('editGroupNameModal').classList.add('active');
}

async function saveGroupName() {
    if (!currentGroup) return;

    const newName = document.getElementById('editGroupNameInput').value.trim();

    if (!newName || newName.length < 3) {
        showToast('Назва має бути мінімум 3 символи', 'error');
        return;
    }

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}`, 'PUT', {
            externalName: newName
        });

        if (response.success) {
            currentGroup.name = newName;
            document.getElementById('groupTitle').textContent = newName;
            document.getElementById('settingsGroupName').textContent = newName;

            closeModal('editGroupNameModal');
            showToast('Назву групи змінено', 'success');

            // Reload groups list
            loadGroups();
        } else {
            showToast(response.message || 'Помилка зміни назви', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка зміни назви', 'error');
    }
}

async function regenerateAccessCode() {
    if (!currentGroup) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/regenerate-code`, 'POST');

        if (response.success && response.data) {
            const newCode = response.data.accessCode || response.data;
            currentGroup.accessCode = newCode;

            document.getElementById('groupAccessCode').textContent = newCode;

            closeModal('groupSettingsModal');
            showToast('Код доступу змінено', 'success');
        } else {
            showToast(response.message || 'Помилка зміни коду', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка зміни коду', 'error');
    }
}

function showDeleteGroupConfirm() {
    closeModal('groupSettingsModal');
    document.getElementById('deleteGroupModal').classList.add('active');
}

async function confirmDeleteGroup() {
    if (!currentGroup) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}`, 'DELETE');

        if (response.success) {
            closeModal('deleteGroupModal');
            showToast('Групу видалено', 'success');
            currentGroup = null;
            navigateTo('mainScreen');
            loadGroups();
        } else {
            showToast(response.message || 'Помилка видалення групи', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка видалення групи', 'error');
    }
}

async function loadGroupMembersForAdmin() {
    const container = document.getElementById('adminMembersList');
    container.innerHTML = '<div class="loading" style="padding: 20px;"><div class="spinner" style="width: 24px; height: 24px;"></div></div>';

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/members`, 'GET');

        if (response.success && response.data) {
            // Зберігаємо кількість адмінів для перевірки
            currentGroup.adminCount = response.data.filter(m => m.role === 'ADMIN' && m.status === 'ACCEPTED').length;
            renderMembersForAdmin(response.data);
            // Оновлюємо лічильник у інфо-блоці
            const membersInfo = document.getElementById('groupMembersInfo');
            if (membersInfo) {
                membersInfo.textContent = `${response.data.length}/${currentGroup.maxMembers || 10}`;
            }
        } else {
            container.innerHTML = '<p style="color: var(--text-secondary); text-align: center; padding: 20px;">Не вдалося завантажити учасників</p>';
        }
    } catch (error) {
        container.innerHTML = '<p style="color: var(--text-secondary); text-align: center; padding: 20px;">Помилка завантаження</p>';
    }
}

function loadGroupMembers() {
    loadGroupMembersForAdmin();
}

function renderMembersForAdmin(members) {
    const container = document.getElementById('adminMembersList');

    if (!members || members.length === 0) {
        container.innerHTML = '<p style="color: var(--text-secondary); text-align: center; padding: 20px;">Немає учасників</p>';
        return;
    }

    container.innerHTML = `<div class="card">${members.map(member => {
        const name = member.name || member.userName || 'Невідомий';
        const phone = member.phoneNumber || '';
        const role = member.role || 'MEMBER';
        const isAdmin = role === 'ADMIN';
        const isPending = member.status === 'PENDING';
        const memberId = member.id || member.userId;

        return `
            <div class="report-item" style="align-items: center;">
                <div class="report-avatar" style="width: 40px; height: 40px;">${name.charAt(0).toUpperCase()}</div>
                <div class="report-content" style="flex: 1;">
                    <div class="report-header" style="flex-wrap: wrap; gap: 4px;">
                        <span class="report-name">${escapeHtml(name)}</span>
                        <span onclick="showRoleChangeDialog('${memberId}', '${role}')" style="font-size: 12px; padding: 2px 8px; border-radius: 10px; background: ${isAdmin ? 'var(--primary)' : isPending ? 'var(--warning)' : 'rgba(255,255,255,0.1)'}; color: ${isAdmin || isPending ? 'white' : 'var(--text-secondary)'}; cursor: ${isPending ? 'default' : 'pointer'};">
                            ${isAdmin ? 'Адмін' : isPending ? 'Очікує' : 'Учасник'}
                        </span>
                    </div>
                    ${phone && !isPending ? `
                        <div onclick="showContactOptions('${phone}')" style="font-size: 13px; color: var(--primary); cursor: pointer; margin-top: 2px;">
                            ${escapeHtml(phone)}
                        </div>
                    ` : ''}
                </div>
                ${!isAdmin && !isPending ? `
                    <button onclick="removeMember('${memberId}')" style="background: none; border: none; color: var(--danger); padding: 8px; cursor: pointer;">
                        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                        </svg>
                    </button>
                ` : ''}
                ${isPending ? `
                    <div style="display: flex; gap: 8px;">
                        <button onclick="approveMember('${memberId}')" style="background: var(--success); border: none; color: white; padding: 8px 12px; border-radius: 8px; cursor: pointer;">✓</button>
                        <button onclick="rejectMember('${memberId}')" style="background: var(--danger); border: none; color: white; padding: 8px 12px; border-radius: 8px; cursor: pointer;">✕</button>
                    </div>
                ` : ''}
            </div>
        `;
    }).join('')}</div>`;
}

// Показати опції зв'язку
function showContactOptions(phone) {
    const cleanPhone = phone.replace(/[^\d+]/g, '');

    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.id = 'contactOptionsModal';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h2>Зв'язатися</h2>
                <button class="modal-close" onclick="closeModal('contactOptionsModal')">&times;</button>
            </div>
            <p style="color: var(--text-secondary); margin-bottom: 16px;">${escapeHtml(phone)}</p>
            <div style="display: flex; flex-direction: column; gap: 12px;">
                <button class="btn btn-secondary" onclick="callPhone('${cleanPhone}')">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style="margin-right: 8px;">
                        <path d="M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"/>
                    </svg>
                    Зателефонувати
                </button>
                <button class="btn btn-secondary" onclick="openSignal('${cleanPhone}')">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style="margin-right: 8px;">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                    </svg>
                    Signal
                </button>
                <button class="btn btn-secondary" onclick="openWhatsApp('${cleanPhone}')">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style="margin-right: 8px;">
                        <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347z"/>
                    </svg>
                    WhatsApp
                </button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function callPhone(phone) {
    window.location.href = 'tel:' + phone;
    closeModal('contactOptionsModal');
}

function openSignal(phone) {
    window.open('https://signal.me/#p/' + phone, '_blank');
    closeModal('contactOptionsModal');
}

function openWhatsApp(phone) {
    window.open('https://wa.me/' + phone.replace('+', ''), '_blank');
    closeModal('contactOptionsModal');
}

// Показати діалог зміни ролі
function showRoleChangeDialog(memberId, currentRole) {
    if (currentRole === 'PENDING') return;

    const isAdmin = currentRole === 'ADMIN';
    const adminCount = currentGroup.adminCount || 1;

    // Якщо це єдиний адмін - не можна змінити роль
    if (isAdmin && adminCount <= 1) {
        showToast('Неможливо змінити роль єдиного адміністратора', 'error');
        return;
    }

    const newRole = isAdmin ? 'MEMBER' : 'ADMIN';
    const newRoleText = isAdmin ? 'Учасника' : 'Адміністратора';

    if (confirm(`Змінити роль на "${newRoleText}"?`)) {
        changeMemberRole(memberId, newRole);
    }
}

async function changeMemberRole(memberId, newRole) {
    try {
        const response = await apiRequest(`/groups/${currentGroup.id}/members/${memberId}/role`, 'PUT', { role: newRole });

        if (response.success) {
            showToast('Роль змінено', 'success');
            loadGroupMembers();
        } else {
            showToast(response.message || 'Помилка зміни ролі', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка зміни ролі', 'error');
    }
}

async function removeMember(memberId) {
    if (!currentGroup || !memberId) return;

    if (!confirm('Видалити учасника з групи?')) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/members/${memberId}`, 'DELETE');

        if (response.success) {
            showToast('Учасника видалено', 'success');
            loadGroupMembers();
            loadGroupDetails(currentGroup.id);
        } else {
            showToast(response.message || 'Помилка видалення', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка видалення', 'error');
    }
}

async function approveMember(memberId) {
    if (!currentGroup || !memberId) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/members/${memberId}/approve`, 'POST');

        if (response.success) {
            showToast('Учасника схвалено', 'success');
            loadGroupMembers();
            loadGroupDetails(currentGroup.id);
        } else {
            showToast(response.message || 'Помилка схвалення', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка схвалення', 'error');
    }
}

async function rejectMember(memberId) {
    if (!currentGroup || !memberId) return;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/members/${memberId}/reject`, 'POST');

        if (response.success) {
            showToast('Заявку відхилено', 'success');
            loadGroupMembers();
        } else {
            showToast(response.message || 'Помилка відхилення', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка відхилення', 'error');
    }
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
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('active');
        // Видаляємо динамічно створені модалки
        if (modalId === 'contactOptionsModal') {
            setTimeout(() => modal.remove(), 300);
        }
    }
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

// Reports Screen - показує список груп для звітування (як в Android)
async function loadReportsScreen() {
    const container = document.getElementById('reportGroupsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        console.log('[PWA] Loading groups for reporting...');
        const response = await apiRequest('/pwa/groups', 'GET');
        console.log('[PWA] Groups for reporting:', response);

        if (response.success && response.data && response.data.length > 0) {
            renderReportGroups(response.data);
        } else {
            container.innerHTML = `
                <div class="empty-state">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                    </svg>
                    <p>У вас немає груп для звітування</p>
                    <p style="font-size: 12px; margin-top: 8px; opacity: 0.7;">Приєднайтесь до групи або створіть нову</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('[PWA] Groups loading error:', error);
        container.innerHTML = `
            <div class="card" style="text-align: center; color: var(--danger);">
                <p>Помилка завантаження груп</p>
                <p style="font-size: 12px; margin-top: 5px; opacity: 0.7;">${error.message || 'Невідома помилка'}</p>
                <button class="btn btn-secondary" style="margin-top: 10px;" onclick="loadReportsScreen()">Спробувати знову</button>
            </div>
        `;
    }
}

function renderReportGroups(groups) {
    const container = document.getElementById('reportGroupsList');

    // Розділяємо на групи де адмін і де учасник
    const adminGroups = groups.filter(g => g.isAdmin);
    const memberGroups = groups.filter(g => !g.isAdmin);

    let html = '';

    // Групи де учасник (можна звітувати)
    if (memberGroups.length > 0) {
        html += `<div class="section-title" style="padding: 0 0 8px 0; color: var(--text-secondary); font-size: 12px; text-transform: uppercase;">Мої групи</div>`;
        memberGroups.forEach(group => {
            html += renderReportGroupCard(group);
        });
    }

    // Групи де адмін
    if (adminGroups.length > 0) {
        html += `<div class="section-title" style="padding: 16px 0 8px 0; color: var(--text-secondary); font-size: 12px; text-transform: uppercase;">Адміністрування</div>`;
        adminGroups.forEach(group => {
            html += renderReportGroupCard(group, true);
        });
    }

    container.innerHTML = html;
}

function renderReportGroupCard(group, isAdminSection = false) {
    const membersCount = group.membersCount || 0;
    const reportType = group.reportType === 'EXTENDED' ? 'Розширений' : 'Простий';

    return `
        <div class="card report-group-card" style="margin-bottom: 12px; cursor: pointer;" onclick="openReportForGroup('${group.id}', '${escapeHtml(group.name)}', '${group.reportType}', ${group.isAdmin})">
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                <div style="flex: 1;">
                    <h3 style="margin: 0 0 4px 0; font-size: 16px;">${escapeHtml(group.name)}</h3>
                    <div style="font-size: 13px; color: var(--text-secondary);">
                        ${membersCount} учасник${getPlural(membersCount, '', 'и', 'ів')} · ${reportType}
                    </div>
                </div>
                <button class="btn btn-primary" style="padding: 8px 16px; font-size: 14px;" onclick="event.stopPropagation(); openReportForGroup('${group.id}', '${escapeHtml(group.name)}', '${group.reportType}', ${group.isAdmin})">
                    ${isAdminSection ? 'Переглянути' : 'Звітувати'}
                </button>
            </div>
        </div>
    `;
}

function getPlural(n, one, few, many) {
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return one;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
    return many;
}

function openReportForGroup(groupId, groupName, reportType, isAdmin) {
    // Зберігаємо вибрану групу для звіту
    currentGroup = { id: groupId, name: groupName, reportType: reportType, isAdmin: isAdmin };

    if (isAdmin) {
        // Для адміна - переходимо до деталей групи
        showScreen('groupScreen');
        loadGroupDetails(groupId);
    } else {
        // Для учасника - відкриваємо модальне вікно звіту
        document.getElementById('reportModalTitle').textContent = groupName;
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

// Pull-to-Refresh
let pullStartY = 0;
let pullMoveY = 0;
let isPulling = false;
let pullToRefreshEnabled = true;

function initPullToRefresh() {
    const screens = ['mainScreen', 'reportsScreen', 'groupScreen'];

    screens.forEach(screenId => {
        const screen = document.getElementById(screenId);
        if (!screen) return;

        screen.addEventListener('touchstart', (e) => {
            if (window.scrollY === 0 && pullToRefreshEnabled) {
                pullStartY = e.touches[0].clientY;
                isPulling = true;
            }
        }, { passive: true });

        screen.addEventListener('touchmove', (e) => {
            if (!isPulling) return;

            pullMoveY = e.touches[0].clientY;
            const pullDistance = pullMoveY - pullStartY;

            if (pullDistance > 0 && pullDistance < 150) {
                // Show visual feedback
                const indicator = screen.querySelector('.ptr-indicator');
                if (indicator) {
                    indicator.style.transform = `translateX(-50%) translateY(${pullDistance / 2}px)`;
                    indicator.style.opacity = Math.min(pullDistance / 80, 1);
                }
            }
        }, { passive: true });

        screen.addEventListener('touchend', (e) => {
            if (!isPulling) return;

            const pullDistance = pullMoveY - pullStartY;

            if (pullDistance > 80) {
                // Trigger refresh
                triggerRefresh(screenId);
            }

            // Reset indicator
            const indicator = screen.querySelector('.ptr-indicator');
            if (indicator) {
                indicator.style.transform = 'translateX(-50%)';
                indicator.style.opacity = '0';
            }

            isPulling = false;
            pullStartY = 0;
            pullMoveY = 0;
        }, { passive: true });
    });
}

function triggerRefresh(screenId) {
    showToast('Оновлення...', 'info');

    if (screenId === 'mainScreen') {
        loadGroups();
    } else if (screenId === 'reportsScreen') {
        loadReportsScreen();
    } else if (screenId === 'groupScreen' && currentGroup) {
        loadGroupDetails(currentGroup.id);
        loadReports(currentGroup.id);
    }
}

// Initialize pull-to-refresh when app starts
document.addEventListener('DOMContentLoaded', () => {
    setTimeout(initPullToRefresh, 500);
});
