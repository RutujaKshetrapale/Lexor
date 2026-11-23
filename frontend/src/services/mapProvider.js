/**
 * LEXOR Real-Time Map & Routing Service
 * Connects with OpenStreetMap / OSRM and Leaflet for authentic navigation
 */

export const POPULAR_PLACES = [
  {
    name: 'Indiranagar 100ft Road',
    address: 'Indiranagar 100ft Road, Bengaluru',
    lat: 12.9784,
    lng: 77.6408,
    category: 'popular'
  },
  {
    name: 'Prestige Tech Park',
    address: 'Prestige Tech Park, Marathahalli, Bengaluru',
    lat: 12.9352,
    lng: 77.6953,
    category: 'work'
  },
  {
    name: 'Koramangala Sony World',
    address: 'Koramangala 4th Block, Bengaluru',
    lat: 12.9352,
    lng: 77.6245,
    category: 'popular'
  },
  {
    name: 'MG Road Metro Station',
    address: 'MG Road, Central Business District, Bengaluru',
    lat: 12.9756,
    lng: 77.6066,
    category: 'popular'
  },
  {
    name: 'Kempegowda Int. Airport (BLR)',
    address: 'Terminal 2, Kempegowda Int. Airport, Devanahalli',
    lat: 13.1986,
    lng: 77.7066,
    category: 'airport'
  },
  {
    name: 'Electronic City Phase 1',
    address: 'Electronic City Tollgate, Hosur Road, Bengaluru',
    lat: 12.8452,
    lng: 77.6602,
    category: 'work'
  }
];

class MapService {
  /**
   * Geocode a place string into coordinates
   */
  async geocode(placeText) {
    if (!placeText || typeof placeText !== 'string') {
      return POPULAR_PLACES[0];
    }

    const query = placeText.toLowerCase();
    const matched = POPULAR_PLACES.find(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        p.address.toLowerCase().includes(query)
    );

    if (matched) return matched;

    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(
        placeText
      )}&limit=1`;
      const res = await fetch(url, { headers: { 'User-Agent': 'LexorMobilityApp/1.0' } });
      if (res.ok) {
        const data = await res.json();
        if (data && data.length > 0) {
          return {
            name: placeText,
            address: data[0].display_name,
            lat: parseFloat(data[0].lat),
            lng: parseFloat(data[0].lon)
          };
        }
      }
    } catch (e) {
      console.warn('Nominatim geocode failed, using fallback:', e);
    }

    return POPULAR_PLACES[0];
  }

  /**
   * Fetch real route geometry and turn points between two lat/lng coordinates
   */
  async getRouteGeometry(origin, destination) {
    const oLat = origin.lat;
    const oLng = origin.lng;
    const dLat = destination.lat;
    const dLng = destination.lng;

    try {
      const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${oLng},${oLat};${dLng},${dLat}?overview=full&geometries=geojson`;
      const res = await fetch(osrmUrl);

      if (res.ok) {
        const json = await res.json();
        if (json.routes && json.routes.length > 0) {
          const route = json.routes[0];
          const distanceKm = Math.round((route.distance / 1000) * 10) / 10;
          const durationMinutes = Math.max(2, Math.round(route.duration / 60));
          const coordinates = route.geometry.coordinates.map(([lng, lat]) => [lat, lng]);

          return {
            coordinates,
            distanceKm,
            durationMinutes,
            provider: 'OSRM'
          };
        }
      }
    } catch (err) {
      console.warn('OSRM route fetch failed, using fallback interpolation:', err);
    }

    // Geodesic curve interpolation fallback
    return this.calculateFallbackRoute(origin, destination);
  }

  calculateFallbackRoute(origin, destination) {
    const R = 6371; // Earth radius in km
    const dLat = ((destination.lat - origin.lat) * Math.PI) / 180;
    const dLon = ((destination.lng - origin.lng) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((origin.lat * Math.PI) / 180) *
        Math.cos((destination.lat * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distanceKm = Math.max(2.5, Math.round(R * c * 1.35 * 10) / 10);
    const durationMinutes = Math.max(5, Math.round(distanceKm * 2.5));

    const steps = 18;
    const coordinates = [];
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      // Add slight curved deviation to look like a realistic road path
      const curvedOffset = Math.sin(t * Math.PI) * 0.006;
      const lat = origin.lat + (destination.lat - origin.lat) * t + curvedOffset;
      const lng = origin.lng + (destination.lng - origin.lng) * t - curvedOffset * 0.5;
      coordinates.push([lat, lng]);
    }

    return {
      coordinates,
      distanceKm,
      durationMinutes,
      provider: 'Interpolated'
    };
  }

  /**
   * Calculate fare estimates for categories given distance in km
   */
  calculateFares(distanceKm = 8.5) {
    const km = Math.max(2, distanceKm);
    return {
      economy: {
        id: 'economy',
        name: 'Economy',
        fare: `₹${Math.round(50 + km * 14)}`,
        rawFare: Math.round(50 + km * 14),
        time: '3 min',
        etaMinutes: 3,
        image: '/car_economy.png',
        desc: 'Affordable, compact rides for daily commute'
      },
      comfort: {
        id: 'comfort',
        name: 'Comfort',
        fare: `₹${Math.round(75 + km * 18)}`,
        rawFare: Math.round(75 + km * 18),
        time: '5 min',
        etaMinutes: 5,
        image: '/car_comfort.png',
        desc: 'Newer sedans with top-rated drivers and extra legroom'
      },
      premium: {
        id: 'premium',
        name: 'Premium',
        fare: `₹${Math.round(110 + km * 24)}`,
        rawFare: Math.round(110 + km * 24),
        time: '6 min',
        etaMinutes: 6,
        image: '/car_premium.png',
        desc: 'High-end executive sedans with premium amenities'
      },
      xl: {
        id: 'xl',
        name: 'XL',
        fare: `₹${Math.round(150 + km * 32)}`,
        rawFare: Math.round(150 + km * 32),
        time: '8 min',
        etaMinutes: 8,
        image: '/car_xl.png',
        desc: 'Spacious 6-seater SUVs for groups and luggage'
      }
    };
  }
}

export const mapService = new MapService();
