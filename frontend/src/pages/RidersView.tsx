import React, { useState, useEffect, useCallback } from 'react';
import { Search, Users, ShieldAlert, CheckCircle, ChevronLeft, ChevronRight } from 'lucide-react';
import { RiderProfile, PageResponse } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const RidersView: React.FC = () => {
  const [riders, setRiders] = useState<PageResponse<RiderProfile> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  // Administrative action modal
  const [selectedRider, setSelectedRider] = useState<RiderProfile | null>(null);
  const [actionReason, setActionReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchRiders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getRiders(page, 10);
      setRiders(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query rider profiles');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchRiders();
  }, [fetchRiders]);

  const handleToggleStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRider || !actionReason.trim()) return;

    setIsSubmitting(true);
    try {
      const newStatus = selectedRider.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
      const actionName = newStatus === 'SUSPENDED' ? 'SUSPEND_RIDER' : 'RESTORE_RIDER';

      await api.recordAuditLog(
        actionName,
        'RIDER',
        selectedRider.userUuid,
        actionReason.trim()
      );

      setActionMessage(`Rider ${selectedRider.fullName} updated to ${newStatus}. Logged in Audit Trail.`);
      setSelectedRider(null);
      setActionReason('');
      fetchRiders();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Action failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredContent = (riders?.content || []).filter((r) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      r.fullName?.toLowerCase().includes(q) ||
      r.email?.toLowerCase().includes(q) ||
      r.userUuid?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Rider Accounts & Support Administration</h2>
          <p>Inspect rider accounts, travel history, safety flags, and account lifecycle status.</p>
        </div>

        <div className="search-input-box">
          <Search size={16} className="search-icon" />
          <input
            type="text"
            placeholder="Search by name, email, or user UUID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
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
        {loading && !riders ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Loading registered rider profiles...</p>
          </div>
        ) : filteredContent.length === 0 ? (
          <div className="empty-box">
            <Users size={36} />
            <p>No rider accounts found.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>User UUID</th>
                  <th>Full Name</th>
                  <th>Email & Phone</th>
                  <th>Status</th>
                  <th>Trips Taken</th>
                  <th>Joined Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredContent.map((r) => (
                  <tr key={r.userUuid}>
                    <td className="code-cell">{r.userUuid.substring(0, 8)}...</td>
                    <td className="strong">{r.fullName || 'Anonymous Rider'}</td>
                    <td>
                      <div>{r.email}</div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{r.phone || 'No phone'}</div>
                    </td>
                    <td>
                      <span className={`status-pill ${r.status === 'ACTIVE' ? 'success' : 'danger'}`}>
                        {r.status || 'ACTIVE'}
                      </span>
                    </td>
                    <td className="strong">{r.totalRides || 0}</td>
                    <td>{r.joinedAt ? new Date(r.joinedAt).toLocaleDateString() : 'N/A'}</td>
                    <td>
                      <button
                        className={r.status === 'SUSPENDED' ? 'btn-primary' : 'btn-danger'}
                        style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                        onClick={() => setSelectedRider(r)}
                      >
                        {r.status === 'SUSPENDED' ? (
                          <>
                            <CheckCircle size={12} style={{ marginRight: '4px' }} />
                            Restore
                          </>
                        ) : (
                          <>
                            <ShieldAlert size={12} style={{ marginRight: '4px' }} />
                            Suspend
                          </>
                        )}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination-bar">
          <div>
            Showing Page {page + 1} of {riders?.totalPages || 1} ({riders?.totalElements || filteredContent.length} riders)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={riders?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Action Modal */}
      <Modal
        isOpen={!!selectedRider}
        title={selectedRider?.status === 'ACTIVE' ? 'Suspend Rider Account' : 'Restore Rider Account'}
        onClose={() => setSelectedRider(null)}
      >
        <form onSubmit={handleToggleStatus}>
          <div style={{ marginBottom: '16px' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '12px' }}>
              Are you sure you want to change the status of <strong>{selectedRider?.fullName}</strong> ({selectedRider?.email})?
            </p>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
              Administrative Action Reason:
            </label>
            <textarea
              required
              rows={3}
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="e.g. Terms of service violation, payment fraud investigation, or identity verification resolved"
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
            <button type="button" className="btn-secondary" onClick={() => setSelectedRider(null)} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className={selectedRider?.status === 'ACTIVE' ? 'btn-danger' : 'btn-primary'} disabled={isSubmitting || !actionReason.trim()}>
              {isSubmitting ? 'Recording...' : 'Confirm Action'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
