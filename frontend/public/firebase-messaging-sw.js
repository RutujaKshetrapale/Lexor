/**
 * LEXOR Service Worker for Background Web Push Notifications (FCM)
 */

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  if (!event.data) return;

  try {
    const payload = event.data.json();
    const notificationTitle = payload.notification?.title || payload.title || 'LEXOR Alert';
    const notificationOptions = {
      body: payload.notification?.body || payload.body || 'You have a new update from LEXOR Mobility.',
      icon: '/favicon.ico',
      badge: '/favicon.ico',
      data: payload.data || {},
      tag: payload.data?.notificationUuid || 'lexor-alert',
      renotify: true
    };

    event.waitUntil(
      self.registration.showNotification(notificationTitle, notificationOptions)
    );
  } catch {
    const text = event.data.text();
    event.waitUntil(
      self.registration.showNotification('LEXOR Mobility', {
        body: text,
        icon: '/favicon.ico'
      })
    );
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const deepLink = event.notification.data?.deepLink || '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.navigate(deepLink);
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(deepLink);
      }
    })
  );
});
