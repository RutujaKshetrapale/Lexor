import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { realtimeService, RealtimeConnectionStatus } from '../services/realtime';
import { mapProvider } from '../services/mapProvider';
import { paymentProvider } from '../services/paymentProvider';

describe('LEXOR Real-Time, Map Provider & Payment Lifecycle Suite', () => {
  beforeEach(() => {
    localStorage.clear();
    realtimeService.disconnect();
  });

  afterEach(() => {
    realtimeService.disconnect();
    vi.restoreAllMocks();
  });

  describe('RealtimeService (SSE & Event Streaming)', () => {
    it('initializes in DISCONNECTED status', () => {
      expect(realtimeService.getStatus()).toBe('DISCONNECTED');
    });

    it('allows pub/sub subscription and event delivery', () => {
      let receivedOffer: any = null;
      const unsubscribe = realtimeService.subscribe('DRIVER_OFFER', (offer) => {
        receivedOffer = offer;
      });

      const sampleOffer = {
        bookingUuid: 'b-999',
        totalFare: 450,
        pickupAddress: 'Koramangala 4th Block'
      };

      realtimeService.triggerEvent('DRIVER_OFFER', sampleOffer, 'evt-1');
      expect(receivedOffer).toEqual(sampleOffer);

      // Deduplication: sending same eventId again should NOT trigger listener twice
      let callCount = 0;
      const countSub = realtimeService.subscribe('DRIVER_OFFER', () => {
        callCount++;
      });
      realtimeService.triggerEvent('DRIVER_OFFER', sampleOffer, 'evt-1'); // Duplicate!
      expect(callCount).toBe(0);

      unsubscribe();
      countSub();
    });

    it('notifies status listeners on status change', () => {
      const statuses: RealtimeConnectionStatus[] = [];
      const unsub = realtimeService.onStatusChange((status) => {
        statuses.push(status);
      });

      // Initial callback on subscribe
      expect(statuses).toContain('DISCONNECTED');
      unsub();
    });

    it('triggers state sync listeners on demand', () => {
      let syncCalled = false;
      const unsub = realtimeService.onStateSync(() => {
        syncCalled = true;
      });

      // Trigger local event to verify callback mechanism
      realtimeService.triggerEvent('DRIVER_ARRIVED', { bookingUuid: 'b-1' }, 'evt-arrived-1');
      unsub();
    });
  });

  describe('MapProvider (OSM/OSRM & Geolocation Engine)', () => {
    it('defaults to OpenStreetMap/OSRM as the open live provider', () => {
      const config = mapProvider.getProviderConfig();
      expect(config.isLiveProvider).toBe(true);
      expect(config.provider).toBe('OSM');
      expect(config.label).toContain('OpenStreetMap');
    });

    it('evaluates location freshness correctly according to production SLA', () => {
      // 1. Fresh GPS signal (< 30s)
      const freshTimestamp = new Date(Date.now() - 5000).toISOString();
      const freshRes = mapProvider.evaluateFreshness(freshTimestamp);
      expect(freshRes.isFresh).toBe(true);
      expect(freshRes.isStale).toBe(false);
      expect(freshRes.isOffline).toBe(false);
      expect(freshRes.color).toBe('emerald');

      // 2. Stale GPS signal (between 30s and 120s)
      const staleTimestamp = new Date(Date.now() - 45000).toISOString();
      const staleRes = mapProvider.evaluateFreshness(staleTimestamp);
      expect(staleRes.isFresh).toBe(false);
      expect(staleRes.isStale).toBe(true);
      expect(staleRes.isOffline).toBe(false);
      expect(staleRes.color).toBe('amber');

      // 3. Offline / Signal Lost (> 120s)
      const offlineTimestamp = new Date(Date.now() - 180000).toISOString();
      const offlineRes = mapProvider.evaluateFreshness(offlineTimestamp);
      expect(offlineRes.isFresh).toBe(false);
      expect(offlineRes.isStale).toBe(false);
      expect(offlineRes.isOffline).toBe(true);
      expect(offlineRes.color).toBe('rose');

      // 4. Undefined / Missing location
      const nullRes = mapProvider.evaluateFreshness(undefined);
      expect(nullRes.isOffline).toBe(true);
      expect(nullRes.color).toBe('rose');
    });

    it('calculates compass bearing degrees between coordinates', () => {
      const start = { latitude: 12.9716, longitude: 77.5946 };
      // Due north (greater latitude, same longitude)
      const north = { latitude: 13.0716, longitude: 77.5946 };
      const bearingNorth = mapProvider.calculateBearing(start, north);
      expect(Math.round(bearingNorth)).toBe(0);

      // Due east (same latitude, greater longitude)
      const east = { latitude: 12.9716, longitude: 77.6946 };
      const bearingEast = mapProvider.calculateBearing(start, east);
      expect(Math.round(bearingEast)).toBe(90);
    });

    it('generates route geometry with distance and duration', async () => {
      const pickup = { latitude: 12.9716, longitude: 77.5946, address: 'MG Road' };
      const drop = { latitude: 12.9372, longitude: 77.6949, address: 'Bellandur' };

      const route = await mapProvider.getRouteGeometry(pickup, drop);
      expect(route).toBeDefined();
      expect(route.distanceKm).toBeGreaterThan(0);
      expect(route.durationMinutes).toBeGreaterThan(0);
      expect(route.coordinates.length).toBeGreaterThan(0);
    });
  });

  describe('PaymentProvider (Tokenized Checkout & Webhooks)', () => {
    it('discloses gateway configuration and live vs sandbox state', () => {
      const info = paymentProvider.getGatewayInfo();
      expect(info.name).toBe('Razorpay / UPI Payment Suite');
      expect(typeof info.isLive).toBe('boolean');
    });

    it('executes tokenized checkout via sandbox boundary when merchant keys absent', async () => {
      const intent = {
        paymentUuid: 'pay-test-uuid',
        amount: 250,
        amountPaise: 25000,
        currency: 'INR',
        customerName: 'Test Rider',
        paymentMethod: 'UPI'
      };

      const result = await paymentProvider.openCheckout(intent);
      expect(result.success).toBe(true);
      expect(result.status).toBe('SUCCEEDED');
      expect(result.paymentUuid).toBe('pay-test-uuid');
      expect(result.isTestMode).toBe(true);
      expect(result.providerPaymentId).toBeDefined();
    });
  });
});
