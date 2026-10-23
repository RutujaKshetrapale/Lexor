import React, { useState, useEffect } from 'react';
import { TripBookingDetail, DetailedInvoiceReceipt } from '../../types';
import { InteractiveMap } from '../../components/InteractiveMap';
import { SafetyModal } from '../../components/SafetyModal';
import { RiderReceiptModal } from './RiderReceiptModal';
import { RiderRatingModal } from './RiderRatingModal';
import { api } from '../../services/api';
import { realtimeService, RealtimeConnectionStatus, DriverLocationPayload } from '../../services/realtime';
import {
  Shield,
  Phone,
  MessageSquare,
  Lock,
  Clock,
  Navigation,
  Car,
  XCircle,
  Receipt,
  Star,
  RefreshCw,
  Radio
} from 'lucide-react';

export interface RiderActiveTripViewProps {
  booking?: TripBookingDetail | null;
  initialBooking?: TripBookingDetail | null;
  onTripCancelled?: () => void;
  onTripFinished?: () => void;
  onBookAnother?: () => void;
}

const DEFAULT_RIDER_BOOKING: TripBookingDetail = {
  bookingUuid: 'bk-rider-live-01',
  status: 'DRIVER_ARRIVING',
  rider: {
    uuid: 'rider-01',
    fullName: 'Ananya Sharma',
    phone: '+91 98765 43210',
    rating: 4.95
  },
  driver: {
    uuid: 'driver-01',
    fullName: 'Vikram Singh',
    phone: '+91 98111 22334',
    rating: 4.92,
    licenseNumber: 'KA-04-2018-0098412'
  },
  vehicle: {
    make: 'Hyundai',
    model: 'Aura',
    color: 'Polar White',
    licensePlate: 'KA 05 MN 4821',
    year: 2023,
    category: 'SEDAN'
  },
  rideType: {
    code: 'GO',
    name: 'LEXOR Go'
  },
  pickupAddress: 'Indiranagar 100ft Road, Bengaluru',
  pickupLatitude: 12.9784,
  pickupLongitude: 77.6408,
  dropAddress: 'Prestige Tech Park, Marathahalli, Bengaluru',
  dropLatitude: 12.9372,
  dropLongitude: 77.6949,
  estimatedDistanceKm: 7.4,
  estimatedDurationMinutes: 22,
  totalFare: 340,
  currency: 'INR',
  otp: '4829',
  otpVerified: false,
  createdAt: new Date().toISOString()
};

