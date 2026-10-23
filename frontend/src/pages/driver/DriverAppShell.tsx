import React, { useState } from 'react';
import { DriverDashboardView } from './DriverDashboardView';
import { DriverActiveTripView } from './DriverActiveTripView';
import { DriverOnboardingView } from './DriverOnboardingView';
import { DriverEarningsView } from './DriverEarningsView';
import { TripBookingDetail } from '../../types';

export const DriverAppShell: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'radar' | 'trip' | 'onboarding' | 'earnings'>('radar');
  const [currentBooking, setCurrentBooking] = useState<TripBookingDetail | null>(null);

  const handleNavigateToTrip = (booking: TripBookingDetail) => {
    setCurrentBooking(booking);
    setActiveTab('trip');
  };

  const handleTripFinished = () => {
    setCurrentBooking(null);
    setActiveTab('earnings');
  };

  return (
    <div style={{ minHeight: 'calc(100vh - 60px)', background: 'var(--bg-main, #0b1120)' }}>
      {/* Sub-navigation bar for Driver Experience */}
      <div
        style={{
          background: 'rgba(15, 23, 42, 0.85)',
          backdropFilter: 'blur(8px)',
          borderBottom: '1px solid #1e293b',
          position: 'sticky',
          top: 57,
          zIndex: 30,
          padding: '0.5rem 1rem'
        }}
      >
        <div
          style={{
            maxWidth: 1200,
            margin: '0 auto',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.5rem'
          }}
        >
          <div style={{ display: 'flex', gap: '0.5rem', overflowX: 'auto', paddingBottom: 2 }}>
            <button
              onClick={() => setActiveTab('radar')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '0.5rem 1rem',
                borderRadius: 20,
                border: 'none',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: activeTab === 'radar' ? '#f59e0b' : '#1e293b',
                color: activeTab === 'radar' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>📡</span> Radar & Dispatch
            </button>

            <button
              onClick={() => setActiveTab('trip')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '0.5rem 1rem',
                borderRadius: 20,
                border: 'none',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: activeTab === 'trip' ? '#f59e0b' : '#1e293b',
                color: activeTab === 'trip' ? '#0f172a' : '#94a3b8',
                position: 'relative',
                transition: 'all 0.2s'
              }}
            >
              <span>🚖</span> Active Trip
              {currentBooking && (
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#f59e0b',
                    boxShadow: '0 0 8px #f59e0b'
                  }}
                />
              )}
            </button>

            <button
              onClick={() => setActiveTab('earnings')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '0.5rem 1rem',
                borderRadius: 20,
                border: 'none',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: activeTab === 'earnings' ? '#f59e0b' : '#1e293b',
                color: activeTab === 'earnings' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>💰</span> Earnings & Statements
            </button>

            <button
              onClick={() => setActiveTab('onboarding')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '0.5rem 1rem',
                borderRadius: 20,
                border: 'none',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
                background: activeTab === 'onboarding' ? '#f59e0b' : '#1e293b',
                color: activeTab === 'onboarding' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>📋</span> KYC & Fleet Compliance
            </button>
          </div>

          <div style={{ fontSize: '0.8rem', color: '#64748b', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#f59e0b' }} />
            Fleet Partner Hub
          </div>
        </div>
      </div>

      {/* Main Driver View */}
      <main>
        {activeTab === 'radar' && (
          <DriverDashboardView
            onNavigateToTrip={handleNavigateToTrip}
            onNavigateToOnboarding={() => setActiveTab('onboarding')}
            onNavigateToEarnings={() => setActiveTab('earnings')}
          />
        )}

        {activeTab === 'trip' && (
          <DriverActiveTripView
            initialBooking={
              currentBooking || {
                bookingUuid: 'bk-demo-active',
                status: 'DRIVER_ARRIVING',
                rider: {
                  uuid: 'rider-01',
                  fullName: 'Priya Sharma',
                  phone: '+91 98765 43210',
                  rating: 4.9
                },
                rideType: { code: 'GO', name: 'LEXOR Go' },
                pickupAddress: 'Indiranagar 100ft Road, Bengaluru',
                pickupLatitude: 12.9784,
                pickupLongitude: 77.6408,
                dropAddress: 'Prestige Tech Park, Marathahalli, Bengaluru',
                dropLatitude: 12.9372,
                dropLongitude: 77.6949,
                estimatedDistanceKm: 7.4,
                estimatedDurationMinutes: 24,
                totalFare: 340,
                currency: 'INR',
                otp: '4829',
                createdAt: new Date().toISOString()
              }
            }
            onTripFinished={handleTripFinished}
          />
        )}

        {activeTab === 'earnings' && (
          <DriverEarningsView onBackToDashboard={() => setActiveTab('radar')} />
        )}

        {activeTab === 'onboarding' && (
          <DriverOnboardingView onBackToDashboard={() => setActiveTab('radar')} />
        )}
      </main>
    </div>
  );
};

export default DriverAppShell;
