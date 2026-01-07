// ZVIT PWA Application

// State
let currentUser = null;
let currentGroup = null;
let selectedReportResponse = 'OK';
let deferredPrompt = null;

// Navigation history for back button
let navigationStack = ['reportsScreen'];
let lastBackPressTime = 0;

// Phone verification state
let phoneVerificationId = null;
let phoneVerificationPhone = null;
let phoneVerificationTimer = null;
let recaptchaVerifier = null;

// Device detection
function isIOS() {
    return /iPad|iPhone|iPod/.test(navigator.userAgent) ||
           (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

function isStandalonePWA() {
    return window.matchMedia('(display-mode: standalone)').matches ||
           window.navigator.standalone === true;
}

// Cookie helpers for iOS PWA persistence
function setCookie(name, value, days = 365) {
    const date = new Date();
    date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
    const expires = "expires=" + date.toUTCString();
    document.cookie = name + "=" + encodeURIComponent(value) + ";" + expires + ";path=/;SameSite=Lax";
}

function getCookie(name) {
    const nameEQ = name + "=";
    const ca = document.cookie.split(';');
    for (let i = 0; i < ca.length; i++) {
        let c = ca[i].trim();
        if (c.indexOf(nameEQ) === 0) {
            return decodeURIComponent(c.substring(nameEQ.length));
        }
    }
    return null;
}

function deleteCookie(name) {
    document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;";
}

// Urgent reports storage
function getUrgentReportsFromStorage() {
    try {
        const data = localStorage.getItem('zvit_urgent_reports');
        return data ? JSON.parse(data) : {};
    } catch (e) {
        return {};
    }
}

function saveUrgentReportsToStorage(reports) {
    localStorage.setItem('zvit_urgent_reports', JSON.stringify(reports));
}

function setUrgentReportForGroup(groupId, deadline, message) {
    const reports = getUrgentReportsFromStorage();
    reports[groupId] = {
        deadline: deadline,
        message: message,
        receivedAt: new Date().toISOString()
    };
    saveUrgentReportsToStorage(reports);
}

function getUrgentReportForGroup(groupId) {
    const reports = getUrgentReportsFromStorage();
    const report = reports[groupId];

    if (!report) return null;

    // Check if deadline passed
    const deadline = parseServerDate(report.deadline);
    if (!deadline || deadline < new Date()) {
        // Deadline passed, remove
        delete reports[groupId];
        saveUrgentReportsToStorage(reports);
        return null;
    }

    return report;
}

function clearUrgentReportForGroup(groupId) {
    const reports = getUrgentReportsFromStorage();
    delete reports[groupId];
    saveUrgentReportsToStorage(reports);
}

function formatUrgentDeadline(deadlineStr) {
    if (!deadlineStr) return '';
    const deadline = parseServerDate(deadlineStr);
    if (!deadline) return '';
    return deadline.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
}

// Обробка термінового звіту з push-повідомлення
function handleUrgentReportFromPush(data) {
    console.log('[URGENT] handleUrgentReportFromPush called with:', JSON.stringify(data));

    if (!data || !data.groupId) {
        console.warn('[URGENT] Invalid urgent report data - missing groupId');
        return;
    }

    // Розраховуємо deadline
    const deadlineMinutes = parseInt(data.deadlineMinutes) || 30;
    const deadline = new Date();
    deadline.setMinutes(deadline.getMinutes() + deadlineMinutes);

    console.log('[URGENT] Saving to localStorage: groupId=' + data.groupId + ', deadline=' + deadline.toISOString());

    // Зберігаємо терміновий звіт
    setUrgentReportForGroup(data.groupId, deadline.toISOString(), data.message || '');

    // Показуємо toast
    showToast(`🚨 Терміновий звіт: ${data.groupName || 'Група'}`, 'warning');

    // Оновлюємо UI якщо на екрані звітів
    const reportsScreen = document.getElementById('reportsScreen');
    if (reportsScreen?.classList.contains('active')) {
        console.log('[URGENT] Reloading reports screen...');
        loadReportsScreen();
    }
}

// Обробка зміни налаштувань групи з push-повідомлення
function handleSettingsUpdateFromPush(data) {
    console.log('[PWA] Settings update from push:', data);

    if (!data || !data.groupId) {
        console.warn('[PWA] Invalid settings update data');
        return;
    }

    // Показуємо toast з інформацією про зміни
    const groupName = data.groupName || 'Група';
    showToast(`⚙️ ${groupName}: налаштування оновлено`, 'info');

    // Оновлюємо список груп для відображення нових налаштувань
    if (document.getElementById('reportsScreen')?.classList.contains('active')) {
        loadReportsScreen();
    }

    // Оновлюємо кеш груп
    if (currentUser) {
        loadUserGroups().catch(err => console.error('Failed to reload groups:', err));
    }
}

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

// ==========================================
// PHONE VERIFICATION (Firebase Phone Auth)
// ==========================================

let recaptchaWidgetId = null;

function initPhoneVerification() {
    // Don't re-initialize if already done
    if (recaptchaVerifier && recaptchaWidgetId !== null) {
        console.log('reCAPTCHA already initialized');
        return;
    }

    // Initialize reCAPTCHA verifier
    try {
        // Clear container first
        const container = document.getElementById('recaptcha-container');
        if (container) {
            container.innerHTML = '';
        }

        recaptchaVerifier = new firebase.auth.RecaptchaVerifier('recaptcha-container', {
            size: 'invisible',
            callback: (response) => {
                console.log('reCAPTCHA solved');
            },
            'expired-callback': () => {
                console.log('reCAPTCHA expired');
                showToast('Час reCAPTCHA вийшов, спробуйте знову');
                recaptchaWidgetId = null;
                recaptchaVerifier = null;
            }
        });
        recaptchaVerifier.render().then(widgetId => {
            recaptchaWidgetId = widgetId;
            console.log('reCAPTCHA rendered, widgetId:', widgetId);
        }).catch(err => {
            console.error('reCAPTCHA render error:', err);
        });
    } catch (error) {
        console.error('reCAPTCHA init error:', error);
    }
}

function resetRecaptcha() {
    try {
        if (recaptchaVerifier) {
            recaptchaVerifier.clear();
        }
    } catch (e) {
        console.log('Error clearing recaptcha:', e);
    }
    recaptchaVerifier = null;
    recaptchaWidgetId = null;

    // Clear container
    const container = document.getElementById('recaptcha-container');
    if (container) {
        container.innerHTML = '';
    }

    // Re-initialize after a delay
    setTimeout(() => {
        initPhoneVerification();
    }, 300);
}

async function sendVerificationCode() {
    const phoneInput = document.getElementById('phoneVerifyInput');
    const phone = normalizePhone(phoneInput.value);
    const btn = document.getElementById('sendCodeBtn');

    // Validate phone
    if (phone.length !== 13) {
        showToast('Введіть коректний номер телефону');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Надсилання...';

    try {
        // Ensure reCAPTCHA is initialized (but don't re-render if already exists)
        if (!recaptchaVerifier || recaptchaWidgetId === null) {
            resetRecaptcha();
            await new Promise(resolve => setTimeout(resolve, 600));
        }

        // Wait for reCAPTCHA to be ready
        if (!recaptchaVerifier) {
            throw new Error('reCAPTCHA не ініціалізовано');
        }

        const confirmationResult = await firebase.auth().signInWithPhoneNumber(phone, recaptchaVerifier);
        phoneVerificationId = confirmationResult.verificationId;
        phoneVerificationPhone = phone;

        // Store confirmation result for verification
        window.confirmationResult = confirmationResult;

        // Show OTP input step
        showOtpInputStep(phone);
        startOtpTimer();

    } catch (error) {
        console.error('SMS send error:', error);
        let errorMessage = 'Помилка надсилання коду';

        if (error.code === 'auth/invalid-phone-number') {
            errorMessage = 'Невірний формат номера телефону';
        } else if (error.code === 'auth/too-many-requests') {
            errorMessage = 'Забагато спроб. Спробуйте пізніше';
        } else if (error.code === 'auth/quota-exceeded') {
            errorMessage = 'Перевищено ліміт SMS. Спробуйте пізніше';
        } else if (error.code === 'auth/captcha-check-failed' ||
                   (error.message && error.message.includes('reCAPTCHA'))) {
            errorMessage = 'Помилка reCAPTCHA. Спробуйте знову';
            resetRecaptcha();
        }

        showToast(errorMessage);
        btn.disabled = false;
        btn.textContent = 'Надіслати код';
    }
}

function showOtpInputStep(phone) {
    document.getElementById('phoneInputStep').style.display = 'none';
    document.getElementById('otpInputStep').style.display = 'block';
    document.getElementById('otpSentMessage').textContent = `Код надіслано на ${formatPhoneDisplay(phone)}`;
    document.getElementById('otpCodeInput').focus();
}

function startOtpTimer() {
    let seconds = 60;
    const timerEl = document.getElementById('otpTimer');
    const resendLink = document.getElementById('resendCodeLink');

    resendLink.style.display = 'none';

    if (phoneVerificationTimer) {
        clearInterval(phoneVerificationTimer);
    }

    phoneVerificationTimer = setInterval(() => {
        seconds--;
        timerEl.textContent = `Код дійсний: ${seconds} сек`;

        if (seconds <= 0) {
            clearInterval(phoneVerificationTimer);
            timerEl.textContent = 'Час вийшов';
            resendLink.style.display = 'inline';
        }
    }, 1000);
}

async function resendVerificationCode() {
    // Reset and send again
    resetRecaptcha();
    document.getElementById('otpInputStep').style.display = 'none';
    document.getElementById('phoneInputStep').style.display = 'block';
    document.getElementById('sendCodeBtn').disabled = false;
    document.getElementById('sendCodeBtn').textContent = 'Надіслати код';

    // Auto-send after recaptcha is ready
    setTimeout(() => {
        sendVerificationCode();
    }, 500);
}

function resetPhoneVerification() {
    if (phoneVerificationTimer) {
        clearInterval(phoneVerificationTimer);
    }

    phoneVerificationId = null;
    phoneVerificationPhone = null;

    document.getElementById('otpInputStep').style.display = 'none';
    document.getElementById('phoneInputStep').style.display = 'block';
    document.getElementById('sendCodeBtn').disabled = false;
    document.getElementById('sendCodeBtn').textContent = 'Надіслати код';
    document.getElementById('otpCodeInput').value = '';
}

async function verifyOtpCode() {
    const code = document.getElementById('otpCodeInput').value.trim();
    const btn = document.getElementById('verifyCodeBtn');

    if (code.length !== 6) {
        showToast('Введіть 6-значний код');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Перевірка...';

    try {
        const result = await window.confirmationResult.confirm(code);

        // Success! Save verified phone
        localStorage.setItem('zvit_verified_phone', phoneVerificationPhone);
        setCookie('zvit_verified_phone', phoneVerificationPhone);

        // Sign out from Firebase (we use our own auth system)
        await firebase.auth().signOut();

        showToast('Номер підтверджено!');

        // Clear timer
        if (phoneVerificationTimer) {
            clearInterval(phoneVerificationTimer);
        }

        // Pre-fill phone in login form
        document.getElementById('loginPhone').value = phoneVerificationPhone;

        // Show choice: login or register
        showLoginOrRegisterChoice();

    } catch (error) {
        console.error('OTP verify error:', error);
        let errorMessage = 'Невірний код';

        if (error.code === 'auth/invalid-verification-code') {
            errorMessage = 'Невірний код верифікації';
        } else if (error.code === 'auth/code-expired') {
            errorMessage = 'Час коду вийшов. Надішліть повторно';
        }

        showToast(errorMessage);
        btn.disabled = false;
        btn.textContent = 'Підтвердити';
    }
}

function showLoginOrRegisterChoice() {
    // Create and show modal for choice
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.id = 'authChoiceModal';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h2>Вхід або реєстрація</h2>
            </div>
            <p style="margin-bottom: 20px; color: var(--text-secondary);">
                Виберіть дію:
            </p>
            <div style="display: flex; flex-direction: column; gap: 12px;">
                <button class="btn btn-primary" onclick="goToLogin()">У мене є акаунт</button>
                <button class="btn btn-secondary" onclick="goToRegister()">Зареєструватися</button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
}

function goToLogin() {
    const modal = document.getElementById('authChoiceModal');
    if (modal) modal.remove();

    const verifiedPhone = localStorage.getItem('zvit_verified_phone');
    if (verifiedPhone) {
        document.getElementById('loginPhone').value = verifiedPhone;
    }
    showScreen('loginScreen');
}

function goToRegister() {
    const modal = document.getElementById('authChoiceModal');
    if (modal) modal.remove();

    const verifiedPhone = localStorage.getItem('zvit_verified_phone');
    if (verifiedPhone) {
        document.getElementById('registerPhone').value = verifiedPhone;
    }
    showScreen('registerScreen');
}

function isPhoneVerified() {
    return localStorage.getItem('zvit_verified_phone') !== null;
}

function getVerifiedPhone() {
    return localStorage.getItem('zvit_verified_phone');
}

// ==========================================
// END PHONE VERIFICATION
// ==========================================

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
    // Initialize history state for back button
    history.replaceState({ screen: 'reportsScreen' }, '', '');

    initApp();
    registerServiceWorker();
    setupInstallPrompt();
});

async function initApp() {
    // Check if user is logged in
    const token = localStorage.getItem('zvit_token');
    const userData = localStorage.getItem('zvit_user');
    let verifiedPhone = localStorage.getItem('zvit_verified_phone');

    // iOS PWA fix: check cookie as fallback for verified phone
    // (iOS uses different localStorage for standalone PWA)
    if (!verifiedPhone) {
        const cookiePhone = getCookie('zvit_verified_phone');
        if (cookiePhone) {
            verifiedPhone = cookiePhone;
            localStorage.setItem('zvit_verified_phone', cookiePhone);
        }
    }

    // Sync localStorage to cookie for future iOS PWA opens
    if (verifiedPhone && !getCookie('zvit_verified_phone')) {
        setCookie('zvit_verified_phone', verifiedPhone);
    }

    if (token && userData) {
        currentUser = JSON.parse(userData);
        showMainScreen();
    } else if (verifiedPhone) {
        // Phone verified but not logged in - show login screen
        showScreen('loginScreen');
        // Pre-fill phone number
        const loginPhoneInput = document.getElementById('loginPhone');
        if (loginPhoneInput) {
            loginPhoneInput.value = verifiedPhone;
        }
    } else if (isIOS() && isStandalonePWA()) {
        // iOS standalone PWA without verified phone - show login directly
        // (user likely has an account, just lost localStorage due to iOS limitation)
        showScreen('loginScreen');
        showToast('Увійдіть у свій акаунт', 'info');
    } else {
        // No phone verification - show phone verify screen
        showScreen('phoneVerifyScreen');
        initPhoneVerification();
    }

    // Setup form handlers
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('registerForm').addEventListener('submit', handleRegister);
    document.getElementById('verifyForm').addEventListener('submit', handleVerify);

    // Setup phone inputs with mask
    setupPhoneInput(document.getElementById('loginPhone'));
    setupPhoneInput(document.getElementById('registerPhone'));
    setupPhoneInput(document.getElementById('phoneVerifyInput'));
}

// Service Worker
async function registerServiceWorker() {
    if ('serviceWorker' in navigator) {
        try {
            const registration = await navigator.serviceWorker.register('/service-worker.js');
            console.log('SW registered:', registration);

            // Listen for messages from SW
            navigator.serviceWorker.addEventListener('message', (event) => {
                console.log('[PWA] Message from SW:', event.data);

                if (event.data.type === 'NOTIFICATION_CLICK') {
                    handleNotificationClick(event.data.data);
                }

                // Терміновий звіт отримано
                if (event.data.type === 'URGENT_REPORT_RECEIVED') {
                    handleUrgentReportFromPush(event.data.data);
                }

                // Налаштування групи змінено
                if (event.data.type === 'SETTINGS_UPDATE_RECEIVED') {
                    handleSettingsUpdateFromPush(event.data.data);
                }
            });
        } catch (error) {
            console.error('SW registration failed:', error);
        }
    }
}

// Install PWA
function setupInstallPrompt() {
    // Android Chrome - use beforeinstallprompt
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

    // iOS Safari - show custom instructions after delay
    if (isIOS() && !isStandalonePWA()) {
        setTimeout(() => {
            if (currentUser && !sessionStorage.getItem('zvit_install_dismissed_ios')) {
                document.getElementById('installModalIOS').classList.add('show');
            }
        }, 3000);
    }
}

async function installPWA() {
    if (!deferredPrompt) {
        // iOS Safari - show iOS modal
        if (isIOS()) {
            document.getElementById('installModal').classList.remove('show');
            document.getElementById('installModalIOS').classList.add('show');
        } else {
            showToast('Натисніть меню браузера → "Встановити додаток"', 'info');
            document.getElementById('installModal').classList.remove('show');
        }
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

function dismissInstallModalIOS(event) {
    document.getElementById('installModalIOS').classList.remove('show');
    sessionStorage.setItem('zvit_install_dismissed_ios', 'true');
}

// Screen Navigation
function showScreen(screenId, addToHistory = true) {
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

    // Track navigation history (but not for auth screens or when going back)
    if (addToHistory && !authScreens.includes(screenId)) {
        // Avoid duplicates at the end of stack
        if (navigationStack[navigationStack.length - 1] !== screenId) {
            navigationStack.push(screenId);
            // Keep stack reasonable size
            if (navigationStack.length > 20) {
                navigationStack = navigationStack.slice(-15);
            }
        }
        // Push state to enable system back button
        history.pushState({ screen: screenId }, '', '');
    }
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

async function updateSettingsScreen() {
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
        document.getElementById('profileEmail').textContent = currentUser.email || 'Не вказано';
    }
    // Load notification setting from server (synced with server)
    await loadNotificationsSettingFromServer();
}

async function showMainScreen() {
    // Після входу показуємо екран Звіти (як в Android)
    showScreen('reportsScreen');
    loadReportsScreen();

    // Update settings
    if (currentUser) {
        document.getElementById('profileName').textContent = currentUser.name || '-';
        document.getElementById('profilePhone').textContent = currentUser.phone || '-';
        document.getElementById('profileEmail').textContent = currentUser.email || 'Не вказано';
    }

    // Load notifications setting from server
    loadNotificationsSettingFromServer();

    // Auto-initialize push notifications if permission already granted
    initPushNotificationsIfEnabled();

    // Show install modal if available (reset dismissed state on screen change)
    sessionStorage.removeItem('zvit_install_dismissed');
    if (deferredPrompt) {
        setTimeout(() => {
            document.getElementById('installModal').classList.add('show');
        }, 2000);
    }
}

/**
 * Automatically initialize push notifications if:
 * 1. Browser supports notifications
 * 2. Permission is already granted (no prompt needed)
 * This ensures FCM token is registered/refreshed on each app load
 */
async function initPushNotificationsIfEnabled() {
    try {
        // Check browser support
        if (!('Notification' in window) || !('serviceWorker' in navigator)) {
            console.log('[FCM] Browser does not support push notifications');
            return;
        }

        // Check if permission already granted (don't prompt)
        if (Notification.permission !== 'granted') {
            console.log('[FCM] Notification permission not granted, skipping auto-init');
            return;
        }

        console.log('[FCM] Auto-initializing push notifications...');

        // Subscribe to push and send token to backend
        await subscribeToPush();

        console.log('[FCM] Auto-init completed successfully');
    } catch (error) {
        console.error('[FCM] Auto-init failed:', error);
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

    // Check if phone is verified and matches
    const verifiedPhone = getVerifiedPhone();
    if (verifiedPhone && verifiedPhone !== phone) {
        showToast('Цей номер не співпадає з верифікованим. Використовуйте ' + formatPhoneDisplay(verifiedPhone), 'error');
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

            // Save phone to cookie for iOS PWA persistence
            localStorage.setItem('zvit_verified_phone', phone);
            setCookie('zvit_verified_phone', phone);

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

    // Check if phone is verified and matches
    const verifiedPhone = getVerifiedPhone();
    if (verifiedPhone && verifiedPhone !== phone) {
        showToast('Цей номер не співпадає з верифікованим. Використовуйте ' + formatPhoneDisplay(verifiedPhone), 'error');
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
            // Реєстрація успішна - автоматично логінимось
            showToast('Реєстрація успішна!', 'success');

            // Виконуємо логін
            const loginResponse = await apiRequest('/pwa/login', 'POST', {
                phone: phone,
                password: password
            });

            if (loginResponse.success && loginResponse.data) {
                const loginData = loginResponse.data;
                localStorage.setItem('zvit_token', loginData.token);
                localStorage.setItem('zvit_user', JSON.stringify({
                    id: loginData.userId,
                    name: loginData.name || name,
                    phone: formatPhoneDisplay(phone)
                }));

                // Save phone to cookie for iOS PWA persistence
                localStorage.setItem('zvit_verified_phone', phone);
                setCookie('zvit_verified_phone', phone);

                currentUser = {
                    id: loginData.userId,
                    name: loginData.name || name,
                    phone: formatPhoneDisplay(phone)
                };

                showMainScreen();
            } else {
                // Якщо автологін не вдався - переходимо на логін
                showToast('Увійдіть з новим паролем', 'info');
                showScreen('loginScreen');
            }
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

            // Save phone to cookie for iOS PWA persistence
            localStorage.setItem('zvit_verified_phone', phone);
            setCookie('zvit_verified_phone', phone);

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
    // Keep verified phone in localStorage and cookie for easier re-login
    currentUser = null;
    currentGroup = null;
    showScreen('loginScreen');
    showToast('Ви вийшли з акаунту', 'success');
}

// Clear App Cache (for PWA)
async function clearAppCache() {
    if (!confirm('Скинути кеш додатку? Сторінка буде перезавантажена.')) {
        return;
    }

    try {
        // Clear Service Worker caches
        if ('caches' in window) {
            const cacheNames = await caches.keys();
            await Promise.all(cacheNames.map(cacheName => caches.delete(cacheName)));
            console.log('Service Worker caches cleared');
        }

        // Unregister Service Worker
        if ('serviceWorker' in navigator) {
            const registrations = await navigator.serviceWorker.getRegistrations();
            for (const registration of registrations) {
                await registration.unregister();
            }
            console.log('Service Worker unregistered');
        }

        // Clear localStorage (except auth token)
        const token = localStorage.getItem('zvit_token');
        const user = localStorage.getItem('zvit_user');
        localStorage.clear();
        if (token) localStorage.setItem('zvit_token', token);
        if (user) localStorage.setItem('zvit_user', user);

        showToast('Кеш скинуто. Перезавантаження...', 'success');

        // Reload page after short delay
        setTimeout(() => {
            window.location.reload(true);
        }, 1000);

    } catch (error) {
        console.error('Error clearing cache:', error);
        showToast('Помилка скидання кешу', 'error');
    }
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
        // Не показуємо помилку якщо сесія закінчилась (logout вже обробив це)
        if (!localStorage.getItem('zvit_token')) {
            return; // Користувач вже розлогінений, не показуємо помилку
        }
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
        const hasAdminRights = group.userRole === 'ADMIN' || group.userRole === 'MODER';
        const roleLabels = { 'ADMIN': 'Адміністратор', 'MODER': 'Модератор', 'MEMBER': 'Учасник' };
        const role = roleLabels[group.userRole] || 'Учасник';
        const roleClass = hasAdminRights ? 'admin-role' : '';
        const cardClass = hasAdminRights ? 'admin' : 'member';
        const reportType = group.reportType === 'EXTENDED' ? 'Розширений' : 'Простий';

        // Admins and moderators can click to open group
        const onclick = hasAdminRights ? `onclick="openGroup('${id}', '${escapeHtml(name)}')"` : '';

        return `
        <div class="card group-card ${cardClass}" ${onclick}>
            <div class="group-name">${escapeHtml(name)}</div>
            <div class="group-members">${members} учасн.</div>
            <div class="group-role ${roleClass}">${role}</div>
            <div class="group-report-type">${reportType}</div>
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
                userRole: group.userRole,
                isAdmin: group.userRole === 'ADMIN' || group.userRole === 'MODER',
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
                document.getElementById('groupMembersInfo').textContent = group.currentMembers || 0;
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

    // Ініціалізуємо drum pickers з поточним значенням
    setIntervalFromMinutes(currentGroup.intervalMinutes || 60);

    const modal = document.getElementById('scheduleModal');
    modal.style.display = '';  // Очищаємо inline style, який міг залишитись від closeModal
    modal.classList.add('active');
}

function toggleScheduleType() {
    const isInterval = document.getElementById('scheduleInterval').checked;
    document.getElementById('fixedTimesSection').style.display = isInterval ? 'none' : 'block';
    document.getElementById('intervalSection').style.display = isInterval ? 'block' : 'none';
}

// Drum Picker для інтервалу
let selectedHours = 0;
let selectedMinutes = 5;

function initDrumPickers() {
    const hoursContainer = document.getElementById('hoursPickerItems');
    const minutesContainer = document.getElementById('minutesPickerItems');

    if (!hoursContainer || !minutesContainer) return;

    // Години: 0-24
    hoursContainer.innerHTML = '';
    for (let h = 0; h <= 24; h++) {
        const item = document.createElement('div');
        item.className = 'drum-picker-item';
        item.textContent = h;
        item.dataset.value = h;
        item.addEventListener('click', () => selectHour(h));
        hoursContainer.appendChild(item);
    }

    // Хвилини: 0, 5, 10, ..., 55
    minutesContainer.innerHTML = '';
    for (let m = 0; m <= 55; m += 5) {
        const item = document.createElement('div');
        item.className = 'drum-picker-item';
        item.textContent = m.toString().padStart(2, '0');
        item.dataset.value = m;
        item.addEventListener('click', () => selectMinute(m));
        minutesContainer.appendChild(item);
    }

    // Ініціалізуємо scroll handlers
    setupDrumPickerScroll('hoursPicker', 'hours');
    setupDrumPickerScroll('minutesPicker', 'minutes');
}

function setupDrumPickerScroll(pickerId, type) {
    const picker = document.getElementById(pickerId);
    if (!picker) return;

    let startY = 0;
    let startX = 0;
    let isSwiping = false;

    picker.addEventListener('wheel', (e) => {
        e.preventDefault();
        const delta = e.deltaY > 0 ? 1 : -1;
        if (type === 'hours') {
            selectHour(Math.max(0, Math.min(24, selectedHours + delta)));
        } else {
            const currentIndex = selectedMinutes / 5;
            const newIndex = Math.max(0, Math.min(11, currentIndex + delta));
            selectMinute(newIndex * 5);
        }
    }, { passive: false });

    // Touch support
    picker.addEventListener('touchstart', (e) => {
        startY = e.touches[0].clientY;
        startX = e.touches[0].clientX;
        isSwiping = false;
    }, { passive: true });

    picker.addEventListener('touchmove', (e) => {
        const diffY = Math.abs(e.touches[0].clientY - startY);
        if (diffY > 10) {
            isSwiping = true;
            e.preventDefault();
        }
    }, { passive: false });

    picker.addEventListener('touchend', (e) => {
        const endY = e.changedTouches[0].clientY;
        const endX = e.changedTouches[0].clientX;
        const diffY = startY - endY;
        const diffX = Math.abs(startX - endX);

        // Якщо це свайп вгору/вниз
        if (Math.abs(diffY) > 20 && diffX < 50) {
            const delta = diffY > 0 ? 1 : -1;
            if (type === 'hours') {
                selectHour(Math.max(0, Math.min(24, selectedHours + delta)));
            } else {
                const currentIndex = selectedMinutes / 5;
                const newIndex = Math.max(0, Math.min(11, currentIndex + delta));
                selectMinute(newIndex * 5);
            }
        }
        // Якщо це тап (не свайп) - знаходимо який елемент натиснули
        else if (!isSwiping && Math.abs(diffY) < 15) {
            const pickerRect = picker.getBoundingClientRect();
            const tapY = endY - pickerRect.top;
            const centerY = pickerRect.height / 2;
            const itemHeight = 50;

            // Визначаємо зміщення від центру в кількості елементів
            const offsetFromCenter = Math.round((tapY - centerY) / itemHeight);

            if (type === 'hours') {
                const newHour = Math.max(0, Math.min(24, selectedHours + offsetFromCenter));
                selectHour(newHour);
            } else {
                const currentIndex = selectedMinutes / 5;
                const newIndex = Math.max(0, Math.min(11, currentIndex + offsetFromCenter));
                selectMinute(newIndex * 5);
            }
        }
    }, { passive: true });
}

function selectHour(hour) {
    selectedHours = hour;
    updateDrumPicker('hoursPickerItems', hour, 25);
    validateInterval();
}

function selectMinute(minute) {
    selectedMinutes = minute;
    updateDrumPicker('minutesPickerItems', minute / 5, 12);
    validateInterval();
}

function updateDrumPicker(containerId, selectedIndex, totalItems) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const items = container.querySelectorAll('.drum-picker-item');
    items.forEach((item, index) => {
        item.classList.toggle('selected', index === selectedIndex);
    });

    // Позиціонування для центрування вибраного елемента
    const offset = -selectedIndex * 50;
    container.style.transform = `translateY(calc(-50% + ${-offset}px - 25px))`;
}

function validateInterval() {
    // Мінімум 5 хвилин, максимум 24 години
    const totalMinutes = selectedHours * 60 + selectedMinutes;

    if (totalMinutes < 5) {
        selectedHours = 0;
        selectedMinutes = 5;
        updateDrumPicker('hoursPickerItems', 0, 25);
        updateDrumPicker('minutesPickerItems', 1, 12);
    } else if (totalMinutes > 24 * 60) {
        selectedHours = 24;
        selectedMinutes = 0;
        updateDrumPicker('hoursPickerItems', 24, 25);
        updateDrumPicker('minutesPickerItems', 0, 12);
    }
}

function setIntervalFromMinutes(totalMinutes) {
    if (!totalMinutes || totalMinutes < 5) totalMinutes = 60; // default 1 година
    if (totalMinutes > 24 * 60) totalMinutes = 24 * 60;

    selectedHours = Math.floor(totalMinutes / 60);
    selectedMinutes = Math.round((totalMinutes % 60) / 5) * 5;

    updateDrumPicker('hoursPickerItems', selectedHours, 25);
    updateDrumPicker('minutesPickerItems', selectedMinutes / 5, 12);
}

function getIntervalMinutes() {
    return selectedHours * 60 + selectedMinutes;
}

// Слухачі для перемикання типу розкладу
document.addEventListener('DOMContentLoaded', () => {
    const scheduleFixed = document.getElementById('scheduleFixed');
    const scheduleInterval = document.getElementById('scheduleInterval');
    if (scheduleFixed) scheduleFixed.addEventListener('change', toggleScheduleType);
    if (scheduleInterval) scheduleInterval.addEventListener('change', toggleScheduleType);

    // Ініціалізуємо drum pickers
    initDrumPickers();
});

async function saveSchedule() {
    if (!currentGroup) return;

    const isInterval = document.getElementById('scheduleInterval').checked;

    const request = {
        scheduleType: isInterval ? 'INTERVAL' : 'FIXED_TIMES'
    };

    if (isInterval) {
        request.intervalStartTime = document.getElementById('intervalStart').value;
        request.intervalMinutes = getIntervalMinutes();
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

// Показати діалог зміни слів звіту
function showChangeReportWordsDialog() {
    if (!currentGroup || !currentGroup.isAdmin) return;

    const currentPositive = currentGroup.positiveWord || 'ОК';
    const currentNegative = currentGroup.negativeWord || 'НЕ ОК';
    const currentPair = `${currentPositive}|${currentNegative}`;

    // Скидаємо всі радіо-кнопки
    const radios = document.querySelectorAll('input[name="reportWords"]');
    let found = false;

    radios.forEach(radio => {
        if (radio.value === currentPair) {
            radio.checked = true;
            found = true;
        } else if (radio.value !== 'CUSTOM') {
            radio.checked = false;
        }
    });

    // Якщо не знайдено стандартний варіант - вибираємо "Власні слова"
    if (!found) {
        document.querySelector('input[name="reportWords"][value="CUSTOM"]').checked = true;
        document.getElementById('customPositiveWord').value = currentPositive;
        document.getElementById('customNegativeWord').value = currentNegative;
        document.getElementById('customWordsSection').style.display = 'block';
    } else {
        document.getElementById('customWordsSection').style.display = 'none';
        document.getElementById('customPositiveWord').value = '';
        document.getElementById('customNegativeWord').value = '';
    }

    // Обробник зміни вибору
    radios.forEach(radio => {
        radio.onchange = function() {
            const customSection = document.getElementById('customWordsSection');
            if (this.value === 'CUSTOM') {
                customSection.style.display = 'block';
                document.getElementById('customPositiveWord').value = currentGroup.positiveWord || '';
                document.getElementById('customNegativeWord').value = currentGroup.negativeWord || '';
            } else {
                customSection.style.display = 'none';
            }
        };
    });

    document.getElementById('changeReportWordsModal').classList.add('active');
}

// Зберегти слова звіту
async function saveReportWords() {
    if (!currentGroup) return;

    const selectedRadio = document.querySelector('input[name="reportWords"]:checked');
    if (!selectedRadio) return;

    let positiveWord, negativeWord;

    if (selectedRadio.value === 'CUSTOM') {
        positiveWord = document.getElementById('customPositiveWord').value.trim();
        negativeWord = document.getElementById('customNegativeWord').value.trim();

        if (!positiveWord || !negativeWord) {
            showToast('Введіть обидва слова', 'error');
            return;
        }

        if (positiveWord.length > 20 || negativeWord.length > 20) {
            showToast('Максимум 20 символів', 'error');
            return;
        }
    } else {
        const words = selectedRadio.value.split('|');
        positiveWord = words[0];
        negativeWord = words[1];
    }

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}`, 'PUT', {
            positiveWord: positiveWord,
            negativeWord: negativeWord
        });

        if (response.success) {
            currentGroup.positiveWord = positiveWord;
            currentGroup.negativeWord = negativeWord;

            document.getElementById('groupWords').textContent = `${positiveWord} / ${negativeWord}`;

            closeModal('changeReportWordsModal');
            showToast('Слова звіту змінено', 'success');
        } else {
            showToast(response.message || 'Помилка збереження', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка збереження', 'error');
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

function showGroupSettings() {
    if (!currentGroup) return;
    document.getElementById('settingsGroupName').textContent = currentGroup.name;
    const modal = document.getElementById('groupSettingsModal');
    modal.classList.add('active');
    modal.style.display = 'flex';
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
                membersInfo.textContent = response.data.length;
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
        const isModerator = role === 'MODER';
        const isPending = member.status === 'PENDING';
        const memberId = member.id || member.userId;

        // Role button styles and labels
        const roleStyles = {
            'ADMIN': 'background: var(--primary); color: white;',
            'MODER': 'background: #7B1FA2; color: white;',
            'MEMBER': 'background: rgba(255,255,255,0.15); color: var(--text-secondary);'
        };
        const roleLabels = { 'ADMIN': 'Адмін', 'MODER': 'Модер', 'MEMBER': 'Учасник' };
        const roleStyle = isPending ? 'background: var(--warning); color: white;' : (roleStyles[role] || roleStyles['MEMBER']);
        const roleLabel = isPending ? 'Очікує' : (roleLabels[role] || 'Учасник');

        return `
            <div class="report-item" style="align-items: center; gap: 12px;">
                <div class="report-avatar" style="width: 40px; height: 40px;">${name.charAt(0).toUpperCase()}</div>
                <div class="report-content" style="flex: 1; min-width: 0;">
                    <div class="report-name" style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(name)}</div>
                    ${phone && !isPending ? `
                        <div onclick="showContactOptions('${phone}')" style="font-size: 13px; color: var(--primary); cursor: pointer; margin-top: 2px;">
                            ${escapeHtml(phone)}
                        </div>
                    ` : ''}
                </div>
                <div style="display: flex; align-items: center; gap: 12px;">
                    <button onclick="${isPending ? '' : `showRoleChangeDialog('${memberId}', '${role}')`}" style="border: none; padding: 8px 14px; border-radius: 8px; font-size: 13px; font-weight: 500; cursor: ${isPending ? 'default' : 'pointer'}; ${roleStyle}">
                        ${roleLabel}
                    </button>
                    ${!isAdmin && !isModerator && !isPending ? `
                        <button onclick="removeMember('${memberId}')" style="background: rgba(229, 115, 115, 0.15); border: none; color: var(--danger); width: 40px; height: 40px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center;">
                            <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                            </svg>
                        </button>
                    ` : ''}
                    ${isPending ? `
                        <button onclick="approveMember('${memberId}')" style="background: var(--success); border: none; color: white; width: 40px; height: 40px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 18px;">✓</button>
                        <button onclick="rejectMember('${memberId}')" style="background: var(--danger); border: none; color: white; width: 40px; height: 40px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 18px;">✕</button>
                    ` : ''}
                </div>
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

    const adminCount = currentGroup.adminCount || 1;

    // Якщо це єдиний адмін - не можна змінити роль
    if (currentRole === 'ADMIN' && adminCount <= 1) {
        showToast('Неможливо змінити роль єдиного адміністратора', 'error');
        return;
    }

    // Показуємо модальне вікно вибору ролі
    const roleOptions = [
        { value: 'ADMIN', label: 'Адміністратор', desc: 'Повний доступ, не звітує' },
        { value: 'MODER', label: 'Модератор', desc: 'Повний доступ, звітує' },
        { value: 'MEMBER', label: 'Учасник', desc: 'Тільки звітує' }
    ];

    let optionsHtml = roleOptions.map(opt => `
        <div class="role-option ${currentRole === opt.value ? 'selected' : ''}" onclick="selectNewRole('${memberId}', '${opt.value}', '${currentRole}')">
            <div class="role-option-title">${opt.label}</div>
            <div class="role-option-desc">${opt.desc}</div>
        </div>
    `).join('');

    document.getElementById('roleSelectOptions').innerHTML = optionsHtml;
    document.getElementById('roleSelectModal').style.display = 'flex';
}

function selectNewRole(memberId, newRole, currentRole) {
    if (newRole === currentRole) {
        closeModal('roleSelectModal');
        return;
    }

    const adminCount = currentGroup.adminCount || 1;
    if (currentRole === 'ADMIN' && newRole !== 'ADMIN' && adminCount <= 1) {
        showToast('Неможливо змінити роль єдиного адміністратора', 'error');
        return;
    }

    changeMemberRole(memberId, newRole);
    closeModal('roleSelectModal');
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
        modal.style.display = 'none';
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
        // Не показуємо помилку якщо сесія закінчилась (logout вже обробив це)
        if (!localStorage.getItem('zvit_token')) {
            return; // Користувач вже розлогінений, не показуємо помилку
        }
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

    console.log('[PWA] renderReportGroups - all groups:', groups);

    // Фільтруємо тільки прийняті групи (userRole не null/undefined)
    const acceptedGroups = groups.filter(g => g.userRole);
    console.log('[PWA] Accepted groups:', acceptedGroups);

    // Розділяємо на групи за роллю
    const adminGroups = acceptedGroups.filter(g => g.userRole === 'ADMIN');
    const moderatorGroups = acceptedGroups.filter(g => g.userRole === 'MODER');
    const memberGroups = acceptedGroups.filter(g => g.userRole === 'MEMBER');

    console.log('[PWA] Admin groups:', adminGroups.length, 'Moderator groups:', moderatorGroups.length, 'Member groups:', memberGroups.length);

    let html = '';

    // Групи де адмін (тільки статуси, без кнопки звіту)
    if (adminGroups.length > 0) {
        html += `<div class="section-title" style="padding: 8px 0; font-size: 16px; font-weight: bold;">Групи де я адміністратор</div>`;
        adminGroups.forEach(group => {
            html += renderReportGroupCard(group, 'ADMIN');
        });
    }

    // Групи де модератор (статуси + кнопка звіту)
    if (moderatorGroups.length > 0) {
        html += `<div class="section-title" style="padding: 8px 0; font-size: 16px; font-weight: bold; margin-top: 8px;">Групи де я модератор</div>`;
        moderatorGroups.forEach(group => {
            html += renderReportGroupCard(group, 'MODER');
        });
    }

    // Групи де учасник (тільки кнопка звіту)
    if (memberGroups.length > 0) {
        html += `<div class="section-title" style="padding: 8px 0; font-size: 16px; font-weight: bold; margin-top: 8px;">Групи де я учасник</div>`;
        memberGroups.forEach(group => {
            html += renderReportGroupCard(group, 'MEMBER');
        });
    }

    if (html === '') {
        html = `
            <div style="text-align: center; padding: 32px; color: var(--text-secondary);">
                <p>Немає груп для звітування</p>
            </div>
        `;
    }

    container.innerHTML = html;
}

function renderReportGroupCard(group, role) {
    const membersCount = group.currentMembers || 0;
    const reportedCount = group.reportedCount || 0;
    const roleLabels = { 'ADMIN': 'Адміністратор', 'MODER': 'Модератор', 'MEMBER': 'Учасник' };
    const roleText = roleLabels[role] || 'Учасник';
    const groupName = group.externalName || group.name || 'Група';
    const groupId = group.groupId || group.id;

    const isAdmin = role === 'ADMIN';
    const isModerator = role === 'MODER';
    const hasAdminRights = isAdmin || isModerator;
    const mustReport = isModerator || role === 'MEMBER';

    // Екрануємо для безпечного використання в onclick
    const safeGroupName = groupName.replace(/'/g, "\\'").replace(/"/g, '&quot;');

    // Обраховуємо час наступного звіту
    const nextReportTime = calculateNextReportTime(group);
    const nextReportText = nextReportTime ? `Наступний звіт о ${nextReportTime}` : '';

    // Перевіряємо терміновий звіт
    // Спочатку з localStorage (збережено з push), потім з сервера
    let urgentReport = getUrgentReportForGroup(groupId);

    // Якщо сервер повідомив про активну сесію - синхронізуємо localStorage
    if (group.hasActiveUrgentSession && group.urgentExpiresAt) {
        console.log('[URGENT] Server reports active session for group', groupId, ':', group.urgentExpiresAt);
        const serverDeadline = parseServerDate(group.urgentExpiresAt);
        if (serverDeadline && serverDeadline > new Date()) {
            // Оновлюємо/зберігаємо інформацію з сервера
            console.log('[URGENT] Syncing to localStorage from server data');
            setUrgentReportForGroup(groupId, group.urgentExpiresAt, group.urgentMessage || '');
            urgentReport = { deadline: group.urgentExpiresAt, message: group.urgentMessage || '' };
        }
    }

    const hasUrgentReport = urgentReport !== null;
    if (hasUrgentReport) {
        console.log('[URGENT] Group', groupId, 'has urgent report:', urgentReport);
    }

    // Права частина залежить від ролі:
    // - ADMIN: нічого (терміновий звіт переноситься в групу)
    // - MODER/MEMBER: кнопка звіту
    let rightSection;
    if (isAdmin) {
        // Адмін: пусто справа (тільки статуси зліва)
        rightSection = `
            <div style="flex: 0.4; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px;">
                <svg viewBox="0 0 24 24" fill="var(--text-secondary)" width="28" height="28" style="opacity: 0.3;">
                    <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/>
                </svg>
                <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px; opacity: 0.5;">Статуси</div>
            </div>
        `;
    } else {
        // Модератор/Учасник: кнопка "Звіт" або терміновий звіт
        if (hasUrgentReport) {
            const deadlineTime = formatUrgentDeadline(urgentReport.deadline);
            rightSection = `
                <div style="flex: 0.4; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px; cursor: pointer; background: rgba(244, 67, 54, 0.1);" onclick="openReportForGroup('${groupId}', '${safeGroupName}', '${group.reportType}', ${hasAdminRights}, '${(group.positiveWord || 'ОК').replace(/'/g, "\\'")}', '${(group.negativeWord || 'НЕ ОК').replace(/'/g, "\\'")}')">
                    <svg viewBox="0 0 24 24" fill="var(--danger)" width="32" height="32">
                        <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>
                    </svg>
                    <div style="font-size: 11px; font-weight: bold; color: var(--danger); margin-top: 4px;">ТЕРМІНОВО</div>
                    <div style="font-size: 10px; color: var(--danger);">до ${deadlineTime}</div>
                </div>
            `;
        } else {
            rightSection = `
                <div style="flex: 0.4; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px; cursor: pointer;" onclick="openReportForGroup('${groupId}', '${safeGroupName}', '${group.reportType}', ${hasAdminRights}, '${(group.positiveWord || 'ОК').replace(/'/g, "\\'")}', '${(group.negativeWord || 'НЕ ОК').replace(/'/g, "\\'")}')">
                    <svg viewBox="0 0 24 24" fill="var(--primary)" width="32" height="32">
                        <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                    </svg>
                    <div style="font-size: 12px; font-weight: bold; color: var(--primary); margin-top: 4px;">Звіт</div>
                </div>
            `;
        }
    }

    // Ліва частина:
    // - ADMIN/MODER: відкриває статуси
    // - MEMBER: відкриває свої звіти
    const leftOnclick = hasAdminRights
        ? `onclick="openGroupStatuses('${groupId}', '${safeGroupName}')"`
        : `onclick="openMyReportsInGroup('${groupId}', '${safeGroupName}')"`;

    // Індикатор термінового звіту - тільки для адмінів (у них права частина без термінового статусу)
    // Для учасників/модераторів терміновий статус вже показується в правій частині (rightSection)
    const urgentIndicator = (isAdmin && hasUrgentReport) ? `
        <div style="position: absolute; top: 8px; right: 40%; background: var(--danger); color: white; font-size: 10px; padding: 2px 6px; border-radius: 4px; font-weight: bold;">
            ТЕРМІНОВО
        </div>
    ` : '';

    return `
        <div class="card report-group-card" style="margin-bottom: 8px; padding: 0; overflow: hidden; position: relative;">
            ${urgentIndicator}
            <div style="display: flex;">
                <!-- Ліва частина -->
                <div style="flex: 0.6; display: flex; padding: 12px; cursor: pointer;" ${leftOnclick}>
                    <!-- Кольоровий індикатор -->
                    <div style="width: 6px; background: ${hasUrgentReport && mustReport ? 'var(--danger)' : 'var(--primary)'}; border-radius: 3px; margin-right: 10px;"></div>
                    <!-- Інформація -->
                    <div style="flex: 1; min-width: 0;">
                        <div style="font-size: 15px; font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                            ${escapeHtml(groupName)} ${hasAdminRights ? `(${membersCount})` : ''}
                        </div>
                        <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">
                            ${roleText}
                        </div>
                        ${nextReportText ? `<div style="font-size: 11px; color: var(--text-secondary); margin-top: 2px;">${nextReportText}</div>` : ''}
                    </div>
                </div>
                <!-- Розділювач -->
                <div style="width: 1px; background: rgba(255,255,255,0.1);"></div>
                <!-- Права частина -->
                ${rightSection}
            </div>
        </div>
    `;
}

function openGroupDetails(groupId) {
    console.log('[PWA] openGroupDetails called:', groupId);
    currentGroup = { id: groupId };
    showScreen('groupScreen');
    loadGroupDetails(groupId);
}

// Відкрити екран статусів учасників (для адміна)
async function openGroupStatuses(groupId, groupName) {
    console.log('[PWA] openGroupStatuses called:', groupId, groupName);
    currentGroup = { id: groupId, name: groupName };
    document.getElementById('groupStatusTitle').textContent = groupName || 'Статус групи';

    // Показуємо кнопки для адміна
    const urgentBtn = document.getElementById('urgentReportBtn');
    if (urgentBtn) {
        urgentBtn.style.display = 'flex';
    }
    const qrBtn = document.getElementById('qrScannerBtn');
    if (qrBtn) {
        qrBtn.style.display = 'flex';
    }

    showScreen('groupStatusScreen');

    // Завантажуємо деталі групи для власних слів
    try {
        const groupResponse = await apiRequest(`/pwa/groups/${groupId}`, 'GET');
        if (groupResponse.success && groupResponse.data) {
            currentGroup.positiveWord = groupResponse.data.positiveWord || 'ОК';
            currentGroup.negativeWord = groupResponse.data.negativeWord || 'НЕ ОК';
        }
    } catch (e) {
        console.log('[PWA] Could not load group details for words:', e);
    }

    loadGroupStatuses(groupId);
}

// Відкрити мої звіти в групі (для учасника)
async function openMyReportsInGroup(groupId, groupName) {
    console.log('[PWA] openMyReportsInGroup called:', groupId, groupName);

    currentGroup = { id: groupId, name: groupName };
    currentReportUser = null; // Це мої звіти, не потрібен телефон

    // Оновлюємо заголовок - назва групи
    document.getElementById('userReportsTitle').textContent = groupName || 'Група';

    // Показуємо субтитр "Мої звіти"
    document.getElementById('userReportsSubtitle').style.display = 'block';

    // Ховаємо телефон (це мої звіти)
    document.getElementById('userReportsPhoneHeader').style.display = 'none';

    // Ховаємо кнопку видалення (це мої звіти)
    const deleteBtn = document.getElementById('btnDeleteUserReports');
    if (deleteBtn) {
        deleteBtn.style.display = 'none';
    }

    // Переходимо на екран
    showScreen('userReportsScreen');

    // Завантажуємо деталі групи для власних слів
    try {
        const groupResponse = await apiRequest(`/pwa/groups/${groupId}`, 'GET');
        if (groupResponse.success && groupResponse.data) {
            currentGroup.positiveWord = groupResponse.data.positiveWord || 'ОК';
            currentGroup.negativeWord = groupResponse.data.negativeWord || 'НЕ ОК';
        }
    } catch (e) {
        console.log('[PWA] Could not load group details for words:', e);
    }

    const container = document.getElementById('userReportsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        // Завантажуємо мої звіти в групі (той самий ендпойнт що для member view)
        const response = await apiRequest(`/pwa/groups/${groupId}/reports`, 'GET');
        console.log('[PWA] My reports response:', response);

        if (response.success && response.data && response.data.length > 0) {
            renderUserReportsList(response.data);
        } else {
            container.innerHTML = `
                <div style="text-align: center; padding: 32px; color: var(--text-secondary);">
                    <p>Ви ще не надсилали звітів</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('[PWA] My reports error:', error);
        container.innerHTML = `
            <div style="text-align: center; padding: 32px; color: var(--danger);">
                <p>Помилка завантаження звітів</p>
            </div>
        `;
    }
}

// Глобальна змінна для таймера термінового збору
let urgentTimerInterval = null;

// Завантажити статуси учасників групи
async function loadGroupStatuses(groupId) {
    const container = document.getElementById('userTilesGrid');
    container.innerHTML = '<div class="loading" style="grid-column: 1 / -1;"><div class="spinner"></div></div>';

    try {
        console.log('[PWA] Loading group statuses for:', groupId);
        const response = await apiRequest(`/pwa/groups/${groupId}/statuses`, 'GET');
        console.log('[PWA] Group statuses:', response);

        if (response.success && response.data) {
            // Обробка термінового збору
            handleUrgentSession(response.data.urgentSession);

            // Відображення плиток користувачів
            if (response.data.users) {
                renderUserTiles(response.data.users);
            } else {
                container.innerHTML = `
                    <div style="grid-column: 1 / -1; text-align: center; padding: 32px; color: var(--text-secondary);">
                        <p>Немає учасників у групі</p>
                    </div>
                `;
            }
        } else {
            hideUrgentBanner();
            container.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 32px; color: var(--text-secondary);">
                    <p>Немає учасників у групі</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('[PWA] Group statuses error:', error);
        hideUrgentBanner();
        container.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 32px; color: var(--danger);">
                <p>Помилка завантаження статусів</p>
                <p style="font-size: 12px; margin-top: 5px; opacity: 0.7;">${error.message || 'Невідома помилка'}</p>
                <button class="btn btn-secondary" style="margin-top: 10px; width: auto;" onclick="loadGroupStatuses('${groupId}')">Спробувати знову</button>
            </div>
        `;
    }
}

// Обробка термінового збору
function handleUrgentSession(urgentSession) {
    const banner = document.getElementById('urgentSessionBanner');
    const urgentBtn = document.getElementById('urgentReportBtn');

    if (urgentSession && urgentSession.active) {
        // Показати банер термінового збору
        showUrgentBanner(urgentSession);
        // Сховати кнопку створення термінового запиту
        if (urgentBtn) urgentBtn.style.display = 'none';
    } else {
        // Сховати банер
        hideUrgentBanner();
        // Показати кнопку створення термінового запиту
        if (urgentBtn) urgentBtn.style.display = 'flex';
    }
}

// Показати банер термінового збору
function showUrgentBanner(session) {
    const banner = document.getElementById('urgentSessionBanner');
    const infoEl = document.getElementById('urgentSessionInfo');

    if (!banner) return;

    // Інформація про запит
    let infoText = '';
    if (session.requestedByUserName) {
        infoText = `Запит від: ${session.requestedByUserName}`;
        if (session.requestedAt) {
            const requestedTime = new Date(session.requestedAt).toLocaleTimeString('uk-UA', {
                hour: '2-digit', minute: '2-digit'
            });
            infoText += ` о ${requestedTime}`;
        }
    }
    if (session.message) {
        infoText += ` 💬 "${session.message}"`;
    }
    // Додаємо статистику (як в Android)
    if (session.totalMembers !== undefined && session.respondedCount !== undefined) {
        infoText += ` | Відповіли: ${session.respondedCount} / ${session.totalMembers}`;
    }

    if (infoEl) infoEl.textContent = infoText;

    banner.style.display = 'block';

    // Запустити таймер (використовуємо remainingSeconds для точності)
    startUrgentTimer(session.remainingSeconds);
}

// Сховати банер термінового збору
function hideUrgentBanner() {
    const banner = document.getElementById('urgentSessionBanner');
    if (banner) banner.style.display = 'none';

    // Зупинити таймер
    if (urgentTimerInterval) {
        clearInterval(urgentTimerInterval);
        urgentTimerInterval = null;
    }
}

// Запустити таймер зворотного відліку
function startUrgentTimer(remainingSeconds) {
    const timerEl = document.getElementById('urgentTimer');
    if (!timerEl) return;

    // Зупинити попередній таймер
    if (urgentTimerInterval) {
        clearInterval(urgentTimerInterval);
    }

    // Якщо remainingSeconds не передано або <= 0
    if (!remainingSeconds || remainingSeconds <= 0) {
        timerEl.textContent = '00:00';
        return;
    }

    let secondsLeft = Math.floor(remainingSeconds);

    const updateTimer = () => {
        if (secondsLeft <= 0) {
            timerEl.textContent = '00:00';
            clearInterval(urgentTimerInterval);
            // Оновити статуси після закінчення часу
            if (currentGroup && currentGroup.id) {
                loadGroupStatuses(currentGroup.id);
            }
            return;
        }

        const minutes = Math.floor(secondsLeft / 60);
        const seconds = secondsLeft % 60;
        timerEl.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        secondsLeft--;
    };

    updateTimer();
    urgentTimerInterval = setInterval(updateTimer, 1000);
}

// Завершити терміновий збір
async function endUrgentSession() {
    if (!currentGroup || !currentGroup.id) {
        showToast('Помилка: група не вибрана', 'error');
        return;
    }

    if (!confirm('Ви впевнені, що хочете завершити терміновий збір?')) {
        return;
    }

    try {
        const response = await apiRequest(`/pwa/reports/urgent/${currentGroup.id}`, 'DELETE');
        if (response.success) {
            showToast('Терміновий збір завершено', 'success');
            hideUrgentBanner();
            // Оновити статуси
            loadGroupStatuses(currentGroup.id);
        } else {
            showToast(response.message || 'Помилка завершення збору', 'error');
        }
    } catch (error) {
        console.error('[PWA] End urgent session error:', error);
        showToast('Помилка завершення збору', 'error');
    }
}

// Відобразити плитки користувачів
function renderUserTiles(users) {
    const container = document.getElementById('userTilesGrid');

    if (!users || users.length === 0) {
        container.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 32px; color: var(--text-secondary);">
                <p>Немає учасників у групі</p>
            </div>
        `;
        return;
    }

    let html = '';
    users.forEach(user => {
        const userName = user.userName || 'Невідомий';
        const bgColor = user.colorHex || '#444444';
        const isAdmin = user.role === 'ADMIN';
        const userId = user.userId || user.id;
        const userPhone = user.phoneNumber || user.phone || '';

        // Форматуємо час та дату останнього звіту (DD.MM, HH:MM)
        let timeDateText = '';
        if (!isAdmin && user.lastReportAt) {
            const reportDate = parseServerDate(user.lastReportAt);
            if (reportDate) {
                const time = reportDate.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
                const day = String(reportDate.getDate()).padStart(2, '0');
                const month = String(reportDate.getMonth() + 1).padStart(2, '0');
                timeDateText = `${time}   ${day}.${month}`;
            }
        }

        // Визначаємо колір тексту в залежності від яскравості фону
        const textColor = isLightColor(bgColor) ? '#000000' : '#FFFFFF';
        const secondaryTextColor = isLightColor(bgColor) ? 'rgba(0,0,0,0.7)' : 'rgba(255,255,255,0.8)';

        // Екрануємо дані для onclick
        const safeUserName = userName.replace(/'/g, "\\'").replace(/"/g, '&quot;');
        const safePhone = userPhone.replace(/'/g, "\\'").replace(/"/g, '&quot;');

        html += `
            <div class="user-tile" onclick="openUserReports('${userId}', '${safeUserName}', '${safePhone}')" style="
                background: ${bgColor};
                border-radius: 8px;
                padding: 8px 4px;
                text-align: center;
                min-height: 70px;
                display: flex;
                flex-direction: column;
                justify-content: center;
                align-items: center;
                cursor: pointer;
                box-sizing: border-box;
            ">
                <div style="
                    font-size: 12px;
                    font-weight: bold;
                    color: ${textColor};
                    width: 100%;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    hyphens: auto;
                    line-height: 1.2;
                    max-height: 2.4em;
                    overflow: hidden;
                ">${escapeHtml(userName)}</div>
                ${isAdmin ? `
                    <div style="font-size: 10px; color: ${secondaryTextColor}; margin-top: 4px; font-weight: bold;">Адмін</div>
                ` : timeDateText ? `
                    <div style="font-size: 10px; color: ${secondaryTextColor}; margin-top: 4px; white-space: nowrap;">${timeDateText}</div>
                ` : ''}
            </div>
        `;
    });

    container.innerHTML = html;
}

// Зберігаємо дані поточного користувача для звітів
let currentReportUser = null;

// Відкрити звіти користувача (як окрему сторінку)
async function openUserReports(userId, userName, userPhone) {
    console.log('[PWA] Opening user reports:', userId, userName, userPhone);

    // Зберігаємо дані користувача
    currentReportUser = {
        id: userId,
        name: userName,
        phone: userPhone
    };

    // Оновлюємо заголовок
    document.getElementById('userReportsTitle').textContent = userName;

    // Ховаємо субтитр "Мої звіти" (це звіти іншого користувача)
    document.getElementById('userReportsSubtitle').style.display = 'none';

    // Показуємо телефон якщо є
    const phoneHeader = document.getElementById('userReportsPhoneHeader');
    if (userPhone && userPhone.trim()) {
        phoneHeader.textContent = userPhone;
        phoneHeader.style.display = 'block';
    } else {
        phoneHeader.style.display = 'none';
    }

    // Показуємо кнопку видалення звітів (це звіти іншого користувача, ми адмін/модератор)
    const deleteBtn = document.getElementById('btnDeleteUserReports');
    if (deleteBtn) {
        deleteBtn.style.display = 'block';
    }

    // Переходимо на екран
    showScreen('userReportsScreen');

    const container = document.getElementById('userReportsList');
    container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

    try {
        const groupId = currentGroup.id;
        const response = await apiRequest(`/pwa/groups/${groupId}/users/${userId}/reports`, 'GET');
        console.log('[PWA] User reports response:', response);

        if (response.success && response.data && response.data.length > 0) {
            renderUserReportsList(response.data);
        } else {
            container.innerHTML = `
                <div style="text-align: center; padding: 32px; color: var(--text-secondary);">
                    <p>Немає звітів</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('[PWA] User reports error:', error);
        container.innerHTML = `
            <div style="text-align: center; padding: 32px; color: var(--danger);">
                <p>Помилка завантаження звітів</p>
            </div>
        `;
    }
}

// Повернутись назад з екрану звітів користувача
function navigateBackFromUserReports() {
    // Якщо це мої звіти (currentReportUser null) - повертаємось на екран звітів
    // Якщо це звіти іншого користувача - повертаємось на статуси групи
    if (currentReportUser) {
        showScreen('groupStatusScreen');
    } else {
        navigateTo('reportsScreen');
    }
}

// Показати опції зв'язку з користувачем
function showUserCallOptions() {
    if (!currentReportUser || !currentReportUser.phone) return;

    document.getElementById('callOptionsPhone').textContent = currentReportUser.phone;
    document.getElementById('callOptionsModal').classList.add('active');
}

// Підтвердити видалення звітів користувача
function confirmDeleteUserReports() {
    if (!currentReportUser || !currentGroup) {
        showToast('Помилка: користувач або група не вибрані', 'error');
        return;
    }

    const userName = currentReportUser.name || 'цього користувача';

    if (confirm(`Ви впевнені, що хочете видалити ВСІ звіти користувача "${userName}"?\n\nЦю дію неможливо скасувати!`)) {
        deleteUserReports();
    }
}

// Видалити звіти користувача
async function deleteUserReports() {
    if (!currentReportUser || !currentGroup) return;

    try {
        const groupId = currentGroup.id;
        const userId = currentReportUser.id;

        const response = await apiRequest(`/reports/group/${groupId}/user/${userId}`, 'DELETE');

        if (response.success) {
            const deletedCount = response.data || 0;
            showToast(`Видалено ${deletedCount} звітів`, 'success');

            // Оновити список (буде порожній)
            const container = document.getElementById('userReportsList');
            container.innerHTML = `
                <div style="text-align: center; padding: 32px; color: var(--text-secondary);">
                    <p>Немає звітів</p>
                </div>
            `;
        } else {
            showToast(response.message || 'Помилка видалення', 'error');
        }
    } catch (error) {
        console.error('[PWA] Delete reports error:', error);
        showToast('Помилка видалення звітів', 'error');
    }
}

// Мобільний зв'язок
function makePhoneCall() {
    if (!currentReportUser || !currentReportUser.phone) return;
    const cleanPhone = currentReportUser.phone.replace(/[^\d+]/g, '');
    window.location.href = 'tel:' + cleanPhone;
    closeModal('callOptionsModal');
}

// Відкрити Signal
function openUserSignal() {
    if (!currentReportUser || !currentReportUser.phone) return;
    let phone = currentReportUser.phone.replace(/[^\d+]/g, '');
    if (!phone.startsWith('+')) {
        phone = '+' + phone;
    }
    window.open('https://signal.me/#p/' + phone, '_blank');
    closeModal('callOptionsModal');
}

// Відкрити WhatsApp
function openUserWhatsApp() {
    if (!currentReportUser || !currentReportUser.phone) return;
    const phone = currentReportUser.phone.replace(/[^\d+]/g, '').replace('+', '');
    window.open('https://wa.me/' + phone, '_blank');
    closeModal('callOptionsModal');
}

// Відобразити список звітів користувача
function renderUserReportsList(reports) {
    const container = document.getElementById('userReportsList');

    // Отримуємо власні слова з групи (якщо є)
    const positiveWord = currentGroup?.positiveWord || 'ОК';
    const negativeWord = currentGroup?.negativeWord || 'НЕ ОК';

    let html = '';
    reports.forEach(report => {
        const date = parseServerDate(report.submittedAt);
        if (!date) return;
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const time = date.toLocaleTimeString('uk-UA', { hour: '2-digit', minute: '2-digit' });
        const dateStr = `${day}.${month}`;

        // Тип звіту
        let typeText = 'Звіт';
        if (report.reportType === 'SIMPLE') typeText = 'Простий';
        else if (report.reportType === 'EXTENDED') typeText = 'Розширений';
        else if (report.reportType === 'URGENT') typeText = 'Терміновий';

        // Відповідь для простого звіту (з власними словами)
        let responseText = '';
        let responseColor = 'var(--text-primary)';
        if (report.reportType === 'SIMPLE' && report.simpleResponse) {
            if (report.simpleResponse === 'OK') {
                responseText = '✅ ' + positiveWord;
                responseColor = 'var(--success)';
            } else if (report.simpleResponse === 'NOT_OK') {
                responseText = '❌ ' + negativeWord;
                responseColor = 'var(--danger)';
            } else {
                responseText = report.simpleResponse;
            }
        }

        html += `
            <div class="card" style="margin: 8px 0; padding: 12px;">
                <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
                    <div style="display: flex; gap: 12px; align-items: center;">
                        <span style="color: var(--text-secondary);">${dateStr}</span>
                        <span style="font-weight: bold;">${time}</span>
                        <span style="color: var(--primary);">${typeText}</span>
                    </div>
                    ${responseText ? `<span style="font-weight: bold; color: ${responseColor}; text-align: right;">${responseText}</span>` : ''}
                </div>
        `;

        // Розширені поля (T1-T5)
        if (report.reportType === 'EXTENDED') {
            const fields = [
                { label: 'Т1', value: report.field1Value },
                { label: 'Т2', value: report.field2Value },
                { label: 'Т3', value: report.field3Value },
                { label: 'Т4', value: report.field4Value },
                { label: 'Т5', value: report.field5Value }
            ];

            fields.forEach(field => {
                if (field.value && field.value.trim()) {
                    html += `<div style="margin-top: 4px; font-size: 13px; color: var(--text-secondary);">${field.label}: ${escapeHtml(field.value)}</div>`;
                }
            });
        }

        // Коментар
        if (report.comment && report.comment.trim()) {
            html += `<div style="margin-top: 6px; font-size: 13px; font-style: italic; color: var(--text-secondary);">💬 ${escapeHtml(report.comment)}</div>`;
        }

        html += '</div>';
    });

    container.innerHTML = html;
}

// Визначити чи колір світлий
function isLightColor(hexColor) {
    if (!hexColor) return false;
    const hex = hexColor.replace('#', '');
    const r = parseInt(hex.substr(0, 2), 16);
    const g = parseInt(hex.substr(2, 2), 16);
    const b = parseInt(hex.substr(4, 2), 16);
    // Використовуємо формулу для відносної яскравості
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance > 0.5;
}

// Відкрити діалог термінового звіту (для адміна)
function openUrgentReportDialog(groupId, groupName) {
    currentGroup = { id: groupId, name: groupName };
    document.getElementById('urgentModalGroupName').textContent = groupName;
    document.getElementById('urgentDeadlineSelect').value = '30';
    document.getElementById('urgentMessage').value = '';
    document.getElementById('urgentReportModal').classList.add('active');
}

// Відкрити діалог термінового звіту з екрану групи
function openUrgentReportDialogFromGroup() {
    if (!currentGroup || !currentGroup.id) {
        showToast('Помилка: група не вибрана', 'error');
        return;
    }
    openUrgentReportDialog(currentGroup.id, currentGroup.name);
}

// Надіслати терміновий запит
async function submitUrgentReport() {
    const groupId = currentGroup.id;
    const deadlineMinutes = parseInt(document.getElementById('urgentDeadlineSelect').value);
    const additionalMessage = document.getElementById('urgentMessage').value.trim();

    // Базове повідомлення
    let message = 'Терміново надішліть звіт';
    if (additionalMessage) {
        message += '. ' + additionalMessage;
    }

    const submitBtn = document.querySelector('#urgentReportModal .btn-danger');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Надсилання...';

    try {
        const response = await apiRequest('/pwa/reports/urgent', 'POST', {
            groupId: groupId,
            deadlineMinutes: deadlineMinutes,
            message: message
        });

        if (response.success) {
            document.getElementById('urgentReportModal').classList.remove('active');
            showToast(`Терміновий запит надіслано! Сповіщень: ${response.data}`, 'success');
            // Пропонуємо переглянути статуси
            if (confirm('Переглянути хто вже відзвітував?')) {
                openGroupStatuses(groupId, currentGroup.name);
            }
        } else {
            showToast(response.message || 'Помилка надсилання', 'error');
        }
    } catch (error) {
        console.error('[PWA] Urgent report error:', error);
        showToast(error.message || 'Помилка надсилання термінового запиту', 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'ВІДПРАВИТИ ТЕРМІНОВО';
    }
}

function getPlural(n, one, few, many) {
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) return one;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
    return many;
}

function openReportForGroup(groupId, groupName, reportType, isAdmin, positiveWord, negativeWord) {
    // Зберігаємо вибрану групу для звіту
    currentGroup = {
        id: groupId,
        name: groupName,
        reportType: reportType,
        isAdmin: isAdmin,
        positiveWord: positiveWord || 'ОК',
        negativeWord: negativeWord || 'НЕ ОК'
    };

    // Визначаємо який модал відкрити в залежності від типу звіту
    if (reportType === 'EXTENDED') {
        openExtendedReportModal();
    } else {
        openSimpleReportModal();
    }
}

// Simple Report Modal
function openSimpleReportModal() {
    if (!currentGroup) {
        showToast('Спочатку оберіть групу', 'error');
        return;
    }

    // Встановлюємо назву групи
    document.getElementById('simpleReportGroupName').textContent = currentGroup.name;

    // Встановлюємо власні слова групи
    const positiveWord = currentGroup.positiveWord || 'ОК';
    const negativeWord = currentGroup.negativeWord || 'НЕ ОК';
    document.getElementById('simpleReportOkLabel').textContent = `✅ ${positiveWord} - Все добре`;
    document.getElementById('simpleReportNotOkLabel').textContent = `❌ ${negativeWord} - Потрібна допомога`;

    // Скидаємо форму
    document.getElementById('simpleReportComment').value = '';
    selectedReportResponse = 'OK';

    // Скидаємо вибір на OK
    document.querySelectorAll('.simple-report-options .radio-option').forEach(opt => {
        opt.classList.remove('selected');
    });
    document.querySelector('.simple-report-options .radio-option').classList.add('selected');

    document.getElementById('simpleReportModal').classList.add('active');
}

// Extended Report Modal
function openExtendedReportModal() {
    if (!currentGroup) {
        showToast('Спочатку оберіть групу', 'error');
        return;
    }

    // Встановлюємо назву групи
    document.getElementById('extendedReportGroupName').textContent = currentGroup.name;

    // Скидаємо форму
    document.getElementById('extendedField1').value = '';
    document.getElementById('extendedField2').value = '';
    document.getElementById('extendedField3').value = '';
    document.getElementById('extendedField4').value = '';
    document.getElementById('extendedField5').value = '';
    document.getElementById('extendedReportComment').value = '';

    // Встановлюємо назви полів (якщо є в групі)
    const labels = ['Поле 1', 'Поле 2', 'Поле 3', 'Поле 4', 'Поле 5'];
    if (currentGroup.field1Name) labels[0] = currentGroup.field1Name;
    if (currentGroup.field2Name) labels[1] = currentGroup.field2Name;
    if (currentGroup.field3Name) labels[2] = currentGroup.field3Name;
    if (currentGroup.field4Name) labels[3] = currentGroup.field4Name;
    if (currentGroup.field5Name) labels[4] = currentGroup.field5Name;

    document.getElementById('extendedField1Label').textContent = labels[0];
    document.getElementById('extendedField2Label').textContent = labels[1];
    document.getElementById('extendedField3Label').textContent = labels[2];
    document.getElementById('extendedField4Label').textContent = labels[3];
    document.getElementById('extendedField5Label').textContent = labels[4];

    document.getElementById('extendedReportModal').classList.add('active');
}

function selectSimpleReportOption(element, value) {
    document.querySelectorAll('.simple-report-options .radio-option').forEach(opt => {
        opt.classList.remove('selected');
    });
    element.classList.add('selected');
    selectedReportResponse = value;
}

async function submitSimpleReport() {
    if (!currentGroup) {
        showToast('Оберіть групу', 'error');
        return;
    }

    const comment = document.getElementById('simpleReportComment').value;

    try {
        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/reports/simple`, 'POST', {
            groupId: currentGroup.id,
            simpleResponse: selectedReportResponse,
            comment: comment || null
        });

        if (response.success) {
            showToast('Звіт відправлено!', 'success');
            closeModal('simpleReportModal');
            // Прибираємо терміновий статус після відправки
            clearUrgentReportForGroup(currentGroup.id);
            // Оновлюємо список звітів
            if (typeof loadReportsScreen === 'function') {
                loadReportsScreen();
            }
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
            closeModal('simpleReportModal');
            // Прибираємо терміновий статус (звіт буде синхронізовано)
            clearUrgentReportForGroup(currentGroup.id);
        } else {
            showToast(error.message || 'Помилка відправки', 'error');
        }
    }
}

async function submitExtendedReport() {
    if (!currentGroup) {
        showToast('Оберіть групу', 'error');
        return;
    }

    const field1 = document.getElementById('extendedField1').value.trim();
    const field2 = document.getElementById('extendedField2').value.trim();
    const field3 = document.getElementById('extendedField3').value.trim();
    const field4 = document.getElementById('extendedField4').value.trim();
    const field5 = document.getElementById('extendedField5').value.trim();
    const comment = document.getElementById('extendedReportComment').value.trim();

    try {
        // Шифруємо поля якщо є публічний ключ
        let encryptedField1 = field1;
        let encryptedField2 = field2;
        let encryptedField3 = field3;
        let encryptedField4 = field4;
        let encryptedField5 = field5;
        let encryptedComment = comment;

        if (typeof encryptText === 'function' && publicKey) {
            if (field1) encryptedField1 = await encryptText(field1);
            if (field2) encryptedField2 = await encryptText(field2);
            if (field3) encryptedField3 = await encryptText(field3);
            if (field4) encryptedField4 = await encryptText(field4);
            if (field5) encryptedField5 = await encryptText(field5);
            if (comment) encryptedComment = await encryptText(comment);
        }

        const response = await apiRequest(`/pwa/groups/${currentGroup.id}/reports/extended`, 'POST', {
            groupId: currentGroup.id,
            field1Value: encryptedField1 || null,
            field2Value: encryptedField2 || null,
            field3Value: encryptedField3 || null,
            field4Value: encryptedField4 || null,
            field5Value: encryptedField5 || null,
            comment: encryptedComment || null
        });

        if (response.success) {
            showToast('Розширений звіт відправлено!', 'success');
            closeModal('extendedReportModal');
            // Прибираємо терміновий статус після відправки
            clearUrgentReportForGroup(currentGroup.id);
            // Оновлюємо список звітів
            if (typeof loadReportsScreen === 'function') {
                loadReportsScreen();
            }
        } else {
            showToast(response.message || 'Помилка відправки', 'error');
        }
    } catch (error) {
        showToast(error.message || 'Помилка відправки', 'error');
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
    const isCurrentlyEnabled = toggle.classList.contains('active');

    if (isCurrentlyEnabled) {
        // Disable notifications
        toggle.classList.remove('active');
        await updateNotificationsOnServer(false);
        showToast('Сповіщення вимкнено', 'success');
    } else {
        // Enable notifications - first request browser permission
        const permission = await requestNotificationPermission();
        if (permission === 'granted') {
            toggle.classList.add('active');
            await subscribeToPush();
            await updateNotificationsOnServer(true);
        }
    }
}

async function updateNotificationsOnServer(enabled) {
    try {
        const response = await apiRequest('/user/notifications', 'PUT', { enabled });
        if (!response.success) {
            console.error('[Notifications] Failed to update on server:', response.message);
        }
    } catch (error) {
        console.error('[Notifications] Error updating on server:', error);
    }
}

async function loadNotificationsSettingFromServer() {
    try {
        const response = await apiRequest('/user/notifications', 'GET');
        if (response.success && response.data) {
            const toggle = document.getElementById('notificationsToggle');
            if (response.data.enabled) {
                toggle.classList.add('active');
            } else {
                toggle.classList.remove('active');
            }
        }
    } catch (error) {
        console.error('[Notifications] Error loading setting:', error);
    }
}

async function requestNotificationPermission() {
    // Check if iOS
    if (isIOS()) {
        if (!isStandalonePWA()) {
            // iOS but not installed as PWA
            showToast('Спочатку встановіть додаток: Поділитися → На Початковий екран', 'info');
            return 'denied';
        }
        // iOS 16.4+ supports push in standalone mode
        if (!('Notification' in window)) {
            showToast('Оновіть iOS до версії 16.4+ для сповіщень', 'info');
            return 'denied';
        }
    }

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
        console.log('[FCM] Starting push subscription...');

        // Register Firebase messaging service worker
        const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
        console.log('[FCM] Service worker registered:', registration);

        // Initialize Firebase Messaging
        const messaging = firebase.messaging();

        // VAPID key from Firebase Console → Project Settings → Cloud Messaging → Web Push certificates
        const VAPID_KEY = 'BHRuaJ0HVXT2dm5GiMxsprIzC7G03hewx4Z0qf4LIERah3lgwi7fhsCKdEJzhKSGHUGFlCSgiObTo2xIrRvY-Y8';

        console.log('[FCM] Requesting token with VAPID key:', VAPID_KEY.substring(0, 20) + '...');

        // Get FCM token
        const fcmToken = await messaging.getToken({
            vapidKey: VAPID_KEY,
            serviceWorkerRegistration: registration
        });

        if (fcmToken) {
            console.log('[FCM] Token received:', fcmToken.substring(0, 20) + '...');

            // Send token to backend
            await sendFcmTokenToBackend(fcmToken);

            // Listen for token refresh
            messaging.onMessage((payload) => {
                console.log('[FCM] Foreground message received:', payload);
                showForegroundNotification(payload);
            });

            return fcmToken;
        } else {
            console.log('[FCM] No token available');
            return null;
        }
    } catch (error) {
        console.error('[FCM] Push subscription failed:', error);

        // Handle specific errors
        if (error.code === 'messaging/permission-blocked') {
            showToast('Сповіщення заблоковані браузером', 'error');
        } else if (error.code === 'messaging/unsupported-browser') {
            showToast('Браузер не підтримує push-сповіщення', 'error');
        }

        return null;
    }
}

async function sendFcmTokenToBackend(fcmToken) {
    try {
        const response = await apiRequest('/pwa/fcm-token', 'POST', {
            token: fcmToken,
            deviceType: 'WEB'
        });

        if (response.success) {
            console.log('[FCM] Token saved to backend');
            localStorage.setItem('zvit_fcm_token', fcmToken);
        } else {
            console.error('[FCM] Failed to save token:', response.message);
        }
    } catch (error) {
        console.error('[FCM] Error sending token to backend:', error);
    }
}

function showForegroundNotification(payload) {
    console.log('[FCM] showForegroundNotification:', payload);
    const data = payload.data || {};
    const messageType = data.type;

    // Обробляємо терміновий звіт
    if (messageType === 'URGENT_REPORT') {
        handleUrgentReportFromPush(data);
        // Показуємо toast вже в handleUrgentReportFromPush
        return;
    }

    // Обробляємо оновлення налаштувань
    if (messageType === 'SETTINGS_UPDATE') {
        handleSettingsUpdateFromPush(data);
        // Показуємо toast вже в handleSettingsUpdateFromPush
        return;
    }

    // Для інших типів - звичайний toast
    const title = payload.notification?.title || data.title || 'ZVIT';
    const body = payload.notification?.body || data.body || '';

    showToast(`${title}: ${body}`, 'info');

    // Also show browser notification if page is not focused
    if (document.hidden && Notification.permission === 'granted') {
        new Notification(title, {
            body: body,
            icon: '/icons/icon-192x192.png',
            tag: 'zvit-foreground'
        });
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

// Calculate next report time based on schedule
function calculateNextReportTime(group) {
    if (!group || !group.scheduleType) return null;

    const now = new Date();
    const currentMinutes = now.getHours() * 60 + now.getMinutes();

    if (group.scheduleType === 'FIXED_TIMES' && group.fixedTimes && group.fixedTimes.length > 0) {
        // Fixed times schedule
        let nextTime = null;
        let minDiff = Infinity;

        for (const time of group.fixedTimes) {
            const [hours, minutes] = time.split(':').map(Number);
            const timeInMinutes = hours * 60 + minutes;
            const diff = timeInMinutes - currentMinutes;

            if (diff > 0 && diff < minDiff) {
                minDiff = diff;
                nextTime = time;
            }
        }

        // If no time found today, take first time (tomorrow)
        if (!nextTime && group.fixedTimes.length > 0) {
            nextTime = group.fixedTimes[0];
        }

        return nextTime;

    } else if (group.scheduleType === 'INTERVAL' && group.intervalMinutes && group.intervalStartTime) {
        // Interval schedule
        const [startHours, startMinutes] = group.intervalStartTime.split(':').map(Number);
        const startTotalMinutes = startHours * 60 + startMinutes;
        const interval = group.intervalMinutes;

        // Calculate how many intervals have passed since start
        let minutesSinceStart = currentMinutes - startTotalMinutes;
        if (minutesSinceStart < 0) {
            minutesSinceStart += 24 * 60; // Add a day
        }

        const intervalsPassed = Math.floor(minutesSinceStart / interval);
        let nextReportMinutes = startTotalMinutes + (intervalsPassed + 1) * interval;

        // Wrap around 24 hours
        nextReportMinutes = nextReportMinutes % (24 * 60);

        const nextHours = Math.floor(nextReportMinutes / 60);
        const nextMins = nextReportMinutes % 60;

        return `${nextHours.toString().padStart(2, '0')}:${nextMins.toString().padStart(2, '0')}`;
    }

    return null;
}

function parseServerDate(dateString) {
    if (!dateString) return null;
    // Якщо дата без таймзони - трактуємо як Київський час
    if (dateString.includes('T') && !dateString.includes('Z') && !dateString.includes('+') && !dateString.includes('-', 10)) {
        // Формат "2025-01-03T14:30:00" - сервер надсилає час в Europe/Kiev
        // Визначаємо offset для Києва на цю дату
        const tempDate = new Date(dateString + 'Z'); // Парсимо як UTC
        const kyivOffset = getKyivOffset(tempDate);
        // Віднімаємо offset щоб отримати правильний UTC
        return new Date(tempDate.getTime() - kyivOffset * 60 * 1000);
    }
    return new Date(dateString);
}

function getKyivOffset(date) {
    // Київ: UTC+2 взимку (EET), UTC+3 влітку (EEST)
    // Літній час: остання неділя березня о 03:00 -> 04:00
    // Зимовий час: остання неділя жовтня о 04:00 -> 03:00
    const year = date.getUTCFullYear();
    const marchLastSunday = getLastSunday(year, 2); // березень = 2
    const octoberLastSunday = getLastSunday(year, 9); // жовтень = 9

    // Літній час починається о 01:00 UTC останньої неділі березня
    const dstStart = new Date(Date.UTC(year, 2, marchLastSunday, 1, 0, 0));
    // Зимовий час починається о 01:00 UTC останньої неділі жовтня
    const dstEnd = new Date(Date.UTC(year, 9, octoberLastSunday, 1, 0, 0));

    if (date >= dstStart && date < dstEnd) {
        return 180; // UTC+3 (EEST) = 180 хвилин
    }
    return 120; // UTC+2 (EET) = 120 хвилин
}

function getLastSunday(year, month) {
    // Знаходимо останню неділю місяця
    const lastDay = new Date(Date.UTC(year, month + 1, 0)); // останній день місяця
    const dayOfWeek = lastDay.getUTCDay(); // 0 = неділя
    return lastDay.getUTCDate() - dayOfWeek;
}

function formatTime(dateString) {
    if (!dateString) return '';
    const date = parseServerDate(dateString);
    if (!date) return '';
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

// Handle system back button
window.addEventListener('popstate', (e) => {
    e.preventDefault();
    handleSystemBack();
});

function handleSystemBack() {
    const currentScreen = document.querySelector('.screen.active');
    const authScreens = ['loginScreen', 'registerScreen', 'verifyScreen'];
    const mainScreens = ['mainScreen', 'reportsScreen', 'settingsScreen'];

    // If on auth screens, do nothing
    if (authScreens.includes(currentScreen.id)) {
        return;
    }

    // Check if any modal is open - close it first
    const activeModal = document.querySelector('.modal.active');
    if (activeModal) {
        activeModal.classList.remove('active');
        history.pushState({}, '', ''); // Re-push state
        return;
    }

    // If on main tabs (reportsScreen, mainScreen, settingsScreen)
    if (mainScreens.includes(currentScreen.id)) {
        // Double-press to exit
        const now = Date.now();
        if (now - lastBackPressTime < 2000) {
            // User pressed back twice within 2 seconds - allow exit
            // On PWA we can't really exit, but we can let the default behavior happen
            window.close(); // This may not work on all browsers
            return;
        } else {
            lastBackPressTime = now;
            showToast('Натисніть ще раз для виходу', 'info');
            history.pushState({}, '', ''); // Re-push state to prevent actual back
            return;
        }
    }

    // For other screens, navigate back in history
    if (navigationStack.length > 1) {
        navigationStack.pop(); // Remove current screen
        const previousScreen = navigationStack[navigationStack.length - 1];
        showScreen(previousScreen, false);
    } else {
        // Fallback to reports screen
        showScreen('reportsScreen', false);
        navigationStack = ['reportsScreen'];
    }

    // Re-push state to maintain navigation
    history.pushState({}, '', '');
}

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

// ==================== QR Scanner ====================
let html5QrCode = null;

function openQrScanner() {
    if (!currentGroup || !currentGroup.id) {
        showToast('Спочатку оберіть групу', 'error');
        return;
    }

    const modal = document.getElementById('qrScannerModal');
    modal.classList.add('active');
    modal.style.display = 'flex';

    // Ініціалізуємо сканер
    setTimeout(() => {
        startQrScanner();
    }, 300);
}

function closeQrScanner() {
    stopQrScanner();
    closeModal('qrScannerModal');
}

async function startQrScanner() {
    try {
        if (html5QrCode) {
            await html5QrCode.stop();
        }
    } catch (e) {
        console.log('QR scanner was not running');
    }

    html5QrCode = new Html5Qrcode("qrReader");

    const config = {
        fps: 10,
        qrbox: { width: 250, height: 250 },
        aspectRatio: 1.0
    };

    try {
        await html5QrCode.start(
            { facingMode: "environment" },
            config,
            onQrCodeScanned,
            (errorMessage) => {
                // Ігноруємо помилки сканування (нормально коли QR не знайдено)
            }
        );
    } catch (err) {
        console.error('Error starting QR scanner:', err);
        showToast('Не вдалося запустити камеру. Перевірте дозволи.', 'error');
        closeQrScanner();
    }
}

async function stopQrScanner() {
    if (html5QrCode) {
        try {
            await html5QrCode.stop();
            html5QrCode = null;
        } catch (e) {
            console.log('Error stopping QR scanner:', e);
        }
    }
}

async function onQrCodeScanned(decodedText) {
    console.log('[PWA] QR code scanned:', decodedText);

    // Зупиняємо сканер
    await stopQrScanner();

    // Витягуємо токен з URL
    const token = extractTokenFromQrUrl(decodedText);

    if (!token) {
        showToast('Невірний QR код', 'error');
        // Перезапускаємо сканер
        setTimeout(() => startQrScanner(), 1000);
        return;
    }

    // Авторизуємо сесію
    await authorizeQrSession(token);
}

function extractTokenFromQrUrl(url) {
    try {
        const urlObj = new URL(url);
        return urlObj.searchParams.get('token');
    } catch (e) {
        console.error('Error parsing QR URL:', e);
        return null;
    }
}

async function authorizeQrSession(sessionToken) {
    if (!currentGroup || !currentGroup.id) {
        showToast('Групу не обрано', 'error');
        closeQrScanner();
        return;
    }

    try {
        // Використовуємо fetch напряму, бо endpoint не має /v1 префіксу
        const token = localStorage.getItem('zvit_token');
        const fetchResponse = await fetch('/api/web-auth/authorize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                sessionToken: sessionToken,
                groupId: currentGroup.id
            })
        });
        const response = await fetchResponse.json();

        if (response.success) {
            showToast('✅ Веб сесію авторизовано!', 'success');
            closeQrScanner();
        } else {
            showToast(response.message || 'Помилка авторизації', 'error');
            // Перезапускаємо сканер
            setTimeout(() => startQrScanner(), 1000);
        }
    } catch (error) {
        console.error('Error authorizing QR session:', error);
        showToast(error.message || 'Помилка авторизації', 'error');
        // Перезапускаємо сканер
        setTimeout(() => startQrScanner(), 1000);
    }
}
