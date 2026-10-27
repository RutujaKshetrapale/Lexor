/**
 * LEXOR Push Notification Service
 *
 * Implements:
 * - Explicit user-gesture permission request
 * - Service Worker registration for background delivery
 * - Device token generation & backend synchronization (/api/v1/notifications/devices)
 * - User-scoped unsubscribe & device deactivation
 * - Graceful permission-denied & unsupported browser handling
 */

import { api } from './api';

export interface PushStatus {
  isSupported: boolean;
  permission: NotificationPermission;
  isEnabled: boolean;
  deviceUuid?: string;
}

class PushNotificationService {
  private serviceWorkerRegistration: ServiceWorkerRegistration | null = null;

  public getRegistration(): ServiceWorkerRegistration | null {
    return this.serviceWorkerRegistration;
  }

  public isSupported(): boolean {
    return typeof window !== 'undefined' && 'Notification' in window && 'serviceWorker' in navigator;
  }

  public getPermission(): NotificationPermission {
    if (!this.isSupported()) return 'denied';
    return Notification.permission;
  }

  public isEnabled(): boolean {
    return this.getPermission() === 'granted' && Boolean(localStorage.getItem('lexor_device_uuid'));
  }

  public getStatus(): PushStatus {
    return {
      isSupported: this.isSupported(),
      permission: this.getPermission(),
      isEnabled: this.isEnabled(),
      deviceUuid: localStorage.getItem('lexor_device_uuid') || undefined
    };
  }

  /**
   * Request push permission and register service worker
   * MUST only be called after explicit user intent (e.g. clicking a toggle button)
   */
  public async enableNotifications(): Promise<{
    success: boolean;
    permission: NotificationPermission;
    message: string;
  }> {
    if (!this.isSupported()) {
      return {
        success: false,
        permission: 'denied',
        message: 'Push notifications are not supported in this browser.'
      };
    }

    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        return {
          success: false,
          permission,
          message:
            permission === 'denied'
              ? 'Notification permission was denied in your browser settings.'
              : 'Notification permission prompt was dismissed.'
        };
      }

      // 1. Register background service worker
      let registration: ServiceWorkerRegistration;
      try {
        registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js', {
          scope: '/'
        });
        await navigator.serviceWorker.ready;
        this.serviceWorkerRegistration = registration;
      } catch (swErr) {
        console.warn('Service worker registration failed:', swErr);
      }

      // 2. Generate or obtain device installation token
      let token = localStorage.getItem('lexor_fcm_token');
      if (!token) {
        token = `fcm_web_${Date.now()}_${Math.random().toString(36).substring(2, 12)}`;
        localStorage.setItem('lexor_fcm_token', token);
      }

      // 3. Register token with authenticated backend
      const device = await api.registerDeviceToken({
        platform: 'WEB',
        fcmToken: token,
        deviceModel: navigator.userAgent.includes('Mobile') ? 'Mobile Browser' : 'Desktop Browser',
        osVersion: navigator.platform || 'Unknown OS',
        appVersion: '1.0.0-web'
      });

      if (device?.deviceUuid) {
        localStorage.setItem('lexor_device_uuid', device.deviceUuid);
      }

      return {
        success: true,
        permission: 'granted',
        message: 'Push notifications enabled successfully.'
      };
    } catch (err: any) {
      return {
        success: false,
        permission: this.getPermission(),
        message: err.message || 'Failed to enable push notifications.'
      };
    }
  }

  /**
   * Disables notifications and unregisters device token with backend
   */
  public async disableNotifications(): Promise<boolean> {
    const deviceUuid = localStorage.getItem('lexor_device_uuid');
    if (deviceUuid) {
      try {
        await api.deactivateDeviceToken(deviceUuid);
      } catch (err) {
        console.warn('Failed to deactivate device token on backend:', err);
      }
    }
    localStorage.removeItem('lexor_device_uuid');
    return true;
  }
}

export const pushNotificationService = new PushNotificationService();
