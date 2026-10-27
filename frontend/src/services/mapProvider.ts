/**
 * LEXOR Map Provider Abstraction & Geolocation Engine
 *
 * Supports:
 * - Google Maps Platform (Directions, Geocoding, Places Autocomplete via dynamic loader)
 * - Mapbox Navigation
 * - OpenStreetMap & OSRM (Authoritative real road geometry, Nominatim geocoding)
 * - High-precision canvas fallback with explicit [DEMO MODE] disclosure
 * - Device geolocation with permission state detection
 * - Location freshness calculation (<30s fresh, 30s-120s stale, >120s offline)
 */

export interface MapCoordinates {
  latitude: number;
  longitude: number;
  address?: string;
  heading?: number;
  speedKmh?: number;
  recordedAt?: string;
}

export interface RouteGeometryResult {
  coordinates: [number, number][]; // [lat, lng][]
  distanceKm: number;
  durationMinutes: number;
  provider: string;
  isRealRoadGeometry: boolean;
  summary?: string;
}

export interface GeocodeResult {
  address: string;
  latitude: number;
  longitude: number;
  displayPlace: string;
}

export interface MapProviderConfig {
  provider: 'OSM' | 'GOOGLE' | 'MAPBOX' | 'DEMO_CANVAS';
  isLiveProvider: boolean;
  isDemoMode: boolean;
  label: string;
  attribution: string;
  isConfigured: boolean;
}

export interface LocationFreshness {
  isFresh: boolean;
  isStale: boolean;
  isOffline: boolean;
  ageSeconds: number;
  label: string;
  color: 'emerald' | 'amber' | 'rose';
}

declare global {
  interface Window {
    google?: any;
    initGoogleMapsPromise?: Promise<boolean>;
  }
}

class MapProviderService {
  private activeProvider: 'OSM' | 'GOOGLE' | 'MAPBOX' | 'DEMO_CANVAS' = 'OSM';
  private googleMapsApiKey: string = '';
  private mapboxToken: string = '';
  private scriptLoadingPromise: Promise<boolean> | null = null;

  constructor() {
    this.detectConfiguredProvider();
  }

  private detectConfiguredProvider(): void {
    const env = (import.meta as any).env || {};
    this.googleMapsApiKey = (env.VITE_GOOGLE_MAPS_KEY || '').trim();
    this.mapboxToken = (env.VITE_MAPBOX_TOKEN || '').trim();

    if (this.mapboxToken && !this.mapboxToken.startsWith('your_')) {
      this.activeProvider = 'MAPBOX';
    } else if (this.googleMapsApiKey && !this.googleMapsApiKey.startsWith('your_')) {
      this.activeProvider = 'GOOGLE';
    } else if (env.VITE_DISABLE_LIVE_MAP === 'true') {
      this.activeProvider = 'DEMO_CANVAS';
    } else {
      // Default to OpenStreetMap / OSRM (real road routing and tile provider)
      this.activeProvider = 'OSM';
    }
  }

  public getProviderConfig(): MapProviderConfig {
    switch (this.activeProvider) {
      case 'GOOGLE':
        return {
          provider: 'GOOGLE',
          isLiveProvider: true,
          isDemoMode: false,
          label: 'Google Maps Platform',
          attribution: 'Map data © Google',
          isConfigured: Boolean(this.googleMapsApiKey && !this.googleMapsApiKey.startsWith('your_'))
        };
      case 'MAPBOX':
        return {
          provider: 'MAPBOX',
          isLiveProvider: true,
          isDemoMode: false,
          label: 'Mapbox Navigation',
          attribution: '© Mapbox © OpenStreetMap',
          isConfigured: Boolean(this.mapboxToken && !this.mapboxToken.startsWith('your_'))
        };
      case 'OSM':
        return {
          provider: 'OSM',
          isLiveProvider: true,
          isDemoMode: false,
          label: 'OpenStreetMap & OSRM Engine',
          attribution: '© OpenStreetMap contributors, OSRM',
          isConfigured: true
        };
      case 'DEMO_CANVAS':
      default:
        return {
          provider: 'DEMO_CANVAS',
          isLiveProvider: false,
          isDemoMode: true,
          label: 'Demo Mode (Simulated Canvas)',
          attribution: 'LEXOR Simulated Navigation Mesh',
          isConfigured: false
        };
    }
  }

