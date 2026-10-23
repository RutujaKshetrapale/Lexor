import React, { useState } from 'react';
import { RiderHomeView } from './RiderHomeView';
import { RiderActiveTripView } from './RiderActiveTripView';
import { RiderHistoryView } from './RiderHistoryView';
import { RiderProfileView } from './RiderProfileView';
import { TripBookingDetail } from '../../types';

export const RiderAppShell: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'book' | 'trip' | 'history' | 'profile'>('book');
  const [currentBooking, setCurrentBooking] = useState<TripBookingDetail | null>(null);

  const handleBookingConfirmed = (booking: TripBookingDetail) => {
    setCurrentBooking(booking);
    setActiveTab('trip');
  };

  const handleTripCancelled = () => {
    setCurrentBooking(null);
    setActiveTab('book');
  };

  const handleTripFinished = () => {
    setActiveTab('history');
  };

  return (
    <div style={{ minHeight: 'calc(100vh - 60px)', background: 'var(--bg-main, #0b1120)' }}>
      {/* Sub-navigation bar for Rider Experience */}
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
              onClick={() => setActiveTab('book')}
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
                background: activeTab === 'book' ? '#10b981' : '#1e293b',
                color: activeTab === 'book' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>🧭</span> Book Ride
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
                background: activeTab === 'trip' ? '#10b981' : '#1e293b',
                color: activeTab === 'trip' ? '#0f172a' : '#94a3b8',
                position: 'relative',
                transition: 'all 0.2s'
              }}
            >
              <span>🚗</span> Active Trip
              {currentBooking && (
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#22c55e',
                    boxShadow: '0 0 8px #22c55e'
                  }}
                />
              )}
            </button>

            <button
              onClick={() => setActiveTab('history')}
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
                background: activeTab === 'history' ? '#10b981' : '#1e293b',
                color: activeTab === 'history' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>📜</span> Activity & Receipts
            </button>

            <button
              onClick={() => setActiveTab('profile')}
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
                background: activeTab === 'profile' ? '#10b981' : '#1e293b',
                color: activeTab === 'profile' ? '#0f172a' : '#94a3b8',
                transition: 'all 0.2s'
              }}
            >
              <span>👤</span> Saved Places & Safety
            </button>
          </div>

          <div style={{ fontSize: '0.8rem', color: '#64748b', display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981' }} />
            Bengaluru Metro Region
          </div>
        </div>
      </div>

      {/* Main Experience View */}
      <main>
        {activeTab === 'book' && (
          <RiderHomeView onBookingConfirmed={handleBookingConfirmed} />
        )}

        {activeTab === 'trip' && (
          <RiderActiveTripView
            initialBooking={currentBooking}
            onTripCancelled={handleTripCancelled}
            onTripFinished={handleTripFinished}
          />
        )}

        {activeTab === 'history' && <RiderHistoryView />}

        {activeTab === 'profile' && <RiderProfileView />}
      </main>
    </div>
  );
};

export default RiderAppShell;
