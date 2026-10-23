/**
 * LEXOR Real-Time Ride Infrastructure & Event Streaming Service
 *
 * Implements authoritative event handling for:
 * - Driver dispatch offers
 * - Booking confirmations & cancellations
 * - Driver arrival & OTP-verified trip start
 * - Real device driver location streaming
 * - Trip completion & Payment status updates
 *
 * Uses Server-Sent Events (SSE) via the API Gateway (/api/v1/notifications/me/stream)
 * with automatic reconnection, exponential backoff, event deduplication, and
 * fallback state synchronization.
 */

import { getAuthToken } from './api';

export type RealtimeEventType =
  | 'DRIVER_OFFER'
  | 'BOOKING_ACCEPTED'
  | 'DRIVER_ARRIVED'
  | 'TRIP_STARTED'
  | 'DRIVER_LOCATION_UPDATE'
  | 'TRIP_COMPLETED'
  | 'BOOKING_CANCELLED'
  | 'PAYMENT_STATUS_UPDATE'
  | 'SYSTEM_ALERT';

export type RealtimeConnectionStatus =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'RECONNECTING'
  | 'POLLING_FALLBACK';

export interface DriverLocationPayload {
  driverUuid: string;
  bookingUuid?: string;
  latitude: number;
  longitude: number;
  heading: number;
  speedKmh?: number;
  timestamp: string;
  isStale?: boolean;
}

export interface RealtimeEvent<T = any> {
  eventId: string;
  eventType: RealtimeEventType;
  timestamp: string;
  payload: T;
}

type EventListener = (payload: any) => void;
type StatusListener = (status: RealtimeConnectionStatus) => void;
type SyncListener = () => void;

class RealtimeService {
  private status: RealtimeConnectionStatus = 'DISCONNECTED';
  private listeners: Map<RealtimeEventType, Set<EventListener>> = new Map();
  private statusListeners: Set<StatusListener> = new Set();
  private syncListeners: Set<SyncListener> = new Set();
  private abortController: AbortController | null = null;
  private reconnectTimeoutId: any = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private processedEventIds = new Set<string>();
  private maxEventHistory = 200;
  private pollingIntervalId: any = null;

  constructor() {
    // Empty constructor
  }

  public getStatus(): RealtimeConnectionStatus {
    return this.status;
  }

