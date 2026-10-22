import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import App from '../App';

describe('LEXOR Admin Operations Dashboard', () => {
  beforeEach(() => {
    // Mock global fetch for API calls
    (globalThis as any).fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/admin/operations/overview')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                totalRides: 42,
                completedRides: 38,
                cancelledRides: 4,
                activeRides: 2,
                pendingRideRequests: 1,
                onlineDrivers: 15,
                inTripDrivers: 2,
                pendingDriverVerifications: 3,
                registeredRiders: 120,
                registeredDrivers: 25,
                registeredVehicles: 20,
                grossBookingValue: 1250.5,
                successfulPaymentTotals: 1100.0,
                refundTotals: 50.0,
                platformCommissionEarned: 220.0,
                averageDriverRating: 4.85,
                feedbackReportsAwaitingModeration: 0,
                timeRange: 'TODAY',
                rangeStartDate: new Date().toISOString(),
                rangeEndDate: new Date().toISOString(),
                timeZone: 'UTC',
                calculatedAt: new Date().toISOString(),
                metricDefinitions: {
                  totalRides: 'Authoritative bookings recorded in booking-service',
                  completedRides: 'Bookings reaching COMPLETED status'
                }
              }
            })
        });
      }

      if (url.includes('/admin/operations/live-snapshot')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                snapshotTimestamp: new Date().toISOString(),
                refreshIntervalSeconds: 10,
                driverStates: {
                  onlineAvailable: 15,
                  busyInTrip: 2,
                  offline: 8,
                  totalDrivers: 25
                },
                activeRides: [],
                pendingRequests: [],
                recentCancellations: [],
                activeAlerts: []
              }
            })
        });
      }

      if (url.includes('/admin/health-monitor')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                checkedAt: new Date().toISOString(),
                totalServices: 15,
                healthyServices: 14,
                degradedServices: 1,
                downServices: 0,
                overallSystemStatus: 'UP',
                services: [
                  {
                    serviceName: 'API Gateway',
                    port: 8080,
                    endpointUrl: 'http://localhost:8080/actuator/health',
                    status: 'UP',
                    latencyMs: 12,
                    readiness: 'READY',
                    lastChecked: new Date().toISOString()
                  }
                ]
              }
            })
        });
      }

      if (url.includes('/admin/audit-logs/settings')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: [
                {
                  settingKey: 'LIVE_OPS_POLL_INTERVAL_SECONDS',
                  settingValue: '10',
                  description: 'Live operations refresh interval'
                }
              ]
            })
        });
      }

      return Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve({
            success: true,
            data: {
              content: [],
              pageNumber: 0,
              pageSize: 10,
              totalElements: 0,
              totalPages: 0,
              isLast: true
            }
          })
      });
    });
  });

  it('renders sidebar brand and operations navigation', () => {
    render(<App />);
    expect(screen.getByText('LEXOR')).toBeInTheDocument();
    expect(screen.getByText('Operations HQ')).toBeInTheDocument();
    expect(screen.getAllByText('Executive Overview').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Live Operations')).toBeInTheDocument();
    expect(screen.getByText('Service Health')).toBeInTheDocument();
    expect(screen.getByText('Audit Logs')).toBeInTheDocument();
  });

  it('renders executive overview metrics upon load', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByText('Total Bookings')).toBeInTheDocument();
      expect(screen.getByText('Completed Rides')).toBeInTheDocument();
      expect(screen.getByText('Gross Booking Value')).toBeInTheDocument();
    });
  });

  it('switches between navigation tabs', async () => {
    render(<App />);

    // Click Live Operations
    const liveOpsBtn = screen.getByText('Live Operations');
    fireEvent.click(liveOpsBtn);

    await waitFor(() => {
      expect(screen.getByText('Live Platform Dispatch Console')).toBeInTheDocument();
      expect(screen.getByText('Driver Fleet Real-Time Availability')).toBeInTheDocument();
    });

    // Click Service Health
    const healthBtn = screen.getByText('Service Health');
    fireEvent.click(healthBtn);

    await waitFor(() => {
      expect(screen.getByText('Platform Health & Dependency Readiness')).toBeInTheDocument();
    });

    // Click Settings
    const settingsBtn = screen.getByText('Settings');
    fireEvent.click(settingsBtn);

    await waitFor(() => {
      expect(screen.getByText('Operational Platform Settings')).toBeInTheDocument();
    });
  });
});
