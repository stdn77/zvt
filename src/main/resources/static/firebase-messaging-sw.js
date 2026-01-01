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

    const notificationTitle = payload.notification?.title || payload.data?.title || 'ZVIT';
    const notificationOptions = {
        body: payload.notification?.body || payload.data?.body || 'Час звітувати!',
        icon: '/icons/icon-192x192.png',
        badge: '/icons/icon-72x72.png',
        tag: payload.data?.tag || 'zvit-reminder',
        data: {
            url: payload.data?.url || '/app',
            groupId: payload.data?.groupId,
            groupName: payload.data?.groupName
        },
        vibrate: [200, 100, 200],
        requireInteraction: true,
        actions: [
            { action: 'open', title: 'Відкрити' },
            { action: 'dismiss', title: 'Закрити' }
        ]
    };

    return self.registration.showNotification(notificationTitle, notificationOptions);
});

// Handle notification click
self.addEventListener('notificationclick', (event) => {
    console.log('[firebase-messaging-sw.js] Notification click:', event);

    event.notification.close();

    const urlToOpen = event.notification.data?.url || '/app';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
            // Check if app is already open
            for (const client of clientList) {
                if (client.url.includes('/app') && 'focus' in client) {
                    // Send message to existing client
                    client.postMessage({
                        type: 'NOTIFICATION_CLICK',
                        data: event.notification.data
                    });
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
