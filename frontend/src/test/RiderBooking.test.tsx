import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { AuthProvider } from '../context/AuthContext';
import { RiderAppShell } from '../pages/rider/RiderAppShell';

describe('Rider Experience & Booking Flow', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('lexor_active_experience', 'rider');

    (globalThis as any).fetch = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/ride-types')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: [
                {
                  uuid: '1',
                  name: 'LEXOR Go',
                  code: 'GO',
                  description: 'Affordable hatchback everyday rides',
                  baseFare: 50,
                  perKmRate: 15,
                  perMinuteRate: 2,
                  minimumFare: 80,
                  cancellationFee: 30,
                  capacity: 4,
                  isActive: true
                },
                {
                  uuid: '2',
                  name: 'LEXOR Premier',
                  code: 'PREMIER',
                  description: 'Comfortable sedans with top-rated drivers',
                  baseFare: 75,
                  perKmRate: 18,
                  perMinuteRate: 2.5,
                  minimumFare: 110,
                  cancellationFee: 40,
                  capacity: 4,
                  isActive: true
                },
                {
                  uuid: '3',
                  name: 'LEXOR XL',
                  code: 'XL',
                  description: 'Spacious 6-seater SUVs for groups & luggage',
                  baseFare: 120,
                  perKmRate: 24,
                  perMinuteRate: 3.5,
                  minimumFare: 180,
                  cancellationFee: 50,
                  capacity: 6,
                  isActive: true
                }
              ]
            })
        });
      }

      if (url.includes('/ride-requests') || url.includes('/bookings')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              success: true,
              data: {
                bookingUuid: 'bk-test-01',
                status: 'CONFIRMED',
                otp: '4829',
                pickupAddress: 'Indiranagar 100ft Road, Bengaluru',
                dropAddress: 'Prestige Tech Park, Marathahalli, Bengaluru',
                totalFare: 250,
                currency: 'INR'
              }
            })
        });
      }

      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ success: true, data: {} })
      });
    });
  });

  it('renders rider experience navigation and booking tabs', () => {
    render(
      <AuthProvider>
        <RiderAppShell />
      </AuthProvider>
    );

    expect(screen.getByText('Book Ride')).toBeInTheDocument();
    expect(screen.getByText('Active Trip')).toBeInTheDocument();
    expect(screen.getByText('Activity & Receipts')).toBeInTheDocument();
    expect(screen.getByText('Saved Places & Safety')).toBeInTheDocument();
  });

  it('displays ride category options with upfront fare estimates', async () => {
    render(
      <AuthProvider>
        <RiderAppShell />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/LEXOR Go/i).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('LEXOR Premier')).toBeInTheDocument();
      expect(screen.getByText('LEXOR XL')).toBeInTheDocument();
    });
  });

  it('allows switching payment method between UPI, Card, and Cash', async () => {
    render(
      <AuthProvider>
        <RiderAppShell />
      </AuthProvider>
    );

    const cashBtn = screen.getByRole('button', { name: 'CASH' });
    expect(cashBtn).toBeInTheDocument();
    fireEvent.click(cashBtn);
    expect(cashBtn).toHaveClass('bg-indigo-600');
  });

  it('confirms a ride and transitions into active trip lifecycle', async () => {
    render(
      <AuthProvider>
        <RiderAppShell />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/LEXOR Go/i).length).toBeGreaterThanOrEqual(1);
    });

    // Click Request Ride
    const requestBtn = screen.getByRole('button', { name: /Request/i });
    expect(requestBtn).toBeInTheDocument();
    fireEvent.click(requestBtn);

    // Verifies transition into active trip state
    await waitFor(() => {
      expect(screen.getByText(/Start Ride OTP/i)).toBeInTheDocument();
    });
  });
});
