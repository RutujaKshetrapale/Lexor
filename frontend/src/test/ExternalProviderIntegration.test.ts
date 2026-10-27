import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mapProvider } from '../services/mapProvider';
import { paymentProvider } from '../services/paymentProvider';
import { pushNotificationService } from '../services/pushNotificationService';
import { api } from '../services/api';

describe('Phase 17 External Providers Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe('Map Provider & Geolocation Engine', () => {
    it('detects default OSRM/OpenStreetMap engine with real road routing capability', () => {
      const config = mapProvider.getProviderConfig();
      expect(config.provider).toBeDefined();
      expect(config.isLiveProvider).toBe(true);
      expect(config.label).toContain('OpenStreetMap');
    });

    it('evaluates driver GPS telemetry freshness correctly across age boundaries', () => {
      const now = new Date();

      // < 30 seconds: Fresh Live GPS
      const freshDate = new Date(now.getTime() - 10 * 1000).toISOString();
      const freshStatus = mapProvider.evaluateFreshness(freshDate);
      expect(freshStatus.isFresh).toBe(true);
      expect(freshStatus.isStale).toBe(false);
      expect(freshStatus.color).toBe('emerald');

      // 30 - 120 seconds: Stale GPS
      const staleDate = new Date(now.getTime() - 65 * 1000).toISOString();
      const staleStatus = mapProvider.evaluateFreshness(staleDate);
      expect(staleStatus.isFresh).toBe(false);
      expect(staleStatus.isStale).toBe(true);
      expect(staleStatus.color).toBe('amber');

      // > 120 seconds: Offline GPS
      const offlineDate = new Date(now.getTime() - 300 * 1000).toISOString();
      const offlineStatus = mapProvider.evaluateFreshness(offlineDate);
      expect(offlineStatus.isFresh).toBe(false);
      expect(offlineStatus.isOffline).toBe(true);
      expect(offlineStatus.color).toBe('rose');
    });

    it('computes road bearing angle accurately between geographic coordinates', () => {
      const start = { latitude: 12.9716, longitude: 77.5946 };
      const north = { latitude: 13.0, longitude: 77.5946 };
      const bearing = mapProvider.calculateBearing(start, north);
      expect(bearing).toBeCloseTo(0, 0);
    });

    it('generates authoritative road geometry or smooth geodesic fallback', async () => {
      const pickup = { latitude: 12.9716, longitude: 77.5946, address: 'MG Road' };
      const drop = { latitude: 12.9352, longitude: 77.6245, address: 'Koramangala' };
      const route = await mapProvider.getRouteGeometry(pickup, drop);
      expect(route).toBeDefined();
      expect(route.distanceKm).toBeGreaterThan(0);
      expect(route.durationMinutes).toBeGreaterThan(0);
      expect(route.coordinates.length).toBeGreaterThan(1);
    });
  });

  describe('Digital Payments Provider (Razorpay Boundary)', () => {
    it('initializes payment gateway info without exposing private secrets', () => {
      const info = paymentProvider.getGatewayInfo();
      expect(info.name).toContain('Razorpay');
      expect(info.keyIdPreview).toBeUndefined(); // Test mode without key
    });

    it('launches tokenized checkout and returns authoritative sandbox signature when in sandbox mode', async () => {
      const result = await paymentProvider.openCheckout({
        paymentUuid: 'pmt-test-12345',
        orderId: 'order_test_98765',
        amount: 340,
        amountPaise: 34000,
        currency: 'INR',
        paymentMethod: 'UPI'
      });

      expect(result.success).toBe(true);
      expect(result.paymentUuid).toBe('pmt-test-12345');
      expect(result.providerPaymentId).toBeDefined();
      expect(result.providerSignature).toBeDefined();
      expect(result.status).toBe('SUCCEEDED');
    });
  });

  describe('Push Notifications Service (FCM & Browser Service Worker)', () => {
    it('detects browser support accurately', () => {
      const supported = pushNotificationService.isSupported();
      expect(typeof supported).toBe('boolean');
    });

    it('returns permission status accurately', () => {
      const status = pushNotificationService.getStatus();
      expect(status).toHaveProperty('permission');
      expect(status).toHaveProperty('isEnabled');
    });

    it('disables notifications cleanly and removes stored device token', async () => {
      vi.spyOn(api, 'deactivateDeviceToken').mockResolvedValue(undefined);
      localStorage.setItem('lexor_device_uuid', 'dev-uuid-test');
      const deactivated = await pushNotificationService.disableNotifications();
      expect(deactivated).toBe(true);
      expect(localStorage.getItem('lexor_device_uuid')).toBeNull();
    });
  });
});
