import React, { useState, useEffect, useCallback } from 'react';
import { Search, Car, Check, X, ChevronLeft, ChevronRight } from 'lucide-react';
import { VehicleProfile, PageResponse } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const VehiclesView: React.FC = () => {
  const [vehicles, setVehicles] = useState<PageResponse<VehicleProfile> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  // Administrative action modal
  const [selectedVehicle, setSelectedVehicle] = useState<VehicleProfile | null>(null);
  const [targetAction, setTargetAction] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [actionReason, setActionReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchVehicles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getVehicles(page, 10);
      setVehicles(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query registered fleet vehicles');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchVehicles();
  }, [fetchVehicles]);

  const handleOpenActionModal = (v: VehicleProfile, action: 'APPROVE' | 'REJECT') => {
    setSelectedVehicle(v);
    setTargetAction(action);
    setActionReason('');
    setActionMessage(null);
  };

  const handleExecuteVehicleAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVehicle || !actionReason.trim()) return;

    setIsSubmitting(true);
    try {
      const actionName = targetAction === 'APPROVE' ? 'APPROVE_VEHICLE' : 'REJECT_VEHICLE';

      await api.recordAuditLog(
        actionName,
        'VEHICLE',
        selectedVehicle.vehicleUuid,
        actionReason.trim()
      );

      setActionMessage(`Vehicle ${selectedVehicle.licensePlate} (${selectedVehicle.make} ${selectedVehicle.model}) updated via ${actionName}.`);
      setSelectedVehicle(null);
      setActionReason('');
      fetchVehicles();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Vehicle compliance update failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredContent = (vehicles?.content || []).filter((v) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      v.make?.toLowerCase().includes(q) ||
      v.model?.toLowerCase().includes(q) ||
      v.licensePlate?.toLowerCase().includes(q) ||
      v.vehicleUuid?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Vehicle Fleet & Document Compliance</h2>
          <p>Inspect vehicle registrations, vehicle types, roadworthiness compliance, and driver assignments.</p>
        </div>

        <div className="search-input-box">
          <Search size={16} className="search-icon" />
          <input
            type="text"
            placeholder="Search by make, model, or license plate..."
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
        {loading && !vehicles ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Loading registered vehicles...</p>
          </div>
        ) : filteredContent.length === 0 ? (
          <div className="empty-box">
            <Car size={36} />
            <p>No registered vehicles found.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Vehicle UUID</th>
                  <th>Make & Model</th>
                  <th>License Plate</th>
                  <th>Year & Color</th>
                  <th>Category</th>
                  <th>Verification</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredContent.map((v) => (
                  <tr key={v.vehicleUuid}>
                    <td className="code-cell">{v.vehicleUuid.substring(0, 8)}...</td>
                    <td className="strong">{v.make} {v.model}</td>
                    <td className="code-cell" style={{ color: '#fff' }}>{v.licensePlate}</td>
                    <td>{v.year} • {v.color}</td>
                    <td><span className="status-pill neutral">{v.vehicleType || 'SEDAN'}</span></td>
                    <td>
                      <span
                        className={`status-pill ${
                          v.verificationStatus === 'VERIFIED'
                            ? 'success'
                            : v.verificationStatus === 'PENDING'
                            ? 'warning'
                            : 'danger'
                        }`}
                      >
                        {v.verificationStatus}
                      </span>
                    </td>
                    <td>
                      <span className={`status-pill ${v.isActive ? 'success' : 'neutral'}`}>
                        {v.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        {v.verificationStatus !== 'VERIFIED' && (
                          <button
                            className="btn-primary"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenActionModal(v, 'APPROVE')}
                          >
                            <Check size={12} style={{ marginRight: '3px' }} />
                            Approve
                          </button>
                        )}
                        {v.verificationStatus === 'PENDING' && (
                          <button
                            className="btn-danger"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenActionModal(v, 'REJECT')}
                          >
                            <X size={12} style={{ marginRight: '3px' }} />
                            Reject
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
            Showing Page {page + 1} of {vehicles?.totalPages || 1} ({vehicles?.totalElements || filteredContent.length} vehicles)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={vehicles?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Vehicle Action Modal */}
      <Modal
        isOpen={!!selectedVehicle}
        title={`Vehicle Document Action: ${targetAction}`}
        onClose={() => setSelectedVehicle(null)}
      >
        <form onSubmit={handleExecuteVehicleAction}>
          <div style={{ marginBottom: '16px' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '12px' }}>
              Confirm <strong>{targetAction}</strong> for vehicle <strong>{selectedVehicle?.make} {selectedVehicle?.model}</strong> ({selectedVehicle?.licensePlate}).
            </p>
            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
              Mandatory Compliance Reason:
            </label>
            <textarea
              required
              rows={3}
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder="e.g. Valid vehicle registration certificate and commercial insurance inspected"
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
            <button type="button" className="btn-secondary" onClick={() => setSelectedVehicle(null)} disabled={isSubmitting}>
              Cancel
            </button>
            <button
              type="submit"
              className={targetAction === 'APPROVE' ? 'btn-primary' : 'btn-danger'}
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
