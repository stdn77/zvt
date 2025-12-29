// ZVIT PWA Service Worker
const CACHE_NAME = 'zvit-cache-v1';
const STATIC_CACHE = 'zvit-static-v1';
const DYNAMIC_CACHE = 'zvit-dynamic-v1';

// Статичні ресурси для кешування
const STATIC_ASSETS = [
  '/app',
  '/manifest.json',
  '/icons/icon.svg',
  '/pwa/js/app.js'
];

// Встановлення Service Worker
self.addEventListener('install', (event) => {
  console.log('[SW] Installing Service Worker...');
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('[SW] Precaching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .catch((err) => {
        console.log('[SW] Precaching failed:', err);
      })
  );
  self.skipWaiting();
});

// Активація Service Worker
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating Service Worker...');
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== STATIC_CACHE && name !== DYNAMIC_CACHE)
          .map((name) => {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      );
    })
  );
  self.clients.claim();
});

// Стратегія кешування: Network First для API, Cache First для статики
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Пропускаємо non-GET запити
  if (request.method !== 'GET') {
    return;
  }

  // API запити - Network First
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request));
    return;
  }

  // Статичні ресурси - Cache First
  event.respondWith(cacheFirst(request));
});

// Network First стратегія
async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(DYNAMIC_CACHE);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    // Повертаємо офлайн відповідь для API
    return new Response(
      JSON.stringify({ success: false, message: 'Ви офлайн' }),
      { headers: { 'Content-Type': 'application/json' } }
    );
  }
}

// Cache First стратегія
async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) {
    return cached;
  }
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(STATIC_CACHE);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    // Повертаємо офлайн сторінку
    return caches.match('/app');
  }
}

// Push нотифікації
self.addEventListener('push', (event) => {
  console.log('[SW] Push received:', event);

  let data = { title: 'ZVIT', body: 'Нове повідомлення' };

  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data.body = event.data.text();
    }
  }

  const options = {
    body: data.body || data.message,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-72x72.png',
    vibrate: [100, 50, 100],
    data: {
      url: data.url || '/app',
      reportId: data.reportId,
      groupId: data.groupId
    },
    actions: [
      { action: 'open', title: 'Відкрити' },
      { action: 'close', title: 'Закрити' }
    ],
    tag: data.tag || 'zvit-notification',
    renotify: true
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

// Клік по нотифікації
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification click:', event);
  event.notification.close();

  if (event.action === 'close') {
    return;
  }

  const urlToOpen = event.notification.data?.url || '/app';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        // Шукаємо вже відкрите вікно
        for (const client of windowClients) {
          if (client.url.includes('/app') && 'focus' in client) {
            client.postMessage({
              type: 'NOTIFICATION_CLICK',
              data: event.notification.data
            });
            return client.focus();
          }
        }
        // Відкриваємо нове вікно
        if (clients.openWindow) {
          return clients.openWindow(urlToOpen);
        }
      })
  );
});

// Синхронізація у фоні (для відправки звітів офлайн)
self.addEventListener('sync', (event) => {
  console.log('[SW] Background sync:', event.tag);

  if (event.tag === 'sync-reports') {
    event.waitUntil(syncPendingReports());
  }
});

// Синхронізація відкладених звітів
async function syncPendingReports() {
  try {
    const cache = await caches.open('zvit-pending-reports');
    const requests = await cache.keys();

    for (const request of requests) {
      const response = await cache.match(request);
      const reportData = await response.json();

      try {
        const result = await fetch('/api/v1/reports', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${reportData.token}`
          },
          body: JSON.stringify(reportData.report)
        });

        if (result.ok) {
          await cache.delete(request);
          console.log('[SW] Report synced successfully');
        }
      } catch (e) {
        console.log('[SW] Report sync failed, will retry');
      }
    }
  } catch (error) {
    console.error('[SW] Sync failed:', error);
  }
}
