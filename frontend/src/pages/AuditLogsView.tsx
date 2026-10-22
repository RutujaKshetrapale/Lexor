import React, { useState, useEffect, useCallback } from 'react';
import {
  FileText,
  Filter,
  ShieldCheck,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { AdminAuditLog, PageResponse } from '../types';
import { api } from '../services/api';

export const AuditLogsView: React.FC = () => {
  const [logs, setLogs] = useState<PageResponse<AdminAuditLog> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionFilter, setActionFilter] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getAuditLogs(page, 15, actionFilter || undefined);
      setLogs(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query operational audit logs');
    } finally {
      setLoading(false);
    }
  }, [page, actionFilter]);

  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Operations Audit Trail</h2>
          <p>Immutable record of all privileged administrative overrides, driver KYC actions, cancellations, and moderations.</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Filter size={16} color="var(--text-muted)" />
          <select
            className="select-filter"
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All Administrative Actions</option>
            <option value="EMERGENCY_RIDE_CANCEL">Emergency Ride Cancel</option>
            <option value="SUSPEND_DRIVER">Suspend Driver</option>
            <option value="VERIFY_DRIVER">Verify Driver KYC</option>
            <option value="REJECT_DRIVER">Reject Driver</option>
            <option value="APPROVE_VEHICLE">Approve Vehicle</option>
            <option value="REJECT_VEHICLE">Reject Vehicle</option>
            <option value="SUSPEND_RIDER">Suspend Rider</option>
            <option value="RESTORE_RIDER">Restore Rider</option>
            <option value="MODERATE_REVIEW_VISIBLE">Approve Review</option>
            <option value="MODERATE_REVIEW_HIDDEN">Hide Review</option>
            <option value="MODERATE_REVIEW_REMOVED">Remove Review</option>
            <option value="UPDATE_SETTING">Update System Setting</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">
            <ShieldCheck size={20} color="var(--primary)" />
            <span>Audit Event Log</span>
          </div>
        </div>

        {loading && !logs ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Retrieving immutable audit records...</p>
          </div>
        ) : (logs?.content || []).length === 0 ? (
          <div className="empty-box">
            <FileText size={36} />
            <p>No audit events found matching the specified filter.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp (UTC)</th>
                  <th>Action</th>
                  <th>Resource Target</th>
                  <th>Admin Actor</th>
                  <th>Mandatory Justification / Reason</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {logs?.content.map((log) => (
                  <tr key={log.auditUuid}>
                    <td style={{ fontSize: '0.75rem', whiteSpace: 'nowrap' }}>
                      {log.createdAt ? new Date(log.createdAt).toLocaleString() : 'N/A'}
                    </td>
                    <td>
                      <span
                        className={`status-pill ${
                          log.action.includes('CANCEL') || log.action.includes('REJECT') || log.action.includes('SUSPEND')
                            ? 'danger'
                            : log.action.includes('VERIFY') || log.action.includes('APPROVE')
                            ? 'success'
                            : 'info'
                        }`}
                      >
                        {log.action}
                      </span>
                    </td>
                    <td>
                      <div className="code-cell" style={{ fontSize: '0.75rem' }}>
                        {log.resourceType}: {log.resourceUuid.substring(0, 10)}...
                      </div>
                    </td>
                    <td className="code-cell" style={{ color: '#fff' }}>
                      {log.adminUserUuid ? log.adminUserUuid.substring(0, 8) + '...' : 'admin-system'}
                    </td>
                    <td style={{ maxWidth: '340px' }}>
                      <div style={{ fontSize: '0.82rem', color: '#fff' }}>{log.reason}</div>
                    </td>
                    <td>
                      <span className={`status-pill ${log.status === 'SUCCESS' ? 'success' : 'danger'}`}>
                        {log.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination-bar">
          <div>
            Showing Page {page + 1} of {logs?.totalPages || 1} ({logs?.totalElements || 0} total events)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={logs?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
