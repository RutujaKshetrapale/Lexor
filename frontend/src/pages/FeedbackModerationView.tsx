import React, { useState, useEffect, useCallback } from 'react';
import {
  MessageSquare,
  Star,
  ShieldCheck,
  EyeOff,
  Trash2,
  Filter,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { FeedbackReview, PageResponse } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const FeedbackModerationView: React.FC = () => {
  const [reviews, setReviews] = useState<PageResponse<FeedbackReview> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  // Moderation action modal
  const [selectedReview, setSelectedReview] = useState<FeedbackReview | null>(null);
  const [targetStatus, setTargetStatus] = useState<'VISIBLE' | 'HIDDEN' | 'REMOVED'>('VISIBLE');
  const [modReason, setModReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const fetchFeedback = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getFeedbackReviews(page, 10);
      setReviews(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query reviews from Feedback Service');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchFeedback();
  }, [fetchFeedback]);

  const handleOpenModModal = (review: FeedbackReview, status: 'VISIBLE' | 'HIDDEN' | 'REMOVED') => {
    setSelectedReview(review);
    setTargetStatus(status);
    setModReason('');
    setActionSuccess(null);
  };

  const handleExecuteModeration = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedReview || !modReason.trim()) return;

    setIsSubmitting(true);
    try {
      await api.moderateFeedback(selectedReview.reviewUuid, targetStatus, modReason.trim());
      await api.recordAuditLog(
        `MODERATE_REVIEW_${targetStatus}`,
        'REVIEW',
        selectedReview.reviewUuid,
        modReason.trim()
      );

      setActionSuccess(`Review ${selectedReview.reviewUuid.substring(0, 8)}... updated to ${targetStatus}.`);
      setSelectedReview(null);
      setModReason('');
      fetchFeedback();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Moderation action failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredReviews = (reviews?.content || []).filter((r) => {
    if (!statusFilter) return true;
    return r.moderationStatus === statusFilter;
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Ratings & Feedback Moderation (Phase 13 Integration)</h2>
          <p>Review customer ratings, filter reported content, and moderate reviews preserving safety standards.</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Filter size={16} color="var(--text-muted)" />
          <select
            className="select-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">All Moderation States</option>
            <option value="UNDER_REVIEW">Under Review (Reported)</option>
            <option value="VISIBLE">Visible (Approved)</option>
            <option value="HIDDEN">Hidden</option>
            <option value="REMOVED">Removed</option>
          </select>
        </div>
      </div>

      {actionSuccess && (
        <div className="glass-panel" style={{ borderColor: 'var(--success-border)', background: 'var(--success-bg)', marginBottom: '16px' }}>
          <div style={{ color: 'var(--success)', fontWeight: 600 }}>{actionSuccess}</div>
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)', marginBottom: '16px' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      <div className="glass-panel">
        {loading && !reviews ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Querying Feedback Service...</p>
          </div>
        ) : filteredReviews.length === 0 ? (
          <div className="empty-box">
            <MessageSquare size={36} />
            <p>No reviews found matching the selected moderation state.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Review UUID</th>
                  <th>Target Role</th>
                  <th>Rating</th>
                  <th>Comment Feedback</th>
                  <th>Report Reason & Flags</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredReviews.map((r) => (
                  <tr key={r.reviewUuid}>
                    <td className="code-cell">{r.reviewUuid.substring(0, 8)}...</td>
                    <td>
                      <span className="status-pill neutral">{r.targetRole || 'DRIVER'}</span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--warning)' }}>
                        <Star size={14} fill="var(--warning)" />
                        <span style={{ fontWeight: 700 }}>{r.rating}</span>
                      </div>
                    </td>
                    <td style={{ maxWidth: '320px' }}>
                      <div style={{ fontSize: '0.84rem', color: '#fff', fontStyle: r.comment ? 'normal' : 'italic' }}>
                        {r.comment || 'No text review supplied'}
                      </div>
                    </td>
                    <td>
                      {r.reportCount > 0 ? (
                        <div>
                          <span className="status-pill danger" style={{ fontSize: '0.7rem' }}>
                            {r.reportCount} reports
                          </span>
                          <div style={{ fontSize: '0.72rem', color: 'var(--danger)', marginTop: '4px' }}>
                            {r.reportReason || 'User flagged violation'}
                          </div>
                        </div>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>None</span>
                      )}
                    </td>
                    <td>
                      <span
                        className={`status-pill ${
                          r.moderationStatus === 'VISIBLE'
                            ? 'success'
                            : r.moderationStatus === 'UNDER_REVIEW'
                            ? 'warning'
                            : 'danger'
                        }`}
                      >
                        {r.moderationStatus}
                      </span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        {r.moderationStatus !== 'VISIBLE' && (
                          <button
                            className="btn-primary"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenModModal(r, 'VISIBLE')}
                          >
                            <ShieldCheck size={12} style={{ marginRight: '3px' }} />
                            Approve
                          </button>
                        )}
                        {r.moderationStatus !== 'HIDDEN' && (
                          <button
                            className="btn-secondary"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenModModal(r, 'HIDDEN')}
                          >
                            <EyeOff size={12} style={{ marginRight: '3px' }} />
                            Hide
                          </button>
                        )}
                        {r.moderationStatus !== 'REMOVED' && (
                          <button
                            className="btn-danger"
                            style={{ padding: '4px 8px', fontSize: '0.72rem' }}
                            onClick={() => handleOpenModModal(r, 'REMOVED')}
                          >
                            <Trash2 size={12} style={{ marginRight: '3px' }} />
                            Remove
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
            Showing Page {page + 1} of {reviews?.totalPages || 1} ({reviews?.totalElements || filteredReviews.length} reviews)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={reviews?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Moderation Action Modal */}
      <Modal
        isOpen={!!selectedReview}
        title={`Review Moderation Override: ${targetStatus}`}
        onClose={() => setSelectedReview(null)}
      >
        <form onSubmit={handleExecuteModeration}>
          <div style={{ marginBottom: '16px' }}>
            <div style={{ background: 'rgba(0,0,0,0.3)', padding: '12px', borderRadius: 'var(--radius-sm)', marginBottom: '12px' }}>
              <div><strong>Rating:</strong> {selectedReview?.rating} ★</div>
              <div style={{ fontStyle: 'italic', marginTop: '4px' }}>"{selectedReview?.comment}"</div>
            </div>

            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '12px' }}>
              Set moderation status to <strong>{targetStatus}</strong>.
            </p>

            <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
              Moderator Audit Justification:
            </label>
            <textarea
              required
              rows={3}
              value={modReason}
              onChange={(e) => setModReason(e.target.value)}
              placeholder="e.g. Content complies with community guidelines / Content contains profanity or abusive language"
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
            <button type="button" className="btn-secondary" onClick={() => setSelectedReview(null)} disabled={isSubmitting}>
              Cancel
            </button>
            <button
              type="submit"
              className={targetStatus === 'VISIBLE' ? 'btn-primary' : 'btn-danger'}
              disabled={isSubmitting || !modReason.trim()}
            >
              {isSubmitting ? 'Submitting...' : `Confirm ${targetStatus}`}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