  private setStatus(newStatus: RealtimeConnectionStatus): void {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.statusListeners.forEach((listener) => {
        try {
          listener(newStatus);
        } catch (err) {
          console.error('[RealtimeService] Status listener error:', err);
        }
      });
    }
  }

  public onStatusChange(callback: StatusListener): () => void {
    this.statusListeners.add(callback);
    callback(this.status);
    return () => this.statusListeners.delete(callback);
  }

  public onStateSync(callback: SyncListener): () => void {
    this.syncListeners.add(callback);
    return () => this.syncListeners.delete(callback);
  }

  public subscribe<T = any>(
    eventType: RealtimeEventType,
    callback: (payload: T) => void
  ): () => void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    const set = this.listeners.get(eventType)!;
    set.add(callback);

    // Auto-connect if not already connected
    if (this.status === 'DISCONNECTED') {
      this.connect();
    }

    return () => {
      set.delete(callback);
      if (set.size === 0) {
        this.listeners.delete(eventType);
      }
    };
  }

  public triggerEvent(eventType: RealtimeEventType, payload: any, eventId?: string): void {
    const id = eventId || `local_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    if (this.processedEventIds.has(id)) {
      return; // Deduplication
    }
    this.recordEventId(id);

    const listeners = this.listeners.get(eventType);
    if (listeners && listeners.size > 0) {
      listeners.forEach((listener) => {
        try {
          listener(payload);
        } catch (err) {
          console.error(`[RealtimeService] Error executing listener for ${eventType}:`, err);
        }
      });
    }
  }

  private recordEventId(id: string): void {
    this.processedEventIds.add(id);
    if (this.processedEventIds.size > this.maxEventHistory) {
      const first = this.processedEventIds.values().next().value;
      if (first) this.processedEventIds.delete(first);
    }
  }

  public async connect(): Promise<void> {
    const token = getAuthToken();
    if (!token) {
      this.setStatus('DISCONNECTED');
      return;
    }

    if (this.status === 'CONNECTED' || this.status === 'CONNECTING') {
      return;
    }

    if (this.reconnectTimeoutId) {
      clearTimeout(this.reconnectTimeoutId);
      this.reconnectTimeoutId = null;
    }

    this.setStatus(this.reconnectAttempts > 0 ? 'RECONNECTING' : 'CONNECTING');
    this.abortController = new AbortController();

    try {
      const streamUrl = '/api/v1/notifications/me/stream';
      const response = await fetch(streamUrl, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
          'Cache-Control': 'no-cache'
        },
        signal: this.abortController.signal
      });

      if (!response.ok || !response.body) {
        throw new Error(`SSE stream failed with status ${response.status}`);
      }

      this.setStatus('CONNECTED');
      this.reconnectAttempts = 0;
      this.stopPollingFallback();

      // Trigger authoritative state sync on successful (re)connection
      this.syncListeners.forEach((listener) => {
        try {
          listener();
        } catch (err) {
          console.error('[RealtimeService] State sync listener error:', err);
        }
      });

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n\n');
        buffer = lines.pop() || '';

        for (const block of lines) {
          this.parseSseBlock(block);
        }
      }

      // Stream closed cleanly by server
      this.handleStreamClose();
    } catch (err: any) {
      if (err.name === 'AbortError') {
        this.setStatus('DISCONNECTED');
        return;
      }
      console.warn('[RealtimeService] Connection error, initiating recovery:', err.message);
      this.handleStreamError();
    }
  }

  private parseSseBlock(block: string): void {
    if (!block.trim()) return;

    let eventType: string = 'MESSAGE';
    let dataStr = '';

    const lines = block.split('\n');
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        dataStr += (dataStr ? '\n' : '') + line.substring(5).trim();
      }
    }

    if (eventType === 'INIT') {
      return;
    }

    if (!dataStr) return;

    try {
      const parsed = JSON.parse(dataStr);
      // Map server notification/event to application domain event
      const targetEventType: RealtimeEventType =
        parsed.type || parsed.eventType || (eventType as RealtimeEventType);

      const eventId = parsed.notificationUuid || parsed.id || parsed.eventId;
      this.triggerEvent(targetEventType, parsed.metadata || parsed.data || parsed, eventId);
    } catch {
      // Non-JSON message
      this.triggerEvent('SYSTEM_ALERT', { message: dataStr });
    }
  }

  private handleStreamClose(): void {
    if (this.status !== 'DISCONNECTED') {
      this.scheduleReconnect();
    }
  }

  private handleStreamError(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[RealtimeService] Maximum reconnect attempts reached. Switching to secure polling fallback.');
      this.startPollingFallback();
      return;
    }

    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    this.setStatus('RECONNECTING');
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 16000);

    this.reconnectTimeoutId = setTimeout(() => {
      this.connect();
    }, delay);
  }

  private startPollingFallback(): void {
    this.setStatus('POLLING_FALLBACK');
    if (this.pollingIntervalId) clearInterval(this.pollingIntervalId);

    // Periodically notify clients to refresh state
    this.pollingIntervalId = setInterval(() => {
      this.syncListeners.forEach((listener) => {
        try {
          listener();
        } catch {
          // ignore
        }
      });
    }, 8000);
  }

  private stopPollingFallback(): void {
    if (this.pollingIntervalId) {
      clearInterval(this.pollingIntervalId);
      this.pollingIntervalId = null;
    }
  }

  public disconnect(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    if (this.reconnectTimeoutId) {
      clearTimeout(this.reconnectTimeoutId);
      this.reconnectTimeoutId = null;
    }
    this.stopPollingFallback();
    this.setStatus('DISCONNECTED');
    this.reconnectAttempts = 0;
  }
}

export const realtimeService = new RealtimeService();
