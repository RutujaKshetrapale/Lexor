export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

export interface MetricDefinitions {
  [key: string]: string;
}

export interface ExecutiveOverview {
  totalRides: number;
  completedRides: number;
  cancelledRides: number;
  activeRides: number;
  pendingRideRequests: number;
  onlineDrivers: number;
  inTripDrivers: number;
  pendingDriverVerifications: number;
  registeredRiders: number;
  registeredDrivers: number;
  registeredVehicles: number;
  grossBookingValue: number;
  successfulPaymentTotals: number;
  refundTotals: number;
  platformCommissionEarned: number;
  averageDriverRating: number;
  feedbackReportsAwaitingModeration: number;
  timeRange: string;
  rangeStartDate: string;
  rangeEndDate: string;
  timeZone: string;
  calculatedAt: string;
  metricDefinitions: MetricDefinitions;
}

export interface DriverStateBreakdown {
  onlineAvailable: number;
  busyInTrip: number;
  offline: number;
  totalDrivers: number;
}

export interface ActiveRideItem {
  bookingUuid: string;
  rideUuid: string;
  riderUuid: string;
  driverUuid: string;
  pickupAddress: string;
  dropoffAddress: string;
  status: string;
  estimatedFare: number;
  startTime: string;
}

export interface PendingRequestItem {
  requestId: string;
  riderUuid: string;
  rideType: string;
  status: string;
  pickupAddress: string;
  dropoffAddress: string;
  waitingDurationSeconds: number;
  createdAt: string;
}

export interface RecentCancellationItem {
  bookingUuid: string;
  cancelledBy: string;
  reason: string;
  cancelledAt: string;
}

export interface OperationalAlert {
  alertType: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  message: string;
  detectedAt: string;
}

export interface LiveOperationsSnapshot {
  snapshotTimestamp: string;
  refreshIntervalSeconds: number;
  driverStates: DriverStateBreakdown;
  activeRides: ActiveRideItem[];
  pendingRequests: PendingRequestItem[];
  recentCancellations: RecentCancellationItem[];
  activeAlerts: OperationalAlert[];
}

export interface DailyRidePoint {
  date: string;
  totalRides: number;
  completedRides: number;
  cancelledRides: number;
}

export interface HourlyDemandPoint {
  hourOfDay: number;
  requestVolume: number;
}

export interface DailyFinancialPoint {
  date: string;
  grossBookingValue: number;
  platformCommission: number;
  driverPayouts: number;
}

export interface PlatformTrends {
  timeRange: string;
  dailyRides: DailyRidePoint[];
  hourlyDemand: HourlyDemandPoint[];
  dailyFinancials: DailyFinancialPoint[];
}

export interface ServiceHealthItem {
  serviceName: string;
  port: number;
  endpointUrl: string;
  status: 'UP' | 'DEGRADED' | 'DOWN';
  latencyMs: number;
  readiness: string;
  errorDetails?: string;
  lastChecked: string;
}

export interface ServiceHealthStatus {
  checkedAt: string;
  totalServices: number;
  healthyServices: number;
  degradedServices: number;
  downServices: number;
  overallSystemStatus: 'UP' | 'DEGRADED' | 'DOWN';
  services: ServiceHealthItem[];
}

export interface AdminAuditLog {
  id?: number;
  auditUuid: string;
  adminUserUuid: string;
  action: string;
  resourceType: string;
  resourceUuid: string;
  reason: string;
  ipAddress?: string;
  status: string;
  createdAt: string;
}

