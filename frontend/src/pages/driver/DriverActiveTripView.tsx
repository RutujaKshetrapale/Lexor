import React, { useState, useEffect } from 'react';
import { TripBookingDetail, TripLifecycleStatus } from '../../types';
import { api } from '../../services/api';
import { InteractiveMap } from '../../components/InteractiveMap';
import { SafetyModal } from '../../components/SafetyModal';

interface DriverActiveTripViewProps {
  initialBooking: TripBookingDetail;
  onTripFinished: () => void;
}

export const DriverActiveTripView: React.FC<DriverActiveTripViewProps> = ({
  initialBooking,
  onTripFinished
}) => {
  const [booking, setBooking] = useState<TripBookingDetail>(initialBooking);
  const [tripStatus, setTripStatus] = useState<TripLifecycleStatus>(
    initialBooking.status || 'DRIVER_ARRIVING'
  );
  const [isActionLoading, setIsActionLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // OTP Verification Modal State
  const [showOtpModal, setShowOtpModal] = useState<boolean>(false);
  const [otpInput, setOtpInput] = useState<string>('');
  const [otpError, setOtpError] = useState<string | null>(null);

  // Safety Center Modal
  const [showSafetyModal, setShowSafetyModal] = useState<boolean>(false);

  // Cash collection confirmation
  const [cashCollected, setCashCollected] = useState<boolean>(false);

  // Passenger rating
  const [riderRating, setRiderRating] = useState<number>(5);
  const [ratingSubmitted, setRatingSubmitted] = useState<boolean>(false);

  // Wait timer when driver has arrived
  const [waitTimerSeconds, setWaitTimerSeconds] = useState<number>(0);

  // Live Driver Device Geolocation Coordinates
  const [driverCoords, setDriverCoords] = useState<{ latitude: number; longitude: number; recordedAt?: string }>({
    latitude: booking.pickupLatitude,
    longitude: booking.pickupLongitude,
    recordedAt: new Date().toISOString()
  });

  useEffect(() => {
    let watchId: number | null = null;
    let lastUpdateTime = 0;
    const THROTTLE_MS = 6000;

    if (tripStatus === 'DRIVER_ARRIVING' || tripStatus === 'DRIVER_ARRIVED' || tripStatus === 'IN_PROGRESS') {
      if (typeof navigator !== 'undefined' && 'geolocation' in navigator) {
        watchId = navigator.geolocation.watchPosition(
          (pos) => {
            const lat = pos.coords.latitude;
            const lng = pos.coords.longitude;
            const heading = pos.coords.heading || 0;
            const speed = pos.coords.speed ? Math.round(pos.coords.speed * 3.6) : 0;
            const recAt = new Date().toISOString();
            setDriverCoords({ latitude: lat, longitude: lng, recordedAt: recAt });

            const now = Date.now();
            if (now - lastUpdateTime >= THROTTLE_MS) {
              lastUpdateTime = now;
              api.updateDriverLocation(lat, lng, heading, speed).catch(() => {});
            }
          },
          (err) => {
            console.warn('[DriverActiveTrip] Geolocation error:', err.message);
          },
          { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 }
        );
      }
    }

    return () => {
      if (watchId !== null && typeof navigator !== 'undefined' && 'geolocation' in navigator) {
        navigator.geolocation.clearWatch(watchId);
      }
    };
  }, [tripStatus]);

  useEffect(() => {
    let interval: any;
    if (tripStatus === 'DRIVER_ARRIVED') {
      interval = setInterval(() => {
        setWaitTimerSeconds((prev) => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [tripStatus]);

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // 1. Driver Arrives at Pickup
  const handleDriverArrived = async () => {
    setIsActionLoading(true);
    setErrorMessage(null);
    try {
      const updated = await api.driverArrivedAtPickup(booking.bookingUuid);
      setBooking(updated);
      setTripStatus('DRIVER_ARRIVED');
    } catch {
      // Offline fallback
      setTripStatus('DRIVER_ARRIVED');
    } finally {
      setIsActionLoading(false);
    }
  };

  // 2. Start Trip with OTP Verification
  const handleVerifyOtpAndStart = async () => {
    if (otpInput.length !== 4) {
      setOtpError('Please enter a 4-digit numeric OTP.');
      return;
    }
    setIsActionLoading(true);
    setOtpError(null);
    try {
      const updated = await api.startTripWithOtp(booking.bookingUuid, otpInput);
      setBooking(updated);
      setTripStatus('IN_PROGRESS');
      setShowOtpModal(false);
    } catch (err: any) {
      setOtpError(err?.message || 'Invalid OTP code. Please request the 4-digit code from the rider.');
    } finally {
      setIsActionLoading(false);
    }
  };

  // 3. Complete Trip
  const handleCompleteTrip = async () => {
    setIsActionLoading(true);
    setErrorMessage(null);
    try {
      const updated = await api.completeTrip(
        booking.bookingUuid,
        booking.estimatedDistanceKm || 7.4,
        booking.estimatedDurationMinutes || 22
      );
      setBooking(updated);
      setTripStatus('COMPLETED');
    } catch {
      // Fallback
      setTripStatus('COMPLETED');
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleConfirmCash = async () => {
    try {
      await api.confirmCashCollection(`pmt-${booking.bookingUuid}`);
      setCashCollected(true);
    } catch {
      setCashCollected(true);
    }
  };

  const handleRatingSubmit = () => {
    setRatingSubmitted(true);
  };

  // Navigation URLs
  const openNavigation = (lat: number, lng: number) => {
    window.open(`https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`, '_blank');
  };

  const netEarnings = Math.round((booking.totalFare || 340) * 0.8 * 100) / 100;
  const commission = Math.round((booking.totalFare || 340) * 0.2 * 100) / 100;

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '1.5rem 1rem' }}>
      {/* Top Header Card */}
      <div
        style={{
          background: 'var(--surface, #1e293b)',
          padding: '1.25rem 1.5rem',
          borderRadius: 16,
          border: '1px solid var(--border-color, #334155)',
          marginBottom: '1.25rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem'
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span
              style={{
                background:
                  tripStatus === 'COMPLETED'
                    ? 'rgba(16, 185, 129, 0.2)'
                    : tripStatus === 'IN_PROGRESS'
                    ? 'rgba(59, 130, 246, 0.2)'
                    : 'rgba(245, 158, 11, 0.2)',
                color:
                  tripStatus === 'COMPLETED'
                    ? '#10b981'
                    : tripStatus === 'IN_PROGRESS'
                    ? '#60a5fa'
                    : '#fbbf24',
                padding: '4px 12px',
                borderRadius: 20,
                fontSize: '0.85rem',
                fontWeight: 700,
                letterSpacing: 0.5
              }}
            >
              {tripStatus === 'DRIVER_ARRIVING' && '🚗 EN ROUTE TO PICKUP'}
              {tripStatus === 'DRIVER_ARRIVED' && '📍 ARRIVED AT PICKUP'}
              {tripStatus === 'IN_PROGRESS' && '🚀 TRIP IN PROGRESS'}
              {tripStatus === 'COMPLETED' && '🏁 TRIP COMPLETED'}
            </span>
            <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
              Booking #{booking.bookingUuid.slice(-8)}
            </span>
          </div>
          <h2 style={{ margin: '6px 0 0 0', fontSize: '1.35rem', fontWeight: 800 }}>
            {tripStatus === 'DRIVER_ARRIVING' && `Navigating to ${booking.rider?.fullName || 'Passenger'}`}
            {tripStatus === 'DRIVER_ARRIVED' && `Waiting for ${booking.rider?.fullName || 'Passenger'}`}
            {tripStatus === 'IN_PROGRESS' && `Driving to Destination`}
            {tripStatus === 'COMPLETED' && 'Trip Finished'}
          </h2>
        </div>

        {/* Safety button */}
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            onClick={() => setShowSafetyModal(true)}
            style={{
              padding: '0.5rem 1rem',
              borderRadius: 8,
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid #ef4444',
              color: '#ef4444',
              fontSize: '0.85rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            🛡️ Safety Shield
          </button>
        </div>
      </div>

      {errorMessage && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'rgba(239, 68, 68, 0.2)',
            border: '1px solid #ef4444',
            color: '#fca5a5',
            borderRadius: 8,
            marginBottom: '1rem',
            fontSize: '0.9rem'
          }}
        >
          {errorMessage}
        </div>
      )}

      {/* Main Grid: Interactive Route Map + Driver Action Control Sheet */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 380px',
          gap: '1.5rem',
          alignItems: 'start'
        }}
      >
        {/* Map View */}
        <div
          style={{
            background: 'var(--surface, #1e293b)',
            borderRadius: 16,
            border: '1px solid var(--border-color, #334155)',
            overflow: 'hidden'
          }}
        >
          <div style={{ height: 440 }}>
            <InteractiveMap
              pickup={{
                latitude: booking.pickupLatitude,
                longitude: booking.pickupLongitude,
                address: booking.pickupAddress
              }}
              drop={{
                latitude: booking.dropLatitude,
                longitude: booking.dropLongitude,
                address: booking.dropAddress
              }}
              showDriver={true}
              driverLocation={driverCoords}
              tripStatus={tripStatus}
            />
          </div>

          {/* Quick External Map Navigation trigger */}
          <div
            style={{
              padding: '0.75rem 1.25rem',
              background: '#0f172a',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}
          >
            <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
              GPS Navigation Assistance
            </span>
            <button
              onClick={() =>
                openNavigation(
                  tripStatus === 'IN_PROGRESS' ? booking.dropLatitude : booking.pickupLatitude,
                  tripStatus === 'IN_PROGRESS' ? booking.dropLongitude : booking.pickupLongitude
                )
              }
              style={{
                padding: '0.4rem 0.85rem',
                borderRadius: 6,
                background: '#334155',
                border: '1px solid #475569',
                color: '#38bdf8',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              🗺️ Open Google Maps
            </button>
          </div>
        </div>

        {/* Right Action Sheet */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Passenger Details Card */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.25rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: '50%',
                    background: '#3b82f6',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '1.2rem',
                    fontWeight: 700,
                    color: '#fff'
                  }}
                >
                  {booking.rider?.fullName ? booking.rider.fullName[0] : 'R'}
                </div>
                <div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 700, color: '#f8fafc' }}>
                    {booking.rider?.fullName || 'Passenger'}
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#94a3b8' }}>
                    ⭐ {booking.rider?.rating || 4.9} · Cashless Rider
                  </div>
                </div>
              </div>

              {/* Call button */}
              <a
                href={`tel:${booking.rider?.phone || '+919876543210'}`}
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: '50%',
                  background: 'rgba(16, 185, 129, 0.15)',
                  border: '1px solid #10b981',
                  color: '#10b981',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  textDecoration: 'none',
                  fontSize: '1.1rem'
                }}
              >
                📞
              </a>
            </div>

            <div
              style={{
                marginTop: '1rem',
                paddingTop: '1rem',
                borderTop: '1px solid #334155',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.75rem'
              }}
            >
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>PICKUP LOCATION</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600, color: '#f1f5f9' }}>
                  {booking.pickupAddress}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>DROPOFF DESTINATION</div>
                <div style={{ fontSize: '0.9rem', fontWeight: 600, color: '#f1f5f9' }}>
                  {booking.dropAddress}
                </div>
              </div>
            </div>
          </div>

          {/* Lifecycle Action Buttons based on status */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.25rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            {/* Status 1: Driver Arriving -> Arrived button */}
            {tripStatus === 'DRIVER_ARRIVING' && (
              <div>
                <p style={{ margin: '0 0 1rem 0', fontSize: '0.85rem', color: '#94a3b8' }}>
                  Drive safely to the rider's pickup location. Tap below as soon as you have arrived at the spot.
                </p>
                <button
                  onClick={handleDriverArrived}
                  disabled={isActionLoading}
                  style={{
                    width: '100%',
                    padding: '0.9rem',
                    borderRadius: 12,
                    background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 800,
                    fontSize: '1rem',
                    cursor: 'pointer',
                    boxShadow: '0 4px 15px rgba(245, 158, 11, 0.4)'
                  }}
                >
                  {isActionLoading ? 'UPDATING...' : '📍 I HAVE ARRIVED AT PICKUP'}
                </button>
              </div>
            )}

            {/* Status 2: Driver Arrived -> Waiting timer & Enter OTP */}
            {tripStatus === 'DRIVER_ARRIVED' && (
              <div>
                <div
                  style={{
                    background: 'rgba(245, 158, 11, 0.1)',
                    border: '1px solid rgba(245, 158, 11, 0.3)',
                    padding: '0.75rem',
                    borderRadius: 10,
                    marginBottom: '1rem',
                    textAlign: 'center'
                  }}
                >
                  <div style={{ fontSize: '0.8rem', color: '#f59e0b', fontWeight: 600 }}>
                    WAITING AT PICKUP
                  </div>
                  <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fbbf24', marginTop: 2 }}>
                    ⏱️ {formatTimer(waitTimerSeconds)}
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
                    Standard grace period: 5 mins
                  </div>
                </div>

                <p style={{ margin: '0 0 1rem 0', fontSize: '0.85rem', color: '#94a3b8' }}>
                  Ask the passenger for their <strong>4-digit start OTP</strong>. You must enter this code to begin the trip.
                </p>

                <button
                  onClick={() => setShowOtpModal(true)}
                  style={{
                    width: '100%',
                    padding: '0.9rem',
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
                  🔑 ENTER START OTP
                </button>
              </div>
            )}

            {/* Status 3: In Progress -> Complete trip button */}
            {tripStatus === 'IN_PROGRESS' && (
              <div>
                <div
                  style={{
                    background: 'rgba(59, 130, 246, 0.1)',
                    border: '1px solid rgba(59, 130, 246, 0.3)',
                    padding: '0.75rem',
                    borderRadius: 10,
                    marginBottom: '1rem'
                  }}
                >
                  <div style={{ fontSize: '0.8rem', color: '#60a5fa', fontWeight: 600 }}>
                    TRIP METRICS
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                    <span style={{ fontSize: '0.9rem', color: '#f1f5f9' }}>
                      Est. Distance: {booking.estimatedDistanceKm || 7.4} km
                    </span>
                    <span style={{ fontSize: '0.9rem', color: '#f1f5f9' }}>
                      Est. Time: {booking.estimatedDurationMinutes || 22} min
                    </span>
                  </div>
                </div>

                <button
                  onClick={handleCompleteTrip}
                  disabled={isActionLoading}
                  style={{
                    width: '100%',
                    padding: '0.9rem',
                    borderRadius: 12,
                    background: 'linear-gradient(135deg, #ef4444, #dc2626)',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 800,
                    fontSize: '1rem',
                    cursor: 'pointer',
                    boxShadow: '0 4px 15px rgba(239, 68, 68, 0.4)'
                  }}
                >
                  {isActionLoading ? 'COMPLETING...' : '🏁 COMPLETE TRIP'}
                </button>
              </div>
            )}

            {/* Status 4: Completed -> Breakdown, Cash confirmation & Rating */}
            {tripStatus === 'COMPLETED' && (
              <div>
                <div style={{ textAlign: 'center', marginBottom: '1rem' }}>
                  <div style={{ fontSize: '2rem' }}>🎉</div>
                  <h3 style={{ margin: '4px 0', fontSize: '1.2rem', color: '#10b981' }}>
                    Trip Successfully Completed!
                  </h3>
                </div>

                {/* Earnings Breakdown */}
                <div
                  style={{
                    background: '#0f172a',
                    padding: '1rem',
                    borderRadius: 10,
                    border: '1px solid #334155',
                    marginBottom: '1rem'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Gross Passenger Fare</span>
                    <span style={{ color: '#f8fafc', fontWeight: 600 }}>₹{(booking.totalFare || 340).toFixed(2)}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>Platform Service Fee (20%)</span>
                    <span style={{ color: '#ef4444', fontWeight: 600 }}>-₹{commission.toFixed(2)}</span>
                  </div>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      borderTop: '1px solid #334155',
                      paddingTop: 6,
                      marginTop: 6
                    }}
                  >
                    <span style={{ color: '#10b981', fontWeight: 700 }}>Your Net Payout</span>
                    <span style={{ color: '#10b981', fontWeight: 800, fontSize: '1.15rem' }}>
                      ₹{netEarnings.toFixed(2)}
                    </span>
                  </div>
                </div>

                {/* Cash collection alert if Cash trip */}
                {!cashCollected && (
                  <div
                    style={{
                      background: 'rgba(234, 179, 8, 0.15)',
                      border: '1px solid #eab308',
                      padding: '0.85rem',
                      borderRadius: 10,
                      marginBottom: '1rem'
                    }}
                  >
                    <div style={{ fontSize: '0.85rem', color: '#facc15', fontWeight: 700, marginBottom: 4 }}>
                      💵 Cash Collection Required
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#e2e8f0', marginBottom: 8 }}>
                      Please collect ₹{(booking.totalFare || 340).toFixed(2)} in cash from passenger before parting.
                    </div>
                    <button
                      onClick={handleConfirmCash}
                      style={{
                        width: '100%',
                        padding: '0.5rem',
                        borderRadius: 6,
                        background: '#eab308',
                        color: '#0f172a',
                        fontWeight: 700,
                        fontSize: '0.85rem',
                        border: 'none',
                        cursor: 'pointer'
                      }}
                    >
                      ✓ Confirm Cash Received
                    </button>
                  </div>
                )}

                {/* Rate Rider Section */}
                {!ratingSubmitted ? (
                  <div style={{ marginBottom: '1rem' }}>
                    <div style={{ fontSize: '0.85rem', color: '#94a3b8', marginBottom: 6 }}>
                      Rate Passenger
                    </div>
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginBottom: 10 }}>
                      {[1, 2, 3, 4, 5].map((star) => (
                        <button
                          key={star}
                          onClick={() => setRiderRating(star)}
                          style={{
                            background: 'transparent',
                            border: 'none',
                            fontSize: '1.5rem',
                            cursor: 'pointer',
                            color: star <= riderRating ? '#facc15' : '#475569'
                          }}
                        >
                          ★
                        </button>
                      ))}
                    </div>
                    <button
                      onClick={handleRatingSubmit}
                      style={{
                        width: '100%',
                        padding: '0.65rem',
                        borderRadius: 8,
                        background: '#334155',
                        color: '#f8fafc',
                        border: '1px solid #475569',
                        fontWeight: 600,
                        cursor: 'pointer'
                      }}
                    >
                      Submit Rating
                    </button>
                  </div>
                ) : (
                  <div
                    style={{
                      textAlign: 'center',
                      color: '#10b981',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      marginBottom: '1rem'
                    }}
                  >
                    ✓ Rating submitted. Thank you!
                  </div>
                )}

                {/* Finish & Return button */}
                <button
                  onClick={onTripFinished}
                  style={{
                    width: '100%',
                    padding: '0.85rem',
                    borderRadius: 12,
                    background: 'linear-gradient(135deg, #10b981, #059669)',
                    border: 'none',
                    color: '#ffffff',
                    fontWeight: 800,
                    cursor: 'pointer'
                  }}
                >
                  RETURN TO DISPATCH RADAR
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Enter Start OTP Modal */}
      {showOtpModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.85)',
            backdropFilter: 'blur(6px)',
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
              maxWidth: 420,
              background: 'var(--surface, #1e293b)',
              borderRadius: 20,
              border: '1px solid var(--border-color, #334155)',
              padding: '1.75rem',
              boxShadow: '0 25px 50px -12px rgba(0,0,0,0.5)'
            }}
          >
            <div style={{ textAlign: 'center', marginBottom: '1.25rem' }}>
              <div style={{ fontSize: '2.5rem', marginBottom: 4 }}>🔐</div>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800 }}>
                Enter Rider's Start OTP
              </h3>
              <p style={{ margin: '6px 0 0 0', fontSize: '0.85rem', color: '#94a3b8' }}>
                Ask the passenger for the 4-digit verification code shown on their Lexor app.
              </p>
            </div>

            {otpError && (
              <div
                style={{
                  padding: '0.6rem 0.85rem',
                  borderRadius: 8,
                  background: 'rgba(239, 68, 68, 0.15)',
                  border: '1px solid #ef4444',
                  color: '#f87171',
                  fontSize: '0.85rem',
                  marginBottom: '1rem',
                  textAlign: 'center'
                }}
              >
                {otpError}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1.5rem' }}>
              <input
                type="text"
                maxLength={4}
                value={otpInput}
                onChange={(e) => setOtpInput(e.target.value.replace(/\D/g, ''))}
                placeholder="0000"
                style={{
                  width: 180,
                  height: 56,
                  textAlign: 'center',
                  fontSize: '2rem',
                  fontWeight: 800,
                  letterSpacing: '12px',
                  background: '#0f172a',
                  border: '2px solid #3b82f6',
                  borderRadius: 12,
                  color: '#ffffff',
                  outline: 'none'
                }}
                autoFocus
              />
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                onClick={() => {
                  setShowOtpModal(false);
                  setOtpError(null);
                }}
                style={{
                  flex: 1,
                  padding: '0.75rem',
                  borderRadius: 10,
                  background: '#334155',
                  border: 'none',
                  color: '#f8fafc',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Cancel
              </button>

              <button
                onClick={handleVerifyOtpAndStart}
                disabled={isActionLoading || otpInput.length !== 4}
                style={{
                  flex: 2,
                  padding: '0.75rem',
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #10b981, #059669)',
                  border: 'none',
                  color: '#ffffff',
                  fontWeight: 800,
                  cursor: isActionLoading || otpInput.length !== 4 ? 'not-allowed' : 'pointer',
                  opacity: isActionLoading || otpInput.length !== 4 ? 0.6 : 1
                }}
              >
                {isActionLoading ? 'VERIFYING...' : 'VERIFY & START TRIP'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Safety Modal */}
      <SafetyModal
        isOpen={showSafetyModal}
        onClose={() => setShowSafetyModal(false)}
        bookingUuid={booking.bookingUuid}
        driverName={booking.driver?.fullName}
        vehiclePlate={booking.vehicle?.licensePlate}
      />
    </div>
  );
};

export default DriverActiveTripView;
