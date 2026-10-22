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