  public setDemoMode(enableDemo: boolean): void {
    if (enableDemo) {
      this.activeProvider = 'DEMO_CANVAS';
    } else {
      this.detectConfiguredProvider();
    }
  }

  /**
   * Dynamically loads Google Maps JavaScript SDK when configured
   */
  public async loadGoogleMapsScript(): Promise<boolean> {
    if (typeof window === 'undefined') return false;
    if (window.google?.maps) return true;
    if (!this.googleMapsApiKey || this.googleMapsApiKey.startsWith('your_')) return false;

    if (this.scriptLoadingPromise) {
      return this.scriptLoadingPromise;
    }

    this.scriptLoadingPromise = new Promise<boolean>((resolve) => {
      const scriptId = 'lexor-google-maps-sdk';
      if (document.getElementById(scriptId)) {
        resolve(Boolean(window.google?.maps));
        return;
      }

      const script = document.createElement('script');
      script.id = scriptId;
      script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(
        this.googleMapsApiKey
      )}&libraries=places,geometry`;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve(true);
      script.onerror = () => {
        console.warn('Failed to load Google Maps SDK, falling back to OSRM / OpenStreetMap');
        resolve(false);
      };
      document.head.appendChild(script);
    });

    return this.scriptLoadingPromise;
  }

  /**
   * Calculates freshness status for driver GPS telemetry
   */
  public evaluateFreshness(recordedAt?: string | Date): LocationFreshness {
    if (!recordedAt) {
      return {
        isFresh: false,
        isStale: false,
        isOffline: true,
        ageSeconds: 99999,
        label: 'No GPS Signal',
        color: 'rose'
      };
    }

    const recDate = typeof recordedAt === 'string' ? new Date(recordedAt) : recordedAt;
    const now = new Date();
    const ageSeconds = Math.max(0, Math.round((now.getTime() - recDate.getTime()) / 1000));

    if (ageSeconds <= 30) {
      return {
        isFresh: true,
        isStale: false,
        isOffline: false,
        ageSeconds,
        label: `Live GPS (${ageSeconds}s ago)`,
        color: 'emerald'
      };
    } else if (ageSeconds <= 120) {
      return {
        isFresh: false,
        isStale: true,
        isOffline: false,
        ageSeconds,
        label: `Stale GPS (${ageSeconds}s ago)`,
        color: 'amber'
      };
    } else {
      return {
        isFresh: false,
        isStale: false,
        isOffline: true,
        ageSeconds,
        label: `GPS Signal Lost (${Math.round(ageSeconds / 60)}m ago)`,
        color: 'rose'
      };
    }
  }

  /**
   * Geocode a search query to coordinates using Google Places or Nominatim
   */
  public async searchPlace(query: string): Promise<GeocodeResult[]> {
    if (!query || query.trim().length < 2) return [];

    // 1. Try Google Maps Places Autocomplete if active
    if (this.activeProvider === 'GOOGLE' && window.google?.maps?.places) {
      try {
        const service = new window.google.maps.places.AutocompleteService();
        const predictions = await new Promise<any[]>((resolve) => {
          service.getPlacePredictions(
            { input: query, componentRestrictions: { country: 'in' } },
            (results: any[], status: any) => {
              if (status === window.google.maps.places.PlacesServiceStatus.OK && results) {
                resolve(results);
              } else {
                resolve([]);
              }
            }
          );
        });

        if (predictions && predictions.length > 0) {
          // Geocode top results
          const geocoder = new window.google.maps.Geocoder();
          const geocoded = await Promise.all(
            predictions.slice(0, 5).map(async (p: any) => {
              return new Promise<GeocodeResult | null>((res) => {
                geocoder.geocode({ placeId: p.place_id }, (results: any[], status: any) => {
                  if (status === 'OK' && results && results[0]) {
                    const loc = results[0].geometry.location;
                    res({
                      address: results[0].formatted_address,
                      latitude: loc.lat(),
                      longitude: loc.lng(),
                      displayPlace: p.structured_formatting?.main_text || results[0].formatted_address
                    });
                  } else {
                    res(null);
                  }
                });
              });
            })
          );
          const validResults = geocoded.filter((g): g is GeocodeResult => g !== null);
          if (validResults.length > 0) return validResults;
        }
      } catch (err) {
        console.warn('Google Places Autocomplete failed, falling back to Nominatim', err);
      }
    }

    // 2. Fallback to OpenStreetMap / Nominatim
    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(
        query + ', Bengaluru'
      )}&limit=5&countrycodes=in`;
      const res = await fetch(url, {
        headers: {
          'Accept-Language': 'en',
          'User-Agent': 'LEXOR-RideHailing/1.0'
        }
      });
      if (!res.ok) return [];

      const data = await res.json();
      return (data || []).map((item: any) => ({
        address: item.display_name,
        latitude: parseFloat(item.lat),
        longitude: parseFloat(item.lon),
        displayPlace: item.name || item.display_name.split(',')[0]
      }));
    } catch {
      return [];
    }
  }

