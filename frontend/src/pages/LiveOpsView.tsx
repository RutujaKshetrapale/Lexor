import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Radio,
  AlertTriangle,
  Play,
  Pause,
  RefreshCw,
  Clock,
  Car,
  AlertCircle
} from 'lucide-react';
import { LiveOperationsSnapshot, ActiveRideItem } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const LiveOpsView: React.FC = () => {
  const [snapshot, setSnapshot] = useState<LiveOperationsSnapshot | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [pollInterval, setPollInterval] = useState<number>(10);
  const [isPollingPaused, setIsPollingPaused] = useState<boolean>(false);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  // Emergency cancellation modal state
  const [cancelModalOpen, setCancelModalOpen] = useState<boolean>(false);
  const [selectedRide, setSelectedRide] = useState<ActiveRideItem | null>(null);
  const [cancelReason, setCancelReason] = useState<string>('');
  const [submittingCancel, setSubmittingCancel] = useState<boolean>(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const timerRef = useRef<any>(null);

  const fetchLiveSnapshot = useCallback(async () => {
    try {
      const data = await api.getLiveSnapshot();
      setSnapshot(data);
      setLastUpdated(new Date());
      setError(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Live telemetry poll failed');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLiveSnapshot();

    if (!isPollingPaused && pollInterval > 0) {
      timerRef.current = setInterval(() => {
        fetchLiveSnapshot();
      }, pollInterval * 1000);
    }

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [fetchLiveSnapshot, pollInterval, isPollingPaused]);

  const handleOpenCancelModal = (ride: ActiveRideItem) => {
    setSelectedRide(ride);
    setCancelReason('');
    setActionSuccess(null);
    setCancelModalOpen(true);
  };

  const handleExecuteEmergencyCancel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRide || !cancelReason.trim()) return;

    setSubmittingCancel(true);
    try {
      const msg = await api.emergencyCancelRide(selectedRide.bookingUuid, cancelReason.trim());
      setActionSuccess(msg || 'Emergency ride cancellation completed and logged.');
      setCancelModalOpen(false);
      fetchLiveSnapshot();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to execute cancellation override');
    } finally {
      setSubmittingCancel(false);
    }
  };

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Live Platform Dispatch Console</h2>
          <p>Real-time vehicle fleet status, in-flight trips, and dispatch queue. Last synced: {lastUpdated.toLocaleTimeString()}.</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div className="time-filter-group">
            {[5, 10, 30].map((sec) => (
              <button
                key={sec}
                className={`filter-pill-btn ${pollInterval === sec && !isPollingPaused ? 'active' : ''}`}
                onClick={() => {
                  setPollInterval(sec);
                  setIsPollingPaused(false);
                }}
              >
                {sec}s
              </button>
            ))}
            <button
              className={`filter-pill-btn ${isPollingPaused ? 'active' : ''}`}
              onClick={() => setIsPollingPaused(!isPollingPaused)}
            >
              {isPollingPaused ? <Play size={12} /> : <Pause size={12} />}
              <span style={{ marginLeft: '4px' }}>{isPollingPaused ? 'Resume' : 'Pause'}</span>
            </button>
          </div>

          <button className="btn-secondary" onClick={fetchLiveSnapshot} title="Manual Refresh">
            <RefreshCw size={14} className={loading ? 'spinner' : ''} />
            <span>Sync</span>
          </button>
        </div>
      </div>

      {actionSuccess && (
        <div className="glass-panel" style={{ borderColor: 'var(--success-border)', background: 'var(--success-bg)', marginBottom: '16px' }}>
          <div style={{ color: 'var(--success)', fontWeight: 600 }}>{actionSuccess}</div>
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--danger-border)', background: 'var(--danger-bg)', marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--danger)' }}>
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
        </div>
      )}

      {snapshot && (
        <>
          {/* Driver Fleet Distribution Meter */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <Radio size={20} color="var(--primary)" />
                <span>Driver Fleet Real-Time Availability</span>
              </div>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Total Fleet: {snapshot.driverStates.totalDrivers} drivers registered
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px', marginBottom: '16px' }}>
              <div style={{ background: 'rgba(16, 185, 129, 0.08)', border: '1px solid var(--success-border)', borderRadius: 'var(--radius-md)', padding: '16px' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--success)', textTransform: 'uppercase' }}>Available Online</div>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#fff', marginTop: '4px' }}>{snapshot.driverStates.onlineAvailable}</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '4px' }}>Ready for matching</div>
              </div>

              <div style={{ background: 'rgba(99, 102, 241, 0.08)', border: '1px solid rgba(99, 102, 241, 0.3)', borderRadius: 'var(--radius-md)', padding: '16px' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--primary-light)', textTransform: 'uppercase' }}>Busy (In Trip)</div>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#fff', marginTop: '4px' }}>{snapshot.driverStates.busyInTrip}</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '4px' }}>Carrying passengers</div>
              </div>

              <div style={{ background: 'rgba(255, 255, 255, 0.03)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: '16px' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Offline Drivers</div>
                <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#fff', marginTop: '4px' }}>{snapshot.driverStates.offline}</div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '4px' }}>Disconnected from app</div>
              </div>
            </div>
          </div>

          {/* Active Operational Alerts */}
          {snapshot.activeAlerts.length > 0 && (
            <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'rgba(245, 158, 11, 0.05)' }}>
              <div className="panel-title" style={{ color: 'var(--warning)', marginBottom: '12px' }}>
                <AlertCircle size={20} />
                <span>Active Operational Alerts ({snapshot.activeAlerts.length})</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {snapshot.activeAlerts.map((alert, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-sm)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span className={`status-pill ${alert.severity === 'CRITICAL' ? 'danger' : 'warning'}`}>
                        {alert.severity}
                      </span>
                      <span style={{ fontSize: '0.85rem', color: '#fff' }}>{alert.message}</span>
                    </div>
                    <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      {new Date(alert.detectedAt).toLocaleTimeString()}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Active Rides In-Progress */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <Car size={20} color="var(--info)" />
                <span>Active Rides En-Route ({snapshot.activeRides.length})</span>
              </div>
            </div>

            {snapshot.activeRides.length === 0 ? (
              <div className="empty-box">
                <Car size={32} />
                <p>No active trips currently in transit.</p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Booking ID</th>
                      <th>Rider ID</th>
                      <th>Driver ID</th>
                      <th>Pickup & Destination</th>
                      <th>Status</th>
                      <th>Est. Fare</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {snapshot.activeRides.map((ride) => (
                      <tr key={ride.bookingUuid}>
                        <td className="code-cell">{ride.bookingUuid.substring(0, 8)}...</td>
                        <td className="code-cell">{ride.riderUuid ? ride.riderUuid.substring(0, 8) + '...' : 'N/A'}</td>
                        <td className="code-cell">{ride.driverUuid ? ride.driverUuid.substring(0, 8) + '...' : 'Unassigned'}</td>
                        <td>
                          <div style={{ fontSize: '0.82rem', color: '#fff' }}>{ride.pickupAddress || 'Starting location'}</div>
                          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>➔ {ride.dropoffAddress || 'Destination'}</div>
                        </td>
                        <td>
                          <span className="status-pill info">{ride.status}</span>
                        </td>
                        <td className="strong">${ride.estimatedFare.toFixed(2)}</td>
                        <td>
                          <button
                            className="btn-danger"
                            style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                            onClick={() => handleOpenCancelModal(ride)}
                          >
                            Emergency Cancel
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Pending Match Requests */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <Clock size={20} color="var(--warning)" />
                <span>Pending Ride Requests ({snapshot.pendingRequests.length})</span>
              </div>
            </div>

            {snapshot.pendingRequests.length === 0 ? (
              <div className="empty-box">
                <Clock size={32} />
                <p>Matching queue is clear. No pending dispatch requests.</p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Request ID</th>
                      <th>Ride Type</th>
                      <th>Pickup</th>
                      <th>Dropoff</th>
                      <th>Wait Time</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {snapshot.pendingRequests.map((req) => (
                      <tr key={req.requestId}>
                        <td className="code-cell">{req.requestId.substring(0, 8)}...</td>
                        <td><span className="status-pill neutral">{req.rideType}</span></td>
                        <td>{req.pickupAddress}</td>
                        <td>{req.dropoffAddress}</td>
                        <td className="strong">{req.waitingDurationSeconds}s</td>
                        <td><span className="status-pill warning">{req.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {/* Emergency Cancellation Modal */}
      <Modal
        isOpen={cancelModalOpen}
        title="Admin Emergency Trip Cancellation"
        onClose={() => setCancelModalOpen(false)}
      >
        <form onSubmit={handleExecuteEmergencyCancel}>
          <div style={{ marginBottom: '16px' }}>
            <p style={{ color: 'var(--danger)', fontSize: '0.85rem', marginBottom: '12px' }}>
              <strong>Caution:</strong> This administrative override terminates the booking immediately in Booking Service, notifies both parties, and records an immutable entry in the Operations Audit Log.
            </p>
            <div style={{ background: 'rgba(0,0,0,0.3)', padding: '10px', borderRadius: 'var(--radius-sm)', marginBottom: '12px', fontSize: '0.8rem' }}>
              <div><strong>Booking:</strong> {selectedRide?.bookingUuid}</div>
              <div><strong>Status:</strong> {selectedRide?.status}</div>
            </div>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
              Mandatory Cancellation Reason:
            </label>
            <textarea
              required
              rows={3}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="e.g. Passenger medical emergency, vehicle road accident, severe weather alert"
              style={{
                width: '100%',
                background: 'var(--bg-input)',
                border: '1px solid var(--border-medium)',
                borderRadius: 'var(--radius-md)',
                color: '#fff',
                padding: '10px',
                fontSize: '0.85rem',
                outline: 'none'
              }}
            />
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setCancelModalOpen(false)}
              disabled={submittingCancel}
            >
              Close
            </button>
            <button
              type="submit"
              className="btn-danger"
              disabled={submittingCancel || !cancelReason.trim()}
            >
              {submittingCancel ? 'Cancelling...' : 'Confirm Emergency Cancel'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
