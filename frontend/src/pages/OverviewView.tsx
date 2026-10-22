import React, { useState, useEffect, useCallback } from 'react';
import {
  Car,
  CheckCircle,
  XCircle,
  Radio,
  Clock,
  UserCheck,
  ShieldCheck,
  Users,
  DollarSign,
  CreditCard,
  RotateCcw,
  Star,
  AlertTriangle,
  FileCheck
} from 'lucide-react';
import { KpiCard } from '../components/KpiCard';
import { ExecutiveOverview } from '../types';
import { api } from '../services/api';

export const OverviewView: React.FC = () => {
  const [timeRange, setTimeRange] = useState<string>('TODAY');
  const [customStart, setCustomStart] = useState<string>('');
  const [customEnd, setCustomEnd] = useState<string>('');
  const [overview, setOverview] = useState<ExecutiveOverview | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchOverview = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getOverview(
        timeRange,
        timeRange === 'CUSTOM' ? customStart : undefined,
        timeRange === 'CUSTOM' ? customEnd : undefined
      );
      setOverview(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to fetch executive overview');
    } finally {
      setLoading(false);
    }
  }, [timeRange, customStart, customEnd]);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Executive Overview</h2>
          <p>Real-time authoritative platform health, trip volumes, fleet status, and revenue breakdown.</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <div className="time-filter-group">
            {['TODAY', '7D', '30D', '90D', 'CUSTOM'].map((range) => (
              <button
                key={range}
                className={`filter-pill-btn ${timeRange === range ? 'active' : ''}`}
                onClick={() => setTimeRange(range)}
              >
                {range}
              </button>
            ))}
          </div>

          {timeRange === 'CUSTOM' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <input
                type="date"
                className="select-filter"
                value={customStart}
                onChange={(e) => setCustomStart(e.target.value)}
                style={{ padding: '6px 10px', fontSize: '0.8rem' }}
              />
              <span style={{ color: 'var(--text-muted)' }}>to</span>
              <input
                type="date"
                className="select-filter"
                value={customEnd}
                onChange={(e) => setCustomEnd(e.target.value)}
                style={{ padding: '6px 10px', fontSize: '0.8rem' }}
              />
              <button className="btn-secondary" onClick={fetchOverview} style={{ padding: '6px 12px' }}>
                Apply
              </button>
            </div>
          )}
        </div>
      </div>

      {loading && !overview && (
        <div className="loading-box">
          <div className="spinner" />
          <p>Aggregating authoritative platform telemetry...</p>
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--danger-border)', background: 'var(--danger-bg)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--danger)' }}>
            <AlertTriangle size={20} />
            <strong>Telemetry Error:</strong> {error}
          </div>
        </div>
      )}

      {overview && (
        <>
          {/* Section 1: Trip & Fleet Volumes */}
          <div className="kpi-grid">
            <KpiCard
              title="Total Bookings"
              value={overview.totalRides}
              icon={Car}
              theme="indigo"
              subtitle={`Active range: ${overview.timeRange}`}
              definition={overview.metricDefinitions?.totalRides}
            />
            <KpiCard
              title="Completed Rides"
              value={overview.completedRides}
              icon={CheckCircle}
              theme="emerald"
              subtitle="Terminal status COMPLETED"
              definition={overview.metricDefinitions?.completedRides}
            />
            <KpiCard
              title="Cancelled Rides"
              value={overview.cancelledRides}
              icon={XCircle}
              theme="rose"
              subtitle="Rider/Driver/Admin cancellations"
              definition={overview.metricDefinitions?.cancelledRides}
            />
            <KpiCard
              title="Active Trips"
              value={overview.activeRides}
              icon={Radio}
              theme="cyan"
              subtitle="En-route / in-progress right now"
              definition={overview.metricDefinitions?.activeRides}
            />
          </div>

          {/* Section 2: Financial Integrity */}
          <div className="kpi-grid">
            <KpiCard
              title="Gross Booking Value"
              value={overview.grossBookingValue}
              isCurrency={true}
              icon={DollarSign}
              theme="emerald"
              subtitle="Total calculated fares"
              definition={overview.metricDefinitions?.grossBookingValue}
            />
            <KpiCard
              title="Collections Collected"
              value={overview.successfulPaymentTotals}
              isCurrency={true}
              icon={CreditCard}
              theme="indigo"
              subtitle="Payment Service COMPLETED state"
              definition={overview.metricDefinitions?.successfulPaymentTotals}
            />
            <KpiCard
              title="Platform Commission"
              value={overview.platformCommissionEarned}
              isCurrency={true}
              icon={FileCheck}
              theme="amber"
              subtitle="Retained platform cut"
              definition={overview.metricDefinitions?.platformCommissionEarned}
            />
            <KpiCard
              title="Disbursed Refunds"
              value={overview.refundTotals}
              isCurrency={true}
              icon={RotateCcw}
              theme="rose"
              subtitle="Rider dispute refunds"
              definition={overview.metricDefinitions?.refundTotals}
            />
          </div>

          {/* Section 3: Drivers, Riders & Moderation */}
          <div className="kpi-grid">
            <KpiCard
              title="Online Drivers"
              value={overview.onlineDrivers}
              icon={UserCheck}
              theme="emerald"
              subtitle="Available for dispatch"
            />
            <KpiCard
              title="Pending Verification"
              value={overview.pendingDriverVerifications}
              icon={ShieldCheck}
              theme="amber"
              subtitle="KYC/License awaiting audit"
            />
            <KpiCard
              title="Registered Riders"
              value={overview.registeredRiders}
              icon={Users}
              theme="cyan"
              subtitle="Total user accounts"
            />
            <KpiCard
              title="Average Driver Rating"
              value={overview.averageDriverRating > 0 ? `${overview.averageDriverRating.toFixed(2)} ★` : 'N/A'}
              icon={Star}
              theme="amber"
              subtitle="Feedback Service verified reviews"
              definition={overview.metricDefinitions?.averageDriverRating}
            />
          </div>

          {/* Context footer */}
          <div className="glass-panel" style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              <Clock size={16} />
              <span>Snapshot computed at: {new Date(overview.calculatedAt).toLocaleString()} ({overview.timeZone})</span>
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              Awaiting Moderation Reviews: <strong style={{ color: overview.feedbackReportsAwaitingModeration > 0 ? 'var(--warning)' : 'var(--success)' }}>{overview.feedbackReportsAwaitingModeration}</strong>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
