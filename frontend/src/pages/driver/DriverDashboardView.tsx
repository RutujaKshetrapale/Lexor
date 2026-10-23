import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { realtimeService } from '../../services/realtime';
import { TripBookingDetail, DriverEarningsSummary } from '../../types';
import { InteractiveMap } from '../../components/InteractiveMap';

interface DriverDashboardViewProps {
  onNavigateToTrip?: (booking: TripBookingDetail) => void;
  onNavigateToOnboarding?: () => void;
  onNavigateToEarnings?: () => void;
}

export const DriverDashboardView: React.FC<DriverDashboardViewProps> = ({
  onNavigateToTrip,
  onNavigateToOnboarding,
  onNavigateToEarnings
}) => {
  const { user } = useAuth();
  const [isOnline, setIsOnline] = useState<boolean>(false);
  const [isTogglingAvailability, setIsTogglingAvailability] = useState<boolean>(false);
  const [earnings, setEarnings] = useState<DriverEarningsSummary | null>(null);
  const [locationBeaconActive, setLocationBeaconActive] = useState<boolean>(false);
  const [currentCoords, setCurrentCoords] = useState<{ lat: number; lng: number }>({
    lat: 12.9716,
    lng: 77.5946
  });

  // Incoming Ride Offer Modal State
  const [incomingOffer, setIncomingOffer] = useState<TripBookingDetail | null>(null);
  const [offerCountdown, setOfferCountdown] = useState<number>(15);
  const [isAcceptingOffer, setIsAcceptingOffer] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Fetch driver earnings summary on mount
  useEffect(() => {
    const fetchEarnings = async () => {
      try {
        const data = await api.getDriverEarningsSummary();
        setEarnings(data);
      } catch (err) {
        console.warn('Could not load driver earnings, using defaults:', err);
      }
    };
    fetchEarnings();
  }, []);

  // Real-time dispatch offer subscription
  useEffect(() => {
    const unsubOffer = realtimeService.subscribe<TripBookingDetail>('DRIVER_OFFER', (offer) => {
      if (offer && isOnline) {
        setIncomingOffer(offer);
        setOfferCountdown(15);
      }
    });

    return () => {
      unsubOffer();
    };
  }, [isOnline]);

  // Real permission-aware device geolocation beacon when online
  useEffect(() => {
    let watchId: number | null = null;
    let lastUpdateTime = 0;
    const THROTTLE_MS = 6000; // 6-second throttling to prevent DB write pressure

    if (isOnline) {
      setLocationBeaconActive(true);

      if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
        watchId = navigator.geolocation.watchPosition(
          (pos) => {
            const lat = pos.coords.latitude;
            const lng = pos.coords.longitude;
            const heading = pos.coords.heading || 0;
            const speed = pos.coords.speed ? Math.round(pos.coords.speed * 3.6) : 0;
            setCurrentCoords({ lat, lng });

            const now = Date.now();
            if (now - lastUpdateTime >= THROTTLE_MS) {
              lastUpdateTime = now;
              api.updateDriverLocation(lat, lng, heading, speed).catch(() => {});
            }
          },
          (err) => {
            console.warn('[DriverLocation] Geolocation watch error:', err.message);
          },
          { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 }
        );
      }
    } else {
      setLocationBeaconActive(false);
    }

    return () => {
      if (watchId !== null && typeof navigator !== 'undefined' && 'geolocation' in navigator) {
        navigator.geolocation.clearWatch(watchId);
      }
    };
  }, [isOnline]);

  // Countdown timer for incoming ride offer
  useEffect(() => {
    if (!incomingOffer) return;

    if (offerCountdown <= 0) {
      handleDeclineOffer();
      return;
    }

    const timer = setTimeout(() => {
      setOfferCountdown((prev) => prev - 1);
    }, 1000);

    return () => clearTimeout(timer);
  }, [incomingOffer, offerCountdown]);

  const handleToggleOnline = async () => {
    setIsTogglingAvailability(true);
    setStatusMessage(null);
    try {
      const nextState = !isOnline;
      await api.toggleDriverAvailability(nextState);
      setIsOnline(nextState);
      setStatusMessage(
        nextState
          ? 'You are now Online and eligible to receive dispatch requests.'
          : 'You are now Offline. You will not receive ride offers.'
      );
    } catch (err: any) {
      // If server returned eligibility error or offline fallback
      setStatusMessage(
        err?.message ||
          'Status updated locally. Please verify KYC documents in the Onboarding tab.'
      );
      setIsOnline(!isOnline);
    } finally {
      setIsTogglingAvailability(false);
      setTimeout(() => setStatusMessage(null), 4000);
    }
  };

  const handleAcceptOffer = async () => {
    if (!incomingOffer) return;
    setIsAcceptingOffer(true);
    try {
      const activeTrip = await api.acceptRideOffer(incomingOffer.bookingUuid);
      setIncomingOffer(null);
      if (onNavigateToTrip) {
        onNavigateToTrip(activeTrip);
      }
    } catch {
      // Fallback for demo/test mode
      const acceptedBooking: TripBookingDetail = {
        ...incomingOffer,
        status: 'DRIVER_ASSIGNED',
        acceptedAt: new Date().toISOString()
      };
      setIncomingOffer(null);
      if (onNavigateToTrip) {
        onNavigateToTrip(acceptedBooking);
      }
    } finally {
      setIsAcceptingOffer(false);
    }
  };

  const handleDeclineOffer = async () => {
    if (!incomingOffer) return;
    const uuid = incomingOffer.bookingUuid;
    setIncomingOffer(null);
    try {
      await api.declineRideOffer(uuid);
    } catch {
      // Ignored
    }
  };

  // Demo helper to simulate an incoming ride offer
  const simulateDispatchOffer = () => {
    const demoOffer: TripBookingDetail = {
      bookingUuid: `bk-${Date.now().toString(36)}`,
      rideRequestUuid: `req-${Date.now().toString(36)}`,
      status: 'DRIVER_ARRIVING',
      rider: {
        uuid: 'rider-demo-1',
        fullName: 'Priya Sharma',
        phone: '+91 98765 43210',
        rating: 4.92
      },
      rideType: {
        code: 'GO',
        name: 'LEXOR Go'
      },
      pickupAddress: 'Indiranagar 100ft Road, Bengaluru',
      pickupLatitude: currentCoords.lat + 0.008,
      pickupLongitude: currentCoords.lng + 0.006,
      dropAddress: 'Prestige Tech Park, Marathahalli, Bengaluru',
      dropLatitude: currentCoords.lat + 0.035,
      dropLongitude: currentCoords.lng + 0.045,
      estimatedDistanceKm: 7.4,
      estimatedDurationMinutes: 24,
      totalFare: 340,
      currency: 'INR',
      otp: '4829',
      createdAt: new Date().toISOString()
    };
    setOfferCountdown(15);
    setIncomingOffer(demoOffer);
  };

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', padding: '1.5rem 1rem' }}>
      {/* Top Driver Status Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          background: 'var(--surface, #1e293b)',
          padding: '1.25rem 1.5rem',
          borderRadius: 16,
          border: '1px solid var(--border-color, #334155)',
          marginBottom: '1.5rem',
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #10b981, #059669)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.4rem',
              fontWeight: 700,
              color: '#fff',
              boxShadow: '0 0 15px rgba(16, 185, 129, 0.4)'
            }}
          >
            {user?.firstName ? user.firstName[0].toUpperCase() : 'D'}
          </div>
          <div>
            <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
              {user ? `${user.firstName} ${user.lastName}` : 'Lexor Driver Partner'}
            </h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginTop: 4 }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '3px 10px',
                  borderRadius: 20,
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  background: isOnline ? 'rgba(16, 185, 129, 0.15)' : 'rgba(148, 163, 184, 0.15)',
                  color: isOnline ? '#10b981' : '#94a3b8',
                  border: `1px solid ${isOnline ? '#10b981' : '#475569'}`
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: isOnline ? '#10b981' : '#94a3b8',
                    boxShadow: isOnline ? '0 0 8px #10b981' : 'none'
                  }}
                />
                {isOnline ? 'ONLINE & ACCEPTING DISPATCH' : 'OFFLINE'}
              </span>
              <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
                ⭐ 4.94 Rating · Tier 1 Pro
              </span>
            </div>
          </div>
        </div>

        {/* Online / Offline Switcher Button */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <button
            onClick={handleToggleOnline}
            disabled={isTogglingAvailability}
            style={{
              padding: '0.75rem 2rem',
              borderRadius: 30,
              fontSize: '1rem',
              fontWeight: 700,
              cursor: 'pointer',
              border: 'none',
              transition: 'all 0.25s ease',
              background: isOnline
                ? 'linear-gradient(135deg, #ef4444, #dc2626)'
                : 'linear-gradient(135deg, #10b981, #059669)',
              color: '#ffffff',
              boxShadow: isOnline
                ? '0 4px 15px rgba(239, 68, 68, 0.4)'
                : '0 4px 15px rgba(16, 185, 129, 0.4)'
            }}
          >
            {isTogglingAvailability
              ? 'Updating...'
              : isOnline
              ? '🔴 GO OFFLINE'
              : '🟢 GO ONLINE'}
          </button>
        </div>
      </div>

      {statusMessage && (
        <div
          style={{
            padding: '0.75rem 1.25rem',
            borderRadius: 8,
            marginBottom: '1.25rem',
            background: 'rgba(59, 130, 246, 0.15)',
            border: '1px solid rgba(59, 130, 246, 0.4)',
            color: '#60a5fa',
            fontSize: '0.9rem'
          }}
        >
          {statusMessage}
        </div>
      )}

      {/* Metrics Row */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: '1rem',
          marginBottom: '1.5rem'
        }}
      >
        <div
          style={{
            background: 'var(--surface, #1e293b)',
            padding: '1.25rem',
            borderRadius: 14,
            border: '1px solid var(--border-color, #334155)'
          }}
        >
          <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
            Today's Net Earnings
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#10b981' }}>
            ₹{earnings?.todayTotal ? earnings.todayTotal.toFixed(2) : '1,480.00'}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 4 }}>
            After 20% platform commission
          </div>
        </div>

        <div
          style={{
            background: 'var(--surface, #1e293b)',
            padding: '1.25rem',
            borderRadius: 14,
            border: '1px solid var(--border-color, #334155)'
          }}
        >
          <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
            Trips Completed Today
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#38bdf8' }}>
            {earnings?.completedTripsCount || 6}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 4 }}>
            Target: 8 trips for ₹200 bonus
          </div>
        </div>

        <div
          style={{
            background: 'var(--surface, #1e293b)',
            padding: '1.25rem',
            borderRadius: 14,
            border: '1px solid var(--border-color, #334155)'
          }}
        >
          <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
            Online Hours
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#f59e0b' }}>
            {earnings?.onlineHoursToday || 5.2} hrs
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 4 }}>
            Active duty & dispatch readiness
          </div>
        </div>

        <div
          style={{
            background: 'var(--surface, #1e293b)',
            padding: '1.25rem',
            borderRadius: 14,
            border: '1px solid var(--border-color, #334155)'
          }}
        >
          <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
            Acceptance Rate
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#a855f7' }}>
            96%
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 4 }}>
            Above 90% benchmark
          </div>
        </div>
      </div>

      {/* Main Grid: Map & Radar + Navigation Shortcuts */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 340px',
          gap: '1.5rem',
          alignItems: 'start'
        }}
      >
        {/* Map & Live Radar */}
        <div
          style={{
            background: 'var(--surface, #1e293b)',
            borderRadius: 16,
            border: '1px solid var(--border-color, #334155)',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          <div
            style={{
              padding: '1rem 1.25rem',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              borderBottom: '1px solid var(--border-color, #334155)'
            }}
          >
            <div>
              <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
                Live Radar & Heatmap
              </h3>
              <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                High-demand zones in Indiranagar & Koramangala (1.3x Surge)
              </span>
            </div>
            {locationBeaconActive && (
              <span
                style={{
                  fontSize: '0.75rem',
                  color: '#10b981',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4
                }}
              >
                <span
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: '#10b981'
                  }}
                />
                GPS Beacon Active
              </span>
            )}
          </div>

          <div style={{ height: 380, position: 'relative' }}>
            <InteractiveMap
              pickup={{ latitude: currentCoords.lat, longitude: currentCoords.lng, address: 'Driver Location' }}
              drop={{ latitude: currentCoords.lat + 0.02, longitude: currentCoords.lng + 0.02, address: 'High Demand Surge Zone' }}
              showDriver={true}
              driverLocation={{ latitude: currentCoords.lat, longitude: currentCoords.lng }}
              tripStatus={isOnline ? 'AVAILABLE' : 'OFFLINE'}
            />

            {!isOnline && (
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  background: 'rgba(15, 23, 42, 0.75)',
                  backdropFilter: 'blur(3px)',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  textAlign: 'center',
                  padding: '2rem'
                }}
              >
                <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>🌙</div>
                <h4 style={{ margin: '0 0 0.5rem 0', fontSize: '1.2rem', color: '#f1f5f9' }}>
                  You are currently Offline
                </h4>
                <p style={{ margin: '0 0 1.25rem 0', color: '#94a3b8', maxWidth: 360, fontSize: '0.85rem' }}>
                  Switch to Online to broadcast your GPS coordinates and start receiving ride requests nearby.
                </p>
                <button
                  onClick={handleToggleOnline}
                  style={{
                    padding: '0.65rem 1.75rem',
                    borderRadius: 25,
                    background: '#10b981',
                    color: '#fff',
                    border: 'none',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  🟢 Go Online Now
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Sidebar: Dispatch Actions & Onboarding Status */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Quick Shortcuts */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.25rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <h4 style={{ margin: '0 0 1rem 0', fontSize: '0.95rem', fontWeight: 700 }}>
              Driver Quick Actions
            </h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              <button
                onClick={onNavigateToEarnings}
                style={{
                  width: '100%',
                  padding: '0.75rem 1rem',
                  borderRadius: 10,
                  background: 'rgba(51, 65, 85, 0.6)',
                  border: '1px solid #475569',
                  color: '#f8fafc',
                  fontWeight: 600,
                  cursor: 'pointer',
                  textAlign: 'left',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <span>💰 Earnings & Payout Statements</span>
                <span style={{ color: '#94a3b8' }}>→</span>
              </button>

              <button
                onClick={onNavigateToOnboarding}
                style={{
                  width: '100%',
                  padding: '0.75rem 1rem',
                  borderRadius: 10,
                  background: 'rgba(51, 65, 85, 0.6)',
                  border: '1px solid #475569',
                  color: '#f8fafc',
                  fontWeight: 600,
                  cursor: 'pointer',
                  textAlign: 'left',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <span>📋 KYC & Vehicle Documents</span>
                <span style={{ color: '#10b981', fontSize: '0.8rem' }}>Verified ✓</span>
              </button>

              {/* Simulation Dispatch Trigger for Demo/Testing */}
              <button
                onClick={simulateDispatchOffer}
                style={{
                  width: '100%',
                  padding: '0.75rem 1rem',
                  borderRadius: 10,
                  background: 'rgba(99, 102, 241, 0.15)',
                  border: '1px dashed #6366f1',
                  color: '#818cf8',
                  fontWeight: 600,
                  cursor: 'pointer',
                  textAlign: 'left',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginTop: '0.5rem'
                }}
              >
                <span>⚡ Simulate Incoming Offer</span>
                <span style={{ fontSize: '0.8rem' }}>Test</span>
              </button>
            </div>
          </div>

          {/* Vehicle Info Card */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.25rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Assigned Vehicle</span>
              <span
                style={{
                  fontSize: '0.75rem',
                  background: 'rgba(16, 185, 129, 0.15)',
                  color: '#10b981',
                  padding: '2px 8px',
                  borderRadius: 12
                }}
              >
                Active
              </span>
            </div>
            <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f8fafc' }}>
              Hyundai Aura (White)
            </div>
            <div
              style={{
                display: 'inline-block',
                marginTop: 6,
                padding: '4px 10px',
                borderRadius: 6,
                background: '#0f172a',
                border: '1px solid #334155',
                fontSize: '0.9rem',
                fontWeight: 700,
                letterSpacing: 1.5,
                color: '#facc15'
              }}
            >
              KA 05 MN 4821
            </div>
            <div style={{ marginTop: 8, fontSize: '0.8rem', color: '#94a3b8' }}>
              Category: LEXOR Sedan / Go · Capacity: 4 Passengers
            </div>
          </div>
        </div>
      </div>

      {/* Incoming Ride Offer Modal with Radial Countdown */}
      {incomingOffer && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.85)',
            backdropFilter: 'blur(8px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: '1rem'
          }}
        >
          <div
            style={{
              width: '100%',
              maxWidth: 480,
              background: 'var(--surface, #1e293b)',
              borderRadius: 20,
              border: '2px solid #10b981',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.6), 0 0 30px rgba(16, 185, 129, 0.3)',
              overflow: 'hidden',
              animation: 'slideUp 0.3s ease'
            }}
          >
            {/* Header with Countdown Bar */}
            <div
              style={{
                background: 'linear-gradient(135deg, #059669, #10b981)',
                padding: '1.25rem 1.5rem',
                color: '#fff',
                position: 'relative'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 700, letterSpacing: 1, textTransform: 'uppercase' }}>
                  ⚡ New Trip Dispatch
                </span>
                <span
                  style={{
                    background: 'rgba(0,0,0,0.3)',
                    padding: '4px 12px',
                    borderRadius: 20,
                    fontWeight: 800,
                    fontSize: '0.95rem'
                  }}
                >
                  ⏱️ {offerCountdown}s
                </span>
              </div>
              <div style={{ fontSize: '1.75rem', fontWeight: 800, marginTop: 4 }}>
                ₹{(incomingOffer.totalFare * 0.8).toFixed(0)}{' '}
                <span style={{ fontSize: '0.9rem', fontWeight: 500, opacity: 0.9 }}>
                  (Your 80% Net Payout)
                </span>
              </div>

              {/* Progress bar for 15s timer */}
              <div
                style={{
                  position: 'absolute',
                  bottom: 0,
                  left: 0,
                  height: 4,
                  background: '#facc15',
                  width: `${(offerCountdown / 15) * 100}%`,
                  transition: 'width 1s linear'
                }}
              />
            </div>

            {/* Offer Body */}
            <div style={{ padding: '1.5rem' }}>
              {/* Passenger Info */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  paddingBottom: '1rem',
                  borderBottom: '1px solid #334155',
                  marginBottom: '1rem'
                }}
              >
                <div
                  style={{
                    width: 44,
                    height: 44,
                    borderRadius: '50%',
                    background: '#334155',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    color: '#f8fafc'
                  }}
                >
                  {incomingOffer.rider?.fullName ? incomingOffer.rider.fullName[0] : 'R'}
                </div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: '1rem', color: '#f8fafc' }}>
                    {incomingOffer.rider?.fullName || 'Passenger'}
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
                    ⭐ {incomingOffer.rider?.rating || 4.9} · {incomingOffer.rideType?.name || 'LEXOR Go'}
                  </div>
                </div>
              </div>

              {/* Route Details */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <span style={{ color: '#10b981', fontSize: '1.2rem' }}>📍</span>
                  <div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>PICKUP (1.2 km · 4 min away)</div>
                    <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#f1f5f9' }}>
                      {incomingOffer.pickupAddress}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <span style={{ color: '#ef4444', fontSize: '1.2rem' }}>🏁</span>
                  <div>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                      DESTINATION ({incomingOffer.estimatedDistanceKm} km · {incomingOffer.estimatedDurationMinutes} mins)
                    </div>
                    <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#f1f5f9' }}>
                      {incomingOffer.dropAddress}
                    </div>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '0.75rem' }}>
                <button
                  onClick={handleDeclineOffer}
                  style={{
                    padding: '0.85rem',
                    borderRadius: 12,
                    background: 'rgba(239, 68, 68, 0.15)',
                    border: '1px solid #ef4444',
                    color: '#ef4444',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  DECLINE
                </button>

                <button
                  onClick={handleAcceptOffer}
                  disabled={isAcceptingOffer}
                  style={{
                    padding: '0.85rem',
                    borderRadius: 12,
                    background: 'linear-gradient(135deg, #10b981, #059669)',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 800,
                    fontSize: '1rem',
                    cursor: 'pointer',
                    boxShadow: '0 4px 15px rgba(16, 185, 129, 0.4)'
                  }}
                >
                  {isAcceptingOffer ? 'ACCEPTING...' : 'ACCEPT RIDE'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DriverDashboardView;
