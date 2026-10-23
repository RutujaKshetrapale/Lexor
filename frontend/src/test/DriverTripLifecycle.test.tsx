import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { AuthProvider } from '../context/AuthContext';
import { DriverAppShell } from '../pages/driver/DriverAppShell';

describe('Driver Experience & Trip Lifecycle Flow', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('lexor_active_experience', 'driver');
  });

  it('renders driver experience navigation tabs', () => {
    render(
      <AuthProvider>
        <DriverAppShell />
      </AuthProvider>
    );

    expect(screen.getByText('Radar & Dispatch')).toBeInTheDocument();
    expect(screen.getByText('Active Trip')).toBeInTheDocument();
    expect(screen.getByText('Earnings & Statements')).toBeInTheDocument();
    expect(screen.getByText('KYC & Fleet Compliance')).toBeInTheDocument();
  });

  it('toggles duty between online and offline', async () => {
    render(
      <AuthProvider>
        <DriverAppShell />
      </AuthProvider>
    );

    const toggleBtn = screen.getByText('🟢 GO ONLINE');
    expect(toggleBtn).toBeInTheDocument();

    fireEvent.click(toggleBtn);

    await waitFor(() => {
      expect(screen.getByText('🔴 GO OFFLINE')).toBeInTheDocument();
    });
  });

  it('triggers an incoming dispatch offer modal with radial countdown', async () => {
    render(
      <AuthProvider>
        <DriverAppShell />
      </AuthProvider>
    );

    const testOfferBtn = screen.getByText(/Simulate Incoming Offer/i);
    fireEvent.click(testOfferBtn);

    await waitFor(() => {
      expect(screen.getByText(/New Trip Dispatch/i)).toBeInTheDocument();
      expect(screen.getByText('ACCEPT RIDE')).toBeInTheDocument();
      expect(screen.getByText('DECLINE')).toBeInTheDocument();
    });
  });

  it('requires entering 4-digit start OTP to progress from arrived to in-progress', async () => {
    render(
      <AuthProvider>
        <DriverAppShell />
      </AuthProvider>
    );

    // Switch to active trip tab
    const tripTab = screen.getByText('Active Trip');
    fireEvent.click(tripTab);

    // Click Arrived at Pickup
    const arrivedBtn = screen.getByText(/I HAVE ARRIVED AT PICKUP/i);
    fireEvent.click(arrivedBtn);

    // Prompt for start OTP
    await waitFor(() => {
      expect(screen.getByText(/ENTER START OTP/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/ENTER START OTP/i));

    await waitFor(() => {
      expect(screen.getByText(/Enter Rider's Start OTP/i)).toBeInTheDocument();
    });
  });
});
