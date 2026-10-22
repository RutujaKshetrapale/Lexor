import {
  ApiResponse,
  ExecutiveOverview,
  LiveOperationsSnapshot,
  PlatformTrends,
  ServiceHealthStatus,
  AdminAuditLog,
  AdminSetting,
  PageResponse,
  RideBooking,
  RiderProfile,
  DriverProfile,
  VehicleProfile,
  PaymentRecord,
  FeedbackReview
} from '../types';

const API_BASE = '/api/v1';

// Token helper: retrieves saved token or fallback admin token for authenticated API requests
export function getAdminToken(): string {
  const token = localStorage.getItem('lexor_admin_token');
  if (token) return token;
  // Default mock development token with ROLE_ADMIN
  const defaultAdmin = 'mock-admin-token-role-admin';
  return defaultAdmin;
}

export function setAdminToken(token: string): void {
  localStorage.setItem('lexor_admin_token', token);
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  headers.set('Content-Type', 'application/json');
  headers.set('Authorization', `Bearer ${getAdminToken()}`);

  const res = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers
  });

  if (!res.ok) {
    let errMsg = `Request failed: ${res.status} ${res.statusText}`;
    try {
      const errJson = await res.json();
      if (errJson.message) errMsg = errJson.message;
    } catch {
      // ignore
    }
    throw new Error(errMsg);
  }

  const json: ApiResponse<T> = await res.json();
  return json.data;
}

export const api = {
  // 1. Executive Overview
  getOverview: async (timeRange: string = 'TODAY', start?: string, end?: string): Promise<ExecutiveOverview> => {
    let query = `?timeRange=${encodeURIComponent(timeRange)}`;
    if (start && end) {
      query += `&startDate=${encodeURIComponent(start)}&endDate=${encodeURIComponent(end)}`;
    }
    return request<ExecutiveOverview>(`/admin/operations/overview${query}`);
  },

  // 2. Live Operations
  getLiveSnapshot: async (): Promise<LiveOperationsSnapshot> => {
    return request<LiveOperationsSnapshot>('/admin/operations/live-snapshot');
  },

  emergencyCancelRide: async (bookingUuid: string, reason: string): Promise<string> => {
    return request<string>('/admin/operations/emergency-cancel-ride', {
      method: 'POST',
      body: JSON.stringify({ bookingUuid, reason })
    });
  },

  // 3. Analytics & Trends
  getTrends: async (timeRange: string = '7D'): Promise<PlatformTrends> => {
    return request<PlatformTrends>(`/admin/analytics/trends?timeRange=${encodeURIComponent(timeRange)}`);
  },

  getExportCsvUrl: (reportType: 'RIDES' | 'PAYMENTS' | 'DRIVERS', timeRange: string = '7D'): string => {
    return `${API_BASE}/admin/analytics/export/csv?reportType=${reportType}&timeRange=${timeRange}`;
  },

  // 4. Service Health Monitor
  getServiceHealth: async (): Promise<ServiceHealthStatus> => {
    return request<ServiceHealthStatus>('/admin/health-monitor');
  },

  // 5. Audit Logs
  getAuditLogs: async (page: number = 0, size: number = 20, action?: string): Promise<PageResponse<AdminAuditLog>> => {
    let query = `?page=${page}&size=${size}`;
    if (action) query += `&action=${encodeURIComponent(action)}`;
    return request<PageResponse<AdminAuditLog>>(`/admin/audit-logs${query}`);
  },

  recordAuditLog: async (action: string, resourceType: string, resourceUuid: string, reason: string): Promise<AdminAuditLog> => {
    return request<AdminAuditLog>('/admin/audit-logs', {
      method: 'POST',
      body: JSON.stringify({ action, resourceType, resourceUuid, reason })
    });
  },

  // 6. Admin Settings
  getSettings: async (): Promise<AdminSetting[]> => {
    return request<AdminSetting[]>('/admin/audit-logs/settings');
  },

  updateSetting: async (settingKey: string, settingValue: string, description?: string): Promise<AdminSetting> => {
    return request<AdminSetting>('/admin/audit-logs/settings', {
      method: 'PUT',
      body: JSON.stringify({ settingKey, settingValue, description })
    });
  },

  // Domain Management Fallback / Aggregated Direct Proxies
  getBookings: async (page: number = 0, size: number = 10, status?: string): Promise<PageResponse<RideBooking>> => {
    let query = `?page=${page}&size=${size}`;
    if (status) query += `&status=${encodeURIComponent(status)}`;
    return request<PageResponse<RideBooking>>(`/admin/bookings${query}`);
  },

  getRiders: async (page: number = 0, size: number = 10): Promise<PageResponse<RiderProfile>> => {
    return request<PageResponse<RiderProfile>>(`/users?role=ROLE_RIDER&page=${page}&size=${size}`);
  },

  getDrivers: async (page: number = 0, size: number = 10, status?: string): Promise<PageResponse<DriverProfile>> => {
    let query = `?page=${page}&size=${size}`;
    if (status) query += `&verificationStatus=${encodeURIComponent(status)}`;
    return request<PageResponse<DriverProfile>>(`/admin/drivers${query}`);
  },

  getVehicles: async (page: number = 0, size: number = 10): Promise<PageResponse<VehicleProfile>> => {
    return request<PageResponse<VehicleProfile>>(`/admin/vehicles?page=${page}&size=${size}`);
  },

  getPayments: async (page: number = 0, size: number = 10): Promise<PageResponse<PaymentRecord>> => {
    return request<PageResponse<PaymentRecord>>(`/admin/payments?page=${page}&size=${size}`);
  },

  getFeedbackReviews: async (page: number = 0, size: number = 10): Promise<PageResponse<FeedbackReview>> => {
    return request<PageResponse<FeedbackReview>>(`/admin/feedback?page=${page}&size=${size}`);
  },

  moderateFeedback: async (reviewUuid: string, moderationStatus: string, reason: string): Promise<void> => {
    return request<void>(`/admin/feedback/reviews/${reviewUuid}/moderate`, {
      method: 'PUT',
      body: JSON.stringify({ moderationStatus, reason })
    });
  }
};
