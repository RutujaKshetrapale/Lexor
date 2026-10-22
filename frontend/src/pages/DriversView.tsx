import React, { useState, useEffect, useCallback } from 'react';
import { Search, UserCheck, Filter, Check, X, ShieldAlert, Star, ChevronLeft, ChevronRight } from 'lucide-react';
import { DriverProfile, PageResponse } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const DriversView: React.FC = () => {
  const [drivers, setDrivers] = useState<PageResponse<DriverProfile> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  // Administrative action modal
  const [selectedDriver, setSelectedDriver] = useState<DriverProfile | null>(null);
  const [targetAction, setTargetAction] = useState<'VERIFY' | 'REJECT' | 'SUSPEND'>('VERIFY');
  const [actionReason, setActionReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchDrivers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getDrivers(page, 10, statusFilter || undefined);
      setDrivers(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query driver records');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    fetchDrivers();
  }, [fetchDrivers]);

  const handleOpenActionModal = (driver: DriverProfile, action: 'VERIFY' | 'REJECT' | 'SUSPEND') => {
    setSelectedDriver(driver);
    setTargetAction(action);
    setActionReason('');
    setActionMessage(null);
  };

  const handleExecuteDriverAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDriver || !actionReason.trim()) return;

    setIsSubmitting(true);
    try {
      const actionName =
        targetAction === 'VERIFY'
          ? 'VERIFY_DRIVER'
          : targetAction === 'REJECT'
          ? 'REJECT_DRIVER'
          : 'SUSPEND_DRIVER';

      await api.recordAuditLog(
        actionName,
        'DRIVER',
        selectedDriver.driverUuid,
        actionReason.trim()
      );

      setActionMessage(`Action ${actionName} successfully recorded for driver ${selectedDriver.fullName}.`);
      setSelectedDriver(null);
      setActionReason('');
      fetchDrivers();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Action failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredContent = (drivers?.content || []).filter((d) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      d.fullName?.toLowerCase().includes(q) ||
      d.driverUuid?.toLowerCase().includes(q) ||
      d.licenseNumber?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Driver Fleet & KYC Verification</h2>
          <p>Manage driver onboarding, verify identity documents, and enforce road compliance.</p>
        </div>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <div className="search-input-box">
            <Search size={16} className="search-icon" />
            <input
              type="text"
              placeholder="Search by driver name or license..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Filter size={16} color="var(--text-muted)" />
            <select
              className="select-filter"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All Verification States</option>
              <option value="PENDING">Pending Review</option>
              <option value="VERIFIED">Verified</option>
              <option value="REJECTED">Rejected</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
          </div>
        </div>
      </div>

      {actionMessage && (
        <div className="glass-panel" style={{ borderColor: 'var(--success-border)', background: 'var(--success-bg)', marginBottom: '16px' }}>
          <div style={{ color: 'var(--success)', fontWeight: 600 }}>{actionMessage}</div>
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)', marginBottom: '16px' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      <div className="glass-panel">
        {loading && !drivers ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Loading registered driver fleet...</p>
          </div>
        ) : filteredContent.length === 0 ? (
          <div className="empty-box">
            <UserCheck size={36} />
            <p>No driver profiles found.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Driver UUID</th>
                  <th>Full Name</th>
                  <th>License Number</th>
                  <th>Availability</th>
                  <th>Verification Status</th>
                  <th>Rating</th>
                  <th>Trips</th>
                  <th>Compliance Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredContent.map((d) => (
                  <tr key={d.driverUuid}>
                    <td className="code-cell">{d.driverUuid.substring(0, 8)}...</td>
                    <td className="strong">{d.fullName || 'Registered Driver'}</td>
                    <td className="code-cell">{d.licenseNumber || 'DL-PENDING'}</td>
                    <td>
                      <span className={`status-pill ${d.isAvailable ? 'success' : 'neutral'}`}>
                        {d.isAvailable ? 'Online' : 'Offline'}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`status-pill ${
                          d.verificationStatus === 'VERIFIED'
                            ? 'success'
                            : d.verificationStatus === 'PENDING'
                            ? 'warning'
                            : 'danger'
                        }`}
                      >
                        {d.verificationStatus}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--warning)' }}>
                        <Star size={14} fill="var(--warning)" />
                        <span style={{ fontWeight: 600 }}>{(d.rating || 5.0).toFixed(1)}</span>
                      </div>
                    </td>
                    <td className="strong">{d.totalTrips || 0}</td>
                    <td>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        {d.verificationStatus !== 'VERIFIED' && (
                          <button
                            className="btn-primary"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenActionModal(d, 'VERIFY')}
                          >
                            <Check size={12} style={{ marginRight: '3px' }} />
                            Verify
                          </button>
                        )}
                        {d.verificationStatus === 'PENDING' && (
                          <button
                            className="btn-danger"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenActionModal(d, 'REJECT')}
                          >
                            <X size={12} style={{ marginRight: '3px' }} />
                            Reject
                          </button>
                        )}
                        {d.verificationStatus === 'VERIFIED' && (
                          <button
                            className="btn-secondary"
                            style={{ padding: '4px 8px', fontSize: '0.72rem', borderColor: 'var(--danger-border)', color: 'var(--danger)' }}
                            onClick={() => handleOpenActionModal(d, 'SUSPEND')}
                          >
                            <ShieldAlert size={12} style={{ marginRight: '3px' }} />
                            Suspend
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination-bar">
          <div>
            Showing Page {page + 1} of {drivers?.totalPages || 1} ({drivers?.totalElements || filteredContent.length} drivers)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={drivers?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Driver Compliance Action Modal */}
      <Modal
        isOpen={!!selectedDriver}
        title={`Driver Compliance Action: ${targetAction}`}
        onClose={() => setSelectedDriver(null)}
      >
        <form onSubmit={handleExecuteDriverAction}>
          <div style={{ marginBottom: '16px' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '12px' }}>
              Confirm <strong>{targetAction}</strong> for driver <strong>{selectedDriver?.fullName}</strong> ({selectedDriver?.licenseNumber}).
            </p>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
              Mandatory Compliance Reason:
            </label>
            <textarea
              required
              rows={3}
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="e.g. Valid driver license and commercial background check passed, or expired documents"
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
            <button type="button" className="btn-secondary" onClick={() => setSelectedDriver(null)} disabled={isSubmitting}>
              Cancel
            </button>
            <button
              type="submit"
              className={targetAction === 'VERIFY' ? 'btn-primary' : 'btn-danger'}
              disabled={isSubmitting || !actionReason.trim()}
            >
              {isSubmitting ? 'Recording...' : `Confirm ${targetAction}`}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