export const RiderActiveTripView: React.FC<RiderActiveTripViewProps> = ({
  booking: propBooking,
  initialBooking,
  onTripCancelled,
  onTripFinished,
  onBookAnother
}) => {
  const effectiveBooking = propBooking || initialBooking || DEFAULT_RIDER_BOOKING;
  const [booking, setBooking] = useState<TripBookingDetail>(effectiveBooking);
  const [safetyOpen, setSafetyOpen] = useState<boolean>(false);
  const [receiptOpen, setReceiptOpen] = useState<boolean>(false);
  const [ratingOpen, setRatingOpen] = useState<boolean>(false);
  const [cancelModalOpen, setCancelModalOpen] = useState<boolean>(false);
  const [cancelReason, setCancelReason] = useState<string>('Driver taking too long');
  const [isCancelling, setIsCancelling] = useState<boolean>(false);
  const [receiptData, setReceiptData] = useState<DetailedInvoiceReceipt | null>(null);
  const [rtStatus, setRtStatus] = useState<RealtimeConnectionStatus>(realtimeService.getStatus());
  const [driverLocation, setDriverLocation] = useState<{ latitude: number; longitude: number; recordedAt?: string } | undefined>(undefined);

  // Real-time Event Subscriptions & Fallback Sync
  useEffect(() => {
    // 1. Connection status tracking
    const unsubStatus = realtimeService.onStatusChange((status) => setRtStatus(status));

    // 2. Authoritative sync when reconnected
    const unsubSync = realtimeService.onStateSync(async () => {
      try {
        const fresh = await api.getBookingDetails(booking.bookingUuid);
        if (fresh) setBooking(fresh);
      } catch {
        // ignore
      }
    });

    // 3. Driver Live Location Streaming
    const unsubLoc = realtimeService.subscribe<DriverLocationPayload>('DRIVER_LOCATION_UPDATE', (loc) => {
      if (loc && loc.latitude && loc.longitude) {
        setDriverLocation({
          latitude: loc.latitude,
          longitude: loc.longitude,
          recordedAt: loc.timestamp
        });
      }
    });

    // 4. Trip Status Transitions
    const unsubArrived = realtimeService.subscribe('DRIVER_ARRIVED', () => {
      setBooking((prev) => ({ ...prev, status: 'DRIVER_ARRIVED' }));
    });

    const unsubStarted = realtimeService.subscribe('TRIP_STARTED', () => {
      setBooking((prev) => ({ ...prev, status: 'IN_PROGRESS', otpVerified: true }));
    });

    const unsubCompleted = realtimeService.subscribe('TRIP_COMPLETED', (payload) => {
      setBooking((prev) => ({
        ...prev,
        status: 'COMPLETED',
        completedAt: new Date().toISOString(),
        ...(payload || {})
      }));
    });

    const unsubCancelled = realtimeService.subscribe('BOOKING_CANCELLED', () => {
      setBooking((prev) => ({ ...prev, status: 'CANCELLED' }));
    });

    // 5. Payment Status Updates
    const unsubPayment = realtimeService.subscribe('PAYMENT_STATUS_UPDATE', (pay) => {
      if (pay?.status === 'SUCCEEDED') {
        setReceiptOpen(true);
      }
    });

    // Fallback polling every 8s if real-time stream is disconnected
    let pollInterval: any = null;
    if (booking.status !== 'COMPLETED' && booking.status !== 'CANCELLED') {
      pollInterval = setInterval(async () => {
        try {
          const updated = await api.getBookingDetails(booking.bookingUuid);
          if (updated) {
            setBooking(updated);
          }
        } catch {
          // offline simulation
        }
      }, 8000);
    }

    return () => {
      unsubStatus();
      unsubSync();
      unsubLoc();
      unsubArrived();
      unsubStarted();
      unsubCompleted();
      unsubCancelled();
      unsubPayment();
      if (pollInterval) clearInterval(pollInterval);
    };
  }, [booking.bookingUuid, booking.status]);

  // Demo state advancement for immediate interactive feedback
  const handleSimulateNextStep = () => {
    if (booking.status === 'CONFIRMED' || booking.status === 'PENDING') {
      setBooking((prev) => ({
        ...prev,
        status: 'DRIVER_ARRIVING',
        driver: {
          uuid: 'drv-1',
          fullName: 'Vikram Rathore',
          phone: '+919876543222',
          rating: 4.92,
          licenseNumber: 'KA-01-2019-0098471'
        },
        vehicle: {
          make: 'Maruti Suzuki',
          model: 'Dzire',
          color: 'Arctic White',
          licensePlate: 'KA-01-EQ-4921',
          year: 2023,
          category: 'GO'
        },
        otp: prev.otp || '4829',
        arrivedAt: new Date().toISOString()
      }));
    } else if (booking.status === 'DRIVER_ARRIVING') {
      setBooking((prev) => ({
        ...prev,
        status: 'DRIVER_ARRIVED',
        arrivedAt: new Date().toISOString()
      }));
    } else if (booking.status === 'DRIVER_ARRIVED') {
      setBooking((prev) => ({
        ...prev,
        status: 'IN_PROGRESS',
        startedAt: new Date().toISOString(),
        otpVerified: true
      }));
    } else if (booking.status === 'IN_PROGRESS') {
      const receipt: DetailedInvoiceReceipt = {
        paymentUuid: `pay-${Date.now()}`,
        bookingUuid: booking.bookingUuid,
        transactionId: `TXN-${Math.random().toString(36).substring(2, 9).toUpperCase()}`,
        paymentMethod: 'UPI',
        status: 'COMPLETED',
        currency: 'INR',
        baseFare: 50.0,
        distanceFare: Math.round(booking.estimatedDistanceKm * 15),
        timeFare: Math.round(booking.estimatedDurationMinutes * 2),
        surgeMultiplier: 1.0,
        surgeAmount: 0.0,
        taxAmount: Math.round(booking.totalFare * 0.05),
        discountAmount: 0.0,
        totalFare: booking.totalFare,
        driverEarnings: Math.round(booking.totalFare * 0.8),
        platformCommission: Math.round(booking.totalFare * 0.2),
        pickupAddress: booking.pickupAddress,
        dropAddress: booking.dropAddress,
        distanceKm: booking.estimatedDistanceKm,
        durationMinutes: booking.estimatedDurationMinutes,
        paidAt: new Date().toISOString()
      };
      setReceiptData(receipt);
      setBooking((prev) => ({
        ...prev,
        status: 'COMPLETED',
        completedAt: new Date().toISOString()
      }));
      setReceiptOpen(true);
    }
  };

  const handleConfirmCancel = async () => {
    setIsCancelling(true);
    try {
      await api.cancelBooking(booking.bookingUuid, cancelReason);
      setBooking((prev) => ({ ...prev, status: 'CANCELLED', cancellationReason: cancelReason }));
      setCancelModalOpen(false);
      if (onTripCancelled) onTripCancelled();
    } catch {
      setBooking((prev) => ({ ...prev, status: 'CANCELLED', cancellationReason: cancelReason }));
      setCancelModalOpen(false);
      if (onTripCancelled) onTripCancelled();
    } finally {
      setIsCancelling(false);
    }
  };

  const driver = booking.driver || {
    uuid: 'drv-demo',
    fullName: 'Vikram Rathore',
    phone: '+919876543222',
    rating: 4.92,
    licenseNumber: 'KA-01-2019-0098471'
  };

  const vehicle = booking.vehicle || {
    make: 'Maruti Suzuki',
    model: 'Dzire',
    color: 'Arctic White',
    licensePlate: 'KA-01-EQ-4921',
    year: 2023,
    category: 'GO'
  };

  const otpCode = booking.otp || '4829';

  return (
    <div className="max-w-5xl mx-auto px-4 py-4 space-y-4 animate-fade-in">
      {/* Top Banner & Status Stepper */}
      <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <Car className="w-5 h-5" />
            </div>
            <div>
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400">
                Booking ID: {booking.bookingUuid.slice(0, 8)}
              </span>
              <h2 className="text-base font-extrabold text-white flex items-center gap-2">
                <span>
                  {booking.status === 'CONFIRMED' && 'Driver Assigned & Dispatching'}
                  {booking.status === 'DRIVER_ARRIVING' && 'Driver is Arriving (ETA 3 mins)'}
                  {booking.status === 'DRIVER_ARRIVED' && 'Driver Has Arrived at Pickup'}
                  {booking.status === 'IN_PROGRESS' && 'Trip in Progress to Destination'}
                  {booking.status === 'COMPLETED' && 'Journey Completed'}
                  {booking.status === 'CANCELLED' && 'Ride Cancelled'}
                </span>
              </h2>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Real-time SSE Stream Status Badge */}
            <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-slate-900 border border-slate-700 text-xs font-semibold text-slate-300">
              <Radio className={`w-3 h-3 ${rtStatus === 'CONNECTED' ? 'text-emerald-400 animate-pulse' : 'text-amber-400'}`} />
              <span className="hidden sm:inline">
                {rtStatus === 'CONNECTED' ? 'Live Stream' : rtStatus === 'CONNECTING' || rtStatus === 'RECONNECTING' ? 'Connecting...' : 'Polling Fallback'}
              </span>
            </div>

            <button
              type="button"
              onClick={() => setSafetyOpen(true)}
              className="py-1.5 px-3 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 text-xs font-semibold flex items-center gap-1.5 transition-colors"
            >
              <Shield className="w-3.5 h-3.5" />
              <span>Safety</span>
            </button>

            {/* Interactive State Advance Button for rapid verification */}
            {booking.status !== 'COMPLETED' && booking.status !== 'CANCELLED' && (
              <button
                type="button"
                onClick={handleSimulateNextStep}
                className="py-1.5 px-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-md shadow-indigo-950/40"
                title="Progress trip simulation to next milestone"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Simulate Next Step</span>
              </button>
            )}
          </div>
        </div>

        {/* Status Stepper Tracker */}
        <div className="grid grid-cols-4 gap-2 pt-3 text-center">
          <div className={`p-2 rounded-xl text-xs font-semibold ${
            ['CONFIRMED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(booking.status)
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
              : 'bg-slate-900 text-slate-500'
          }`}>
            <span>1. Matched</span>
          </div>
          <div className={`p-2 rounded-xl text-xs font-semibold ${
            ['DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED'].includes(booking.status)
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
              : 'bg-slate-900 text-slate-500'
          }`}>
            <span>2. Arriving</span>
          </div>
          <div className={`p-2 rounded-xl text-xs font-semibold ${
            ['IN_PROGRESS', 'COMPLETED'].includes(booking.status)
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
              : 'bg-slate-900 text-slate-500'
          }`}>
            <span>3. In Trip</span>
          </div>
          <div className={`p-2 rounded-xl text-xs font-semibold ${
            booking.status === 'COMPLETED'
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
              : 'bg-slate-900 text-slate-500'
          }`}>
            <span>4. Arrived</span>
          </div>
        </div>
      </div>

      {/* Main Grid: Interactive Map + Driver / OTP Sidecards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Map Visualization (2 Cols) */}
        <div className="lg:col-span-2">
          <InteractiveMap
            pickup={{
              latitude: booking.pickupLatitude || 12.9784,
              longitude: booking.pickupLongitude || 77.6408,
              address: booking.pickupAddress
            }}
            drop={{
              latitude: booking.dropLatitude || 12.9372,
              longitude: booking.dropLongitude || 77.6949,
              address: booking.dropAddress
            }}
            driverLocation={driverLocation}
            tripStatus={booking.status}
            showDriver={true}
            height="440px"
          />
        </div>

        {/* Right Info Column */}
        <div className="space-y-4">
          {/* OTP Verification Card */}
          {booking.status !== 'COMPLETED' && booking.status !== 'CANCELLED' && (
            <div className="p-4 rounded-2xl bg-gradient-to-br from-indigo-950/60 to-purple-950/40 border border-indigo-500/30 shadow-lg">
              <div className="flex items-center justify-between mb-2">
                <span className="text-[11px] font-bold uppercase tracking-wider text-indigo-300 flex items-center gap-1.5">
                  <Lock className="w-3.5 h-3.5 text-indigo-400" />
                  <span>Start Ride OTP</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-semibold">
                  Required by Driver
                </span>
              </div>
              <div className="p-3 rounded-xl bg-slate-950/80 border border-indigo-500/40 text-center my-2">
                <span className="text-3xl font-black tracking-widest text-white font-mono">{otpCode}</span>
              </div>
              <p className="text-[11px] text-slate-300 text-center">
                Tell this code to driver <span className="font-semibold text-white">{driver.fullName}</span> to verify identity and start navigation.
              </p>
            </div>
          )}

          {/* Matched Driver & Vehicle Details Card */}
          <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-11 h-11 rounded-full bg-indigo-600/20 border border-indigo-500/30 text-indigo-400 flex items-center justify-center font-bold text-base">
                  {driver.fullName[0]}
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white leading-tight">{driver.fullName}</h3>
                  <div className="flex items-center gap-1 text-xs text-amber-400 font-semibold mt-0.5">
                    <Star className="w-3 h-3 fill-amber-400" />
                    <span>{driver.rating} ★ (520 rides)</span>
                  </div>
                </div>
              </div>

              <div className="text-right">
                <span className="text-[11px] font-mono font-bold text-white bg-slate-900 px-2 py-1 rounded-lg border border-slate-700">
                  {vehicle.licensePlate}
                </span>
                <p className="text-[11px] text-slate-400 mt-1">
                  {vehicle.color} {vehicle.make}
                </p>
              </div>
            </div>

            {/* Quick Driver Contact Actions */}
            <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800">
              <a
                href={`tel:${driver.phone}`}
                className="py-2 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs font-semibold border border-slate-700 flex items-center justify-center gap-1.5 transition-colors"
              >
                <Phone className="w-3.5 h-3.5 text-emerald-400" />
                <span>Call Driver</span>
              </a>

              <button
                type="button"
                onClick={() => alert(`Messaging channel opened with ${driver.fullName}`)}
                className="py-2 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs font-semibold border border-slate-700 flex items-center justify-center gap-1.5 transition-colors"
              >
                <MessageSquare className="w-3.5 h-3.5 text-indigo-400" />
                <span>Chat Message</span>
              </button>
            </div>
          </div>

          {/* Journey Metrics Card */}
          <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 space-y-2 text-xs">
            <div className="flex justify-between text-slate-300">
              <span className="flex items-center gap-1 text-slate-400">
                <Navigation className="w-3.5 h-3.5 text-indigo-400" />
                <span>Total Journey Distance:</span>
              </span>
              <span className="font-semibold text-white">{booking.estimatedDistanceKm} km</span>
            </div>
            <div className="flex justify-between text-slate-300">
              <span className="flex items-center gap-1 text-slate-400">
                <Clock className="w-3.5 h-3.5 text-amber-400" />
                <span>Est. Duration:</span>
              </span>
              <span className="font-semibold text-white">{booking.estimatedDurationMinutes} mins</span>
            </div>
            <div className="flex justify-between text-slate-300 pt-1 border-t border-slate-800">
              <span className="text-slate-400">Fare Total (UPI):</span>
              <span className="font-bold text-emerald-400 text-sm">₹{booking.totalFare.toFixed(2)}</span>
            </div>
          </div>

          {/* Cancellation / Completion Actions */}
          {booking.status !== 'COMPLETED' && booking.status !== 'CANCELLED' ? (
            <button
              type="button"
              onClick={() => setCancelModalOpen(true)}
              className="w-full py-2.5 px-3 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-xs font-semibold border border-rose-500/30 flex items-center justify-center gap-1.5 transition-colors"
            >
              <XCircle className="w-4 h-4" />
              <span>Cancel Ride Booking</span>
            </button>
          ) : (
            <div className="space-y-2">
              <button
                type="button"
                onClick={() => setReceiptOpen(true)}
                className="w-full py-2.5 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold border border-slate-700 flex items-center justify-center gap-1.5 transition-colors"
              >
                <Receipt className="w-4 h-4 text-emerald-400" />
                <span>View Detailed Receipt</span>
              </button>

              <button
                type="button"
                onClick={() => setRatingOpen(true)}
                className="w-full py-2.5 px-3 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 text-xs font-bold border border-amber-500/30 flex items-center justify-center gap-1.5 transition-colors"
              >
                <Star className="w-4 h-4 fill-amber-400" />
                <span>Rate Driver</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  if (onBookAnother) onBookAnother();
                  if (onTripFinished) onTripFinished();
                }}
                className="w-full py-2.5 px-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-colors shadow-lg shadow-indigo-950/40"
              >
                Book Another Ride
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Cancel Confirmation Dialog Modal */}
      {cancelModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
          <div className="relative w-full max-w-sm rounded-2xl bg-[#0c1220] border border-slate-700 p-5 shadow-2xl text-slate-100">
            <h3 className="text-base font-bold text-white mb-2">Cancel Ride Request?</h3>
            <p className="text-xs text-slate-400 mb-3">
              Driver is currently en route. Are you sure you wish to cancel this booking?
            </p>

            <label className="block text-xs font-semibold text-slate-300 mb-1">Reason for cancellation:</label>
            <select
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              className="w-full p-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white mb-4 focus:outline-none focus:border-indigo-500"
            >
              <option value="Driver taking too long">Driver taking too long</option>
              <option value="Changed my destination">Changed my destination</option>
              <option value="Booked by mistake">Booked by mistake</option>
              <option value="Driver asked to cancel">Driver asked to cancel</option>
              <option value="Found alternative ride">Found alternative ride</option>
            </select>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setCancelModalOpen(false)}
                className="flex-1 py-2 px-3 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold hover:bg-slate-700"
              >
                Keep Ride
              </button>
              <button
                type="button"
                onClick={handleConfirmCancel}
                disabled={isCancelling}
                className="flex-1 py-2 px-3 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold disabled:opacity-50"
              >
                {isCancelling ? 'Cancelling...' : 'Confirm Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Safety Modal */}
      <SafetyModal
        isOpen={safetyOpen}
        onClose={() => setSafetyOpen(false)}
        bookingUuid={booking.bookingUuid}
        driverName={driver.fullName}
        vehiclePlate={vehicle.licensePlate}
      />

      {/* Transparent Fare Breakdown Receipt Modal */}
      <RiderReceiptModal
        isOpen={receiptOpen}
        onClose={() => setReceiptOpen(false)}
        receipt={
          receiptData || {
            paymentUuid: `pay-${booking.bookingUuid.slice(0, 8)}`,
            bookingUuid: booking.bookingUuid,
            transactionId: 'TXN-9824921',
            paymentMethod: 'UPI',
            status: 'COMPLETED',
            currency: 'INR',
            baseFare: 50.0,
            distanceFare: Math.round(booking.estimatedDistanceKm * 15),
            timeFare: Math.round(booking.estimatedDurationMinutes * 2),
            surgeMultiplier: 1.0,
            surgeAmount: 0.0,
            taxAmount: Math.round(booking.totalFare * 0.05),
            discountAmount: 0.0,
            totalFare: booking.totalFare,
            driverEarnings: Math.round(booking.totalFare * 0.8),
            platformCommission: Math.round(booking.totalFare * 0.2),
            pickupAddress: booking.pickupAddress,
            dropAddress: booking.dropAddress,
            distanceKm: booking.estimatedDistanceKm,
            durationMinutes: booking.estimatedDurationMinutes,
            paidAt: new Date().toISOString()
          }
        }
      />

      {/* Two-Sided Rating Modal */}
      <RiderRatingModal
        isOpen={ratingOpen}
        onClose={() => setRatingOpen(false)}
        bookingUuid={booking.bookingUuid}
        driverName={driver.fullName}
      />
    </div>
  );
};