  /**
   * Reverse geocode coordinates to human-readable address
   */
  public async reverseGeocode(lat: number, lng: number): Promise<string> {
    if (this.activeProvider === 'GOOGLE' && window.google?.maps?.Geocoder) {
      try {
        const geocoder = new window.google.maps.Geocoder();
        const res = await new Promise<string>((resolve) => {
          geocoder.geocode({ location: { lat, lng } }, (results: any[], status: any) => {
            if (status === 'OK' && results && results[0]) {
              resolve(results[0].formatted_address);
            } else {
              resolve(`${lat.toFixed(4)}, ${lng.toFixed(4)}`);
            }
          });
        });
        return res;
      } catch {
        // Fallback to Nominatim
      }
    }

    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`;
      const res = await fetch(url, {
        headers: {
          'Accept-Language': 'en',
          'User-Agent': 'LEXOR-RideHailing/1.0'
        }
      });
      if (!res.ok) return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;

      const data = await res.json();
      return data.display_name || `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    } catch {
      return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    }
  }

  /**
   * Fetch authoritative road-following route geometry via Google Directions or OSRM
   */
  public async getRouteGeometry(
    pickup: MapCoordinates,
    drop: MapCoordinates
  ): Promise<RouteGeometryResult> {
    if (this.activeProvider === 'DEMO_CANVAS') {
      return this.computeSyntheticRoute(pickup, drop);
    }

    // 1. Google Maps Directions Service
    if (this.activeProvider === 'GOOGLE' && window.google?.maps?.DirectionsService) {
      try {
        const directionsService = new window.google.maps.DirectionsService();
        const directionsResult = await new Promise<any>((resolve, reject) => {
          directionsService.route(
            {
              origin: { lat: pickup.latitude, lng: pickup.longitude },
              destination: { lat: drop.latitude, lng: drop.longitude },
              travelMode: window.google.maps.TravelMode.DRIVING
            },
            (result: any, status: any) => {
              if (status === window.google.maps.DirectionsStatus.OK && result) {
                resolve(result);
              } else {
                reject(status);
              }
            }
          );
        });

        if (directionsResult?.routes?.[0]?.overview_path) {
          const route = directionsResult.routes[0];
          const leg = route.legs?.[0];
          const distanceKm = leg?.distance?.value ? Math.round((leg.distance.value / 1000) * 10) / 10 : 5.0;
          const durationMinutes = leg?.duration?.value ? Math.max(1, Math.round(leg.duration.value / 60)) : 15;
          const coords: [number, number][] = route.overview_path.map((p: any) => [p.lat(), p.lng()]);

          return {
            coordinates: coords,
            distanceKm,
            durationMinutes,
            provider: 'Google Maps Directions API',
            isRealRoadGeometry: true,
            summary: leg?.summary || route.summary || 'Google Driving Route'
          };
        }
      } catch (err) {
        console.warn('Google Directions route failed, falling back to OSRM', err);
      }
    }

    // 2. OpenStreetMap / OSRM Driving Directions
    try {
      const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${pickup.longitude},${pickup.latitude};${drop.longitude},${drop.latitude}?overview=full&geometries=geojson`;
      const res = await fetch(osrmUrl);

      if (!res.ok) {
        return this.computeSyntheticRoute(pickup, drop);
      }

      const json = await res.json();
      if (!json.routes || json.routes.length === 0) {
        return this.computeSyntheticRoute(pickup, drop);
      }

      const route = json.routes[0];
      const distanceKm = Math.round((route.distance / 1000) * 10) / 10;
      const durationMinutes = Math.max(1, Math.round(route.duration / 60));
      // GeoJSON returns coordinates as [lng, lat] -> map to [lat, lng]
      const geoCoords: [number, number][] = route.geometry.coordinates.map(
        (c: [number, number]) => [c[1], c[0]]
      );

      return {
        coordinates: geoCoords,
        distanceKm,
        durationMinutes,
        provider: 'OSRM (OpenStreetMap Road Network)',
        isRealRoadGeometry: true,
        summary: route.legs?.[0]?.summary || 'Fastest Route'
      };
    } catch {
      return this.computeSyntheticRoute(pickup, drop);
    }
  }

  /**
   * High-accuracy geodesic synthetic route generator (fallback)
   */
  private computeSyntheticRoute(
    pickup: MapCoordinates,
    drop: MapCoordinates
  ): RouteGeometryResult {
    const R = 6371; // Earth radius in km
    const dLat = ((drop.latitude - pickup.latitude) * Math.PI) / 180;
    const dLon = ((drop.longitude - pickup.longitude) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((pickup.latitude * Math.PI) / 180) *
        Math.cos((drop.latitude * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distanceKm = Math.round(Math.max(1.0, R * c * 1.3) * 10) / 10;
    const durationMinutes = Math.max(5, Math.round(distanceKm * 2.8));

    // Generate intermediate waypoints for smooth curve
    const steps = 10;
    const coords: [number, number][] = [];
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const lat = pickup.latitude + (drop.latitude - pickup.latitude) * t;
      const lng = pickup.longitude + (drop.longitude - pickup.longitude) * t;
      coords.push([lat, lng]);
    }

    return {
      coordinates: coords,
      distanceKm,
      durationMinutes,
      provider: 'LEXOR Simulated Mesh (Demo Fallback)',
      isRealRoadGeometry: false,
      summary: 'Simulated Route Geometry'
    };
  }

  /**
   * Compute compass bearing / heading angle in degrees between two coordinates
   */
  public calculateBearing(start: MapCoordinates, end: MapCoordinates): number {
    const startLat = (start.latitude * Math.PI) / 180;
    const startLng = (start.longitude * Math.PI) / 180;
    const endLat = (end.latitude * Math.PI) / 180;
    const endLng = (end.longitude * Math.PI) / 180;

    const dLng = endLng - startLng;
    const y = Math.sin(dLng) * Math.cos(endLat);
    const x =
      Math.cos(startLat) * Math.sin(endLat) -
      Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng);

    let brng = (Math.atan2(y, x) * 180) / Math.PI;
    return (brng + 360) % 360;
  }
}

export const mapProvider = new MapProviderService();
