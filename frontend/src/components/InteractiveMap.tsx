import React, { useState, useEffect } from 'react';
import { Navigation, MapPin, Crosshair, Car, ZoomIn, ZoomOut, ShieldCheck, AlertTriangle, Radio } from 'lucide-react';
import { mapProvider, LocationFreshness, RouteGeometryResult } from '../services/mapProvider';

interface Coordinates {
  latitude: number;
  longitude: number;
  address?: string;
  recordedAt?: string;
  heading?: number;
}

interface InteractiveMapProps {
  pickup: Coordinates;
  drop: Coordinates;
  driverLocation?: Coordinates;
  tripStatus?: string;
  onSelectPickup?: (coords: Coordinates) => void;
  onSelectDrop?: (coords: Coordinates) => void;
  className?: string;
  height?: string;
  showDriver?: boolean;
}

export const InteractiveMap: React.FC<InteractiveMapProps> = ({
  pickup,
  drop,
  driverLocation,
  tripStatus = 'IDLE',
  onSelectPickup,
  onSelectDrop,
  className = '',
  height = '380px',
  showDriver = false
}) => {
  const [zoomLevel, setZoomLevel] = useState<number>(1);
  const [activePinSelection, setActivePinSelection] = useState<'pickup' | 'drop' | null>(null);
  const [locatingUser, setLocatingUser] = useState<boolean>(false);
  const [locationNotice, setLocationNotice] = useState<string | null>(null);
  const [routeResult, setRouteResult] = useState<RouteGeometryResult | null>(null);

  const providerConfig = mapProvider.getProviderConfig();

  // Normalize coordinates into local canvas viewport coordinate space (1000 x 600)
  // Default bounds center on Bengaluru Metro Region
  const minLat = 12.85;
  const maxLat = 13.08;
  const minLng = 77.48;
  const maxLng = 77.78;

  const toCanvasX = (lng: number): number => {
    const clamped = Math.max(minLng, Math.min(maxLng, lng));
    const percent = (clamped - minLng) / (maxLng - minLng);
    return 100 + percent * 800;
  };

  const toCanvasY = (lat: number): number => {
    const clamped = Math.max(minLat, Math.min(maxLat, lat));
    const percent = 1 - (clamped - minLat) / (maxLat - minLat); // Invert Y
    return 60 + percent * 480;
  };

  // Fetch real road geometry on pickup or drop change
  useEffect(() => {
    let isMounted = true;
    mapProvider.getRouteGeometry(pickup, drop).then((res) => {
      if (isMounted) {
        setRouteResult(res);
      }
    });
    return () => {
      isMounted = false;
    };
  }, [pickup.latitude, pickup.longitude, drop.latitude, drop.longitude]);

  const pX = toCanvasX(pickup.longitude);
  const pY = toCanvasY(pickup.latitude);
  const dX = toCanvasX(drop.longitude);
  const dY = toCanvasY(drop.latitude);

  // Compute SVG path string from real road geometry if available, else smooth bezier fallback
  let pathD = '';
  if (routeResult && routeResult.coordinates.length > 1) {
    const pts = routeResult.coordinates.map(([lat, lng]) => `${toCanvasX(lng)} ${toCanvasY(lat)}`);
    pathD = `M ${pts[0]} L ${pts.slice(1).join(' L ')}`;
  } else {
    const midX = (pX + dX) / 2 + 45;
    const midY = (pY + dY) / 2 - 35;
    pathD = `M ${pX} ${pY} Q ${midX} ${midY} ${dX} ${dY}`;
  }

  // Driver marker position & freshness
  const isTripInProgress = tripStatus === 'IN_PROGRESS' || tripStatus === 'TRIP_STARTED';
  const driverX = driverLocation
    ? toCanvasX(driverLocation.longitude)
    : isTripInProgress
    ? (pX + dX) / 2 + 15
    : pX + 50;
  const driverY = driverLocation
    ? toCanvasY(driverLocation.latitude)
    : isTripInProgress
    ? (pY + dY) / 2 - 15
    : pY - 30;

  const freshness: LocationFreshness = driverLocation
    ? mapProvider.evaluateFreshness(driverLocation.recordedAt)
    : { isFresh: false, isStale: false, isOffline: true, ageSeconds: 9999, label: 'Simulated Demo Location', color: 'amber' };

  const handleMapSurfaceClick = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!activePinSelection) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const clickX = ((e.clientX - rect.left) / rect.width) * 1000;
    const clickY = ((e.clientY - rect.top) / rect.height) * 600;

    const newLng = minLng + ((clickX - 100) / 800) * (maxLng - minLng);
    const newLat = minLat + (1 - (clickY - 60) / 480) * (maxLat - minLat);

    const roundedLat = Math.round(newLat * 10000) / 10000;
    const roundedLng = Math.round(newLng * 10000) / 10000;

    if (activePinSelection === 'pickup' && onSelectPickup) {
      onSelectPickup({
        latitude: roundedLat,
        longitude: roundedLng,
        address: `Custom Pin (${roundedLat.toFixed(4)}, ${roundedLng.toFixed(4)})`
      });
      setLocationNotice('Pickup location pin placed');
    } else if (activePinSelection === 'drop' && onSelectDrop) {
      onSelectDrop({
        latitude: roundedLat,
        longitude: roundedLng,
        address: `Custom Destination (${roundedLat.toFixed(4)}, ${roundedLng.toFixed(4)})`
      });
      setLocationNotice('Destination pin placed');
    }
    setActivePinSelection(null);
    setTimeout(() => setLocationNotice(null), 3000);
  };

  const handleGetCurrentLocation = () => {
    if (!navigator.geolocation) {
      setLocationNotice('Geolocation not supported by your browser');
      return;
    }
    setLocatingUser(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocatingUser(false);
        const lat = Math.round(pos.coords.latitude * 10000) / 10000;
        const lng = Math.round(pos.coords.longitude * 10000) / 10000;
        if (onSelectPickup) {
          onSelectPickup({
            latitude: lat,
            longitude: lng,
            address: `Current Location (${lat}, ${lng})`
          });
        }
        setLocationNotice('Accurate device coordinates detected');
        setTimeout(() => setLocationNotice(null), 3000);
      },
      () => {
        setLocatingUser(false);
        setLocationNotice('Location permission denied or unavailable');
        setTimeout(() => setLocationNotice(null), 3500);
      },
      { timeout: 8000, enableHighAccuracy: true }
    );
  };

  return (
    <div
      className={`relative w-full rounded-2xl overflow-hidden border border-slate-800 bg-[#070b14] select-none ${className}`}
      style={{ height, minHeight: '320px' }}
      role="region"
      aria-label="Interactive Ride Navigation Map"
    >
      {/* Dynamic Dark Mode Vector Map Canvas */}
      <svg
        viewBox="0 0 1000 600"
        className="w-full h-full cursor-crosshair transition-transform duration-300"
        style={{ transform: `scale(${zoomLevel})` }}
        onClick={handleMapSurfaceClick}
      >
        <defs>
          <pattern id="roadGrid" width="60" height="60" patternUnits="userSpaceOnUse">
            <path d="M 60 0 L 0 0 0 60" fill="none" stroke="rgba(255, 255, 255, 0.04)" strokeWidth="1" />
            <circle cx="0" cy="0" r="1.5" fill="rgba(99, 102, 241, 0.2)" />
          </pattern>

          <linearGradient id="routeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#10b981" />
            <stop offset="60%" stopColor="#6366f1" />
            <stop offset="100%" stopColor="#ec4899" />
          </linearGradient>

          <filter id="glowEffect" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        <rect width="1000" height="600" fill="#070b14" />
        <rect width="1000" height="600" fill="url(#roadGrid)" />

        {/* Arterial Corridors */}
        <path d="M 50 120 Q 500 280 950 200" stroke="rgba(255, 255, 255, 0.07)" strokeWidth="8" fill="none" />
        <path d="M 120 540 Q 420 300 880 100" stroke="rgba(255, 255, 255, 0.06)" strokeWidth="7" fill="none" />
        <path d="M 280 40 Q 320 320 360 560" stroke="rgba(255, 255, 255, 0.05)" strokeWidth="6" fill="none" />
        <path d="M 700 30 Q 660 300 680 570" stroke="rgba(255, 255, 255, 0.05)" strokeWidth="6" fill="none" />

        {/* Bellandur / Ulsoor Lake Landmark */}
        <path
          d="M 560 320 C 600 300, 650 310, 680 340 C 700 380, 640 430, 590 410 C 550 390, 530 350, 560 320 Z"
          fill="rgba(6, 182, 212, 0.12)"
          stroke="rgba(6, 182, 212, 0.3)"
          strokeWidth="1.5"
        />

        {/* Route Shadow */}
        <path
          d={pathD}
          fill="none"
          stroke="rgba(99, 102, 241, 0.25)"
          strokeWidth="12"
          strokeLinecap="round"
        />

        {/* Active Route Polyline (Road Geometry) */}
        <path
          d={pathD}
          fill="none"
          stroke="url(#routeGradient)"
          strokeWidth="5"
          strokeDasharray="8 6"
          strokeLinecap="round"
          className="animate-pulse"
        />

        {/* Pickup Pin */}
        <g transform={`translate(${pX}, ${pY})`}>
          <circle r="18" fill="rgba(16, 185, 129, 0.2)" className="animate-ping" />
          <circle r="10" fill="#10b981" filter="url(#glowEffect)" />
          <circle r="4" fill="#ffffff" />
          <rect x="-35" y="-34" width="70" height="20" rx="4" fill="#0f172a" stroke="#10b981" strokeWidth="1" />
          <text x="0" y="-20" fill="#10b981" fontSize="10" fontWeight="bold" textAnchor="middle">
            PICKUP
          </text>
        </g>

        {/* Destination Pin */}
        <g transform={`translate(${dX}, ${dY})`}>
          <circle r="18" fill="rgba(239, 68, 68, 0.2)" className="animate-ping" />
          <circle r="10" fill="#ef4444" filter="url(#glowEffect)" />
          <circle r="4" fill="#ffffff" />
          <rect x="-32" y="-34" width="64" height="20" rx="4" fill="#0f172a" stroke="#ef4444" strokeWidth="1" />
          <text x="0" y="-20" fill="#ef4444" fontSize="10" fontWeight="bold" textAnchor="middle">
            DROP
          </text>
        </g>

        {/* Driver Assigned Marker */}
        {showDriver && (
          <g transform={`translate(${driverX}, ${driverY})`}>
            <circle
              r="16"
              fill={freshness.isStale ? 'rgba(245, 158, 11, 0.25)' : 'rgba(16, 185, 129, 0.25)'}
              className="animate-pulse"
            />
            <circle
              r="12"
              fill={freshness.isStale ? '#f59e0b' : '#10b981'}
              filter="url(#glowEffect)"
            />
            <path
              d="M -5 -3 L -3 -6 L 3 -6 L 5 -3 L 6 2 L -6 2 Z"
              fill="#0f172a"
            />
            <rect
              x="-48"
              y="16"
              width="96"
              height="18"
              rx="4"
              fill="#0f172a"
              stroke={freshness.isStale ? '#f59e0b' : '#10b981'}
              strokeWidth="1"
            />
            <text
              x="0"
              y="28"
              fill={freshness.isStale ? '#f59e0b' : '#10b981'}
              fontSize="9"
              fontWeight="bold"
              textAnchor="middle"
            >
              {driverLocation?.recordedAt ? 'LIVE DRIVER' : 'LEXOR DRIVER'}
            </text>
          </g>
        )}
      </svg>

      {/* Floating Status & Provider Chips */}
      <div className="absolute top-3 left-3 flex flex-wrap items-center gap-2 z-10 pointer-events-none">
        {/* Explicit Provider Mode Badge */}
        <div
          className={`flex items-center gap-2 px-3 py-1.5 rounded-lg backdrop-blur-md border text-xs font-semibold shadow-lg ${
            providerConfig.isDemoMode
              ? 'bg-amber-950/80 border-amber-600/50 text-amber-200'
              : 'bg-slate-900/90 border-slate-700/60 text-slate-200'
          }`}
        >
          {providerConfig.isDemoMode ? (
            <>
              <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
              <span>[DEMO MODE] {providerConfig.label}</span>
            </>
          ) : (
            <>
              <Radio className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
              <span>[LIVE] {providerConfig.label}</span>
            </>
          )}
        </div>

        {/* GPS Freshness Badge when driver marker is active */}
        {showDriver && (
          <div
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg backdrop-blur-md border text-xs font-bold ${
              freshness.isFresh
                ? 'bg-emerald-950/80 border-emerald-500/50 text-emerald-300'
                : freshness.isStale
                ? 'bg-amber-950/80 border-amber-500/50 text-amber-300'
                : 'bg-rose-950/80 border-rose-500/50 text-rose-300'
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full ${
                freshness.isFresh
                  ? 'bg-emerald-400 animate-ping'
                  : freshness.isStale
                  ? 'bg-amber-400'
                  : 'bg-rose-400'
              }`}
            />
            <span>{freshness.label}</span>
          </div>
        )}

        {tripStatus !== 'IDLE' && (
          <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-indigo-500/20 backdrop-blur-md border border-indigo-500/40 text-xs font-bold text-indigo-300">
            <span className="w-2 h-2 rounded-full bg-indigo-400 animate-ping" />
            <span>Trip: {tripStatus.replace('_', ' ')}</span>
          </div>
        )}
      </div>

      {/* User Feedback Notification Banner */}
      {locationNotice && (
        <div className="absolute top-12 left-1/2 -translate-x-1/2 z-20 px-4 py-2 rounded-xl bg-slate-900/95 border border-indigo-500/50 text-xs font-medium text-indigo-200 shadow-2xl flex items-center gap-2 animate-bounce">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>{locationNotice}</span>
        </div>
      )}

      {/* Interactive Controls */}
      <div className="absolute right-3 top-3 flex flex-col gap-2 z-10 pointer-events-auto">
        <button
          type="button"
          onClick={handleGetCurrentLocation}
          title="Detect Current GPS Location"
          className="p-2.5 rounded-xl bg-slate-900/90 hover:bg-slate-800 border border-slate-700 text-slate-200 hover:text-emerald-400 transition-colors shadow-lg active:scale-95"
          disabled={locatingUser}
        >
          <Crosshair className={`w-4 h-4 ${locatingUser ? 'animate-spin text-emerald-400' : ''}`} />
        </button>

        <button
          type="button"
          onClick={() => setActivePinSelection(activePinSelection === 'pickup' ? null : 'pickup')}
          title="Click map to relocate pickup pin"
          className={`p-2.5 rounded-xl border transition-colors shadow-lg active:scale-95 ${
            activePinSelection === 'pickup'
              ? 'bg-emerald-500 text-slate-950 border-emerald-400 font-bold'
              : 'bg-slate-900/90 hover:bg-slate-800 border-slate-700 text-emerald-400'
          }`}
        >
          <MapPin className="w-4 h-4" />
        </button>

        <button
          type="button"
          onClick={() => setActivePinSelection(activePinSelection === 'drop' ? null : 'drop')}
          title="Click map to relocate destination pin"
          className={`p-2.5 rounded-xl border transition-colors shadow-lg active:scale-95 ${
            activePinSelection === 'drop'
              ? 'bg-rose-500 text-slate-950 border-rose-400 font-bold'
              : 'bg-slate-900/90 hover:bg-slate-800 border-slate-700 text-rose-400'
          }`}
        >
          <Navigation className="w-4 h-4" />
        </button>

        <div className="flex flex-col rounded-xl overflow-hidden border border-slate-700 bg-slate-900/90 shadow-lg">
          <button
            type="button"
            onClick={() => setZoomLevel((z) => Math.min(2.0, z + 0.2))}
            title="Zoom In"
            className="p-2 hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
          >
            <ZoomIn className="w-4 h-4" />
          </button>
          <div className="h-[1px] bg-slate-800" />
          <button
            type="button"
            onClick={() => setZoomLevel((z) => Math.max(0.8, z - 0.2))}
            title="Zoom Out"
            className="p-2 hover:bg-slate-800 text-slate-300 hover:text-white transition-colors"
          >
            <ZoomOut className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Bottom Route Summary Pill */}
      <div className="absolute bottom-3 left-3 right-3 flex items-center justify-between gap-3 px-4 py-2.5 rounded-xl bg-slate-950/85 backdrop-blur-md border border-slate-800 text-xs text-slate-300 pointer-events-none">
        <div className="flex items-center gap-2 truncate">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 shrink-0" />
          <span className="truncate">{pickup.address || 'MG Road, Indiranagar'}</span>
          <span className="text-slate-500">→</span>
          <div className="w-2.5 h-2.5 rounded-full bg-rose-400 shrink-0" />
          <span className="truncate">{drop.address || 'Prestige Tech Park'}</span>
        </div>

        <div className="flex items-center gap-3 shrink-0 text-slate-400">
          <div className="flex items-center gap-1 font-semibold text-slate-200">
            <Car className="w-3.5 h-3.5 text-indigo-400" />
            <span>
              {routeResult?.distanceKm ? `${routeResult.distanceKm} km (${routeResult.durationMinutes} min)` : 'Live Navigation Ready'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