export interface AdminSetting {
  settingKey: string;
  settingValue: string;
  description: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface RideBooking {
  bookingUuid: string;
  rideUuid: string;
  riderUuid: string;
  driverUuid: string;
  vehicleUuid?: string;
  status: string;
  pickupAddress: string;
  dropoffAddress: string;
  fareAmount: number;
  paymentStatus: string;
  cancellationReason?: string;
  createdAt: string;
}

export interface RiderProfile {
  userUuid: string;
  fullName: string;
  email: string;
  phone: string;
  status: 'ACTIVE' | 'SUSPENDED';
  totalRides: number;
  joinedAt: string;
}

export interface DriverProfile {
  driverUuid: string;
  userUuid: string;
  fullName: string;
  phone: string;
  licenseNumber: string;
  onboardingStatus: string;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED';
  isAvailable: boolean;
  rating: number;
  totalTrips: number;
  createdAt: string;
}

export interface VehicleProfile {
  vehicleUuid: string;
  driverUuid?: string;
  make: string;
  model: string;
  year: number;
  licensePlate: string;
  color: string;
  vehicleType: string;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  isActive: boolean;
}

export interface PaymentRecord {
  paymentUuid: string;
  bookingUuid: string;
  riderUuid: string;
  amount: number;
  paymentMethod: 'CASH' | 'CARD' | 'WALLET' | 'UPI';
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
  transactionId: string;
  createdAt: string;
}

export interface FeedbackReview {
  reviewUuid: string;
  bookingUuid: string;
  authorUuid: string;
  targetUuid: string;
  targetRole: 'RIDER' | 'DRIVER';
  rating: number;
  comment: string;
  moderationStatus: 'VISIBLE' | 'UNDER_REVIEW' | 'HIDDEN' | 'REMOVED';
  reportReason?: string;
  reportCount: number;
  createdAt: string;
}

// ==============================================================================
// Rider & Driver Identity, Authentication & Profile Models
// ==============================================================================

export type UserRole = 'ROLE_RIDER' | 'ROLE_DRIVER' | 'ROLE_ADMIN' | 'RIDER' | 'DRIVER' | 'ADMIN';

export interface AuthUser {
  id: number;
  uuid: string;
  firstName: string;
  lastName: string;
  fullName?: string;
  email: string;
  phoneNumber: string;
  role: UserRole;
  status: 'ACTIVE' | 'PENDING' | 'SUSPENDED' | 'DEACTIVATED';
  profilePictureUrl?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt?: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface SavedPlace {
  id: string;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  type: 'home' | 'work' | 'favorite';
}

// ==============================================================================
// Ride Categories & Dynamic Fare Estimation Models
// ==============================================================================

export interface RideTypeCategory {
  uuid: string;
  name: string;
  code: string;
  description: string;
  baseFare: number;
  perKmRate: number;
  perMinuteRate: number;
  minimumFare: number;
  cancellationFee: number;
  capacity: number;
  iconUrl?: string;
  isActive: boolean;
}

export interface RideFareEstimate {
  rideTypeUuid: string;
  rideTypeCode: string;
  rideTypeName: string;
  capacity: number;
  estimatedDistanceKm: number;
  estimatedDurationMinutes: number;
  estimatedFare: number;
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  surgeMultiplier: number;
  currency: string;
  iconUrl?: string;
}

// ==============================================================================
// Ride Request & Confirmed Booking Lifecycle Models
// ==============================================================================

export type RideRequestStatusType = 'REQUESTED' | 'SEARCHING' | 'MATCHED' | 'CANCELLED' | 'EXPIRED';

export interface RideRequestModel {
  requestUuid: string;
  riderUuid: string;
  riderName?: string;
  riderEmail?: string;
  pickupAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropAddress: string;
  dropLatitude: number;
  dropLongitude: number;
  rideTypeCode: string;
  rideTypeName: string;
  estimatedDistanceKm: number;
  estimatedDurationMinutes: number;
  estimatedFare: number;
  currency: string;
  status: RideRequestStatusType;
  requestedAt: string;
  createdAt?: string;
  updatedAt?: string;
}

export type TripLifecycleStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'DRIVER_ASSIGNED'
  | 'DRIVER_ARRIVING'
  | 'DRIVER_ARRIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export interface TripBookingDetail {
  bookingUuid: string;
  rideRequestUuid?: string;
  status: TripLifecycleStatus;
  rider?: {
    uuid: string;
    fullName: string;
    phone: string;
    rating: number;
  };
  driver?: {
    uuid: string;
    fullName: string;
    phone: string;
    rating: number;
    licenseNumber: string;
  };
  vehicle?: {
    make: string;
    model: string;
    color: string;
    licensePlate: string;
    year: number;
    category: string;
  };
  rideType?: {
    name: string;
    code: string;
    iconUrl?: string;
  };
  pickupAddress: string;
  pickupLatitude: number;
  pickupLongitude: number;
  dropAddress: string;
  dropLatitude: number;
  dropLongitude: number;
  estimatedDistanceKm: number;
  estimatedDurationMinutes: number;
  totalFare: number;
  currency: string;
  otp?: string;
  otpVerified?: boolean;
  scheduledTime?: string;
  cancellationReason?: string;
  cancelledBy?: string;
  acceptedAt?: string;
  arrivedAt?: string;
  startedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt?: string;
}

// ==============================================================================
// Driver Onboarding, Documents & Vehicle Models
// ==============================================================================

export interface DriverDocumentItem {
  id: number;
  documentType: string;
  documentNumber: string;
  documentUrl: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';
  rejectionReason?: string;
  expiryDate?: string;
  uploadedAt: string;
}

export interface DriverOnboardingChecklist {
  isProfileComplete: boolean;
  isDocumentsSubmitted: boolean;
  isVerificationApproved: boolean;
  isVehicleRegistered: boolean;
  isVehicleVerified: boolean;
  overallStatus: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
  checklist: {
    key: string;
    name: string;
    isComplete: boolean;
    description: string;
  }[];
}

export interface DriverEarningsSummary {
  todayTotal: number;
  weekTotal: number;
  monthTotal: number;
  completedTripsCount: number;
  onlineHoursToday: number;
  trips: {
    bookingUuid: string;
    completedAt: string;
    pickupAddress: string;
    dropAddress: string;
    fare: number;
    driverEarnings: number;
    paymentMethod: string;
  }[];
}

// ==============================================================================
// Payments, Invoices & Safety Models
// ==============================================================================

export interface PaymentMethodOption {
  code: 'UPI' | 'CARD' | 'CASH' | 'WALLET';
  displayName: string;
  description: string;
  isEnabled: boolean;
  badge?: string;
}

export interface DetailedInvoiceReceipt {
  paymentUuid: string;
  bookingUuid: string;
  transactionId: string;
  paymentMethod: string;
  status: string;
  currency: string;
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  surgeMultiplier: number;
  surgeAmount: number;
  taxAmount: number;
  discountAmount: number;
  totalFare: number;
  driverEarnings: number;
  platformCommission: number;
  pickupAddress: string;
  dropAddress: string;
  distanceKm: number;
  durationMinutes: number;
  paidAt: string;
}

export interface EmergencyContactItem {
  id?: number;
  name: string;
  phoneNumber: string;
  relationship: string;
}

