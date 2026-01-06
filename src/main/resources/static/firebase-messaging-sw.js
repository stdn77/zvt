// Firebase Messaging Service Worker for PWA
// This handles background push notifications

importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.7.1/firebase-messaging-compat.js');

// Firebase configuration
firebase.initializeApp({
    apiKey: "AIzaSyAqt2bGMmhFNZ8ClYwraWmogo7WjhvtMVA",
    authDomain: "zvit-b9ed2.firebaseapp.com",
    projectId: "zvit-b9ed2",
    storageBucket: "zvit-b9ed2.firebasestorage.app",
    messagingSenderId: "1049422631668",
    appId: "1:1049422631668:web:5bc66bca4521cd0fb204a1"
});

const messaging = firebase.messaging();

// Handle background messages
messaging.onBackgroundMessage((payload) => {
    console.log('[firebase-messaging-sw.js] Received background message:', payload);

    const data = payload.data || {};
    const messageType = data.type;

    // Повідомляємо клієнтів про терміновий звіт
    if (messageType === 'URGENT_REPORT') {
        notifyClientsAboutMessage('URGENT_REPORT_RECEIVED', data);
    }

    // Повідомляємо клієнтів про зміну налаштувань
    if (messageType === 'SETTINGS_UPDATE') {
        notifyClientsAboutMessage('SETTINGS_UPDATE_RECEIVED', data);
    }

    const notificationTitle = payload.notification?.title || data.title || 'ZVIT';
    const notificationOptions = {
        body: payload.notification?.body || data.body || 'Час звітувати!',
        icon: '/icons/icon-192x192.png',
        badge: '/icons/icon-72x72.png',
        tag: messageType === 'URGENT_REPORT' ? 'urgent-' + data.groupId : (data.tag || 'zvit-reminder'),
        data: {
            url: data.url || '/app',
            groupId: data.groupId,
            groupName: data.groupName,
            type: messageType,
            deadlineMinutes: data.deadlineMinutes,
            urgentSessionId: data.urgentSessionId
        },
        vibrate: [200, 100, 200],
        requireInteraction: messageType === 'URGENT_REPORT',
        actions: [
            { action: 'open', title: 'Відкрити' },
            { action: 'dismiss', title: 'Закрити' }
        ]
    };

    return self.registration.showNotification(notificationTitle, notificationOptions);
});

// Notify all clients about a message
async function notifyClientsAboutMessage(type, data) {
    const windowClients = await clients.matchAll({ type: 'window', includeUncontrolled: true });
    console.log('[firebase-messaging-sw.js] Notifying', windowClients.length, 'clients about', type);

    for (const client of windowClients) {
        client.postMessage({ type, data });
    }
}

// Handle notification click
self.addEventListener('notificationclick', (event) => {
    console.log('[firebase-messaging-sw.js] Notification click:', event);

    event.notification.close();

    if (event.action === 'dismiss') {
        return;
    }

    const notificationData = event.notification.data || {};
    const urlToOpen = notificationData.url || '/app';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            // Check if app is already open
            for (const client of clientList) {
                if (client.url.includes('/app') && 'focus' in client) {
                    // Send message to existing client
                    client.postMessage({
                        type: 'NOTIFICATION_CLICK',
                        data: notificationData
                    });

                    // If urgent report - also send the data to save it
                    if (notificationData.type === 'URGENT_REPORT') {
                        client.postMessage({
                            type: 'URGENT_REPORT_RECEIVED',
                            data: notificationData
                        });
                    }

                    return client.focus();
                }
            }
            // Open new window
            if (clients.openWindow) {
                return clients.openWindow(urlToOpen);
            }
        })
    );
});
