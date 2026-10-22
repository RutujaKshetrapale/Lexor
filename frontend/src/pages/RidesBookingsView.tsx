import React, { useState, useEffect, useCallback } from 'react';
import { Search, Eye, Filter, Car, ChevronLeft, ChevronRight } from 'lucide-react';
import { RideBooking, PageResponse } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const RidesBookingsView: React.FC = () => {
  const [bookings, setBookings] = useState<PageResponse<RideBooking> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [page, setPage] = useState<number>(0);
  const [selectedBooking, setSelectedBooking] = useState<RideBooking | null>(null);

  const fetchBookings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getBookings(page, 10, statusFilter || undefined);
      setBookings(data);
    } catch (err: unknown) {
      // In case booking service has mock items or offline fallback
      setError(err instanceof Error ? err.message : 'Failed to query bookings');
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    fetchBookings();
  }, [fetchBookings]);

  const filteredContent = (bookings?.content || []).filter((b) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      b.bookingUuid?.toLowerCase().includes(q) ||
      b.riderUuid?.toLowerCase().includes(q) ||
      b.driverUuid?.toLowerCase().includes(q) ||
      b.pickupAddress?.toLowerCase().includes(q) ||
      b.dropoffAddress?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Ride & Booking Management</h2>
          <p>Search, filter, and audit lifecycle states, fare breakdowns, and dispatch records.</p>
        </div>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <div className="search-input-box">
            <Search size={16} className="search-icon" />
            <input
              type="text"
              placeholder="Search by UUID or address..."
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
              <option value="">All Statuses</option>
              <option value="COMPLETED">Completed</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="ACCEPTED">Accepted</option>
              <option value="CANCELLED_BY_RIDER">Cancelled (Rider)</option>
              <option value="CANCELLED_BY_DRIVER">Cancelled (Driver)</option>
              <option value="CANCELLED_BY_ADMIN">Cancelled (Admin)</option>
            </select>
          </div>
        </div>
      </div>

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>
            <strong>Note:</strong> {error}. Showing operational booking telemetry or recorded events.
          </p>
        </div>
      )}

      <div className="glass-panel">
        {loading && !bookings ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Loading authoritative ride records...</p>
          </div>
        ) : filteredContent.length === 0 ? (
          <div className="empty-box">
            <Car size={36} />
            <p>No ride bookings found matching the selected criteria.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Booking ID</th>
                  <th>Rider</th>
                  <th>Driver</th>
                  <th>Route (Pickup ➔ Dropoff)</th>
                  <th>Status</th>
                  <th>Fare</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredContent.map((b) => (
                  <tr key={b.bookingUuid}>
                    <td className="code-cell">{b.bookingUuid.substring(0, 8)}...</td>
                    <td className="code-cell">{b.riderUuid ? b.riderUuid.substring(0, 8) + '...' : 'N/A'}</td>
                    <td className="code-cell">{b.driverUuid ? b.driverUuid.substring(0, 8) + '...' : 'Unassigned'}</td>
                    <td>
                      <div style={{ fontSize: '0.82rem', color: '#fff' }}>{b.pickupAddress || 'Pickup'}</div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>➔ {b.dropoffAddress || 'Dropoff'}</div>
                    </td>
                    <td>
                      <span
                        className={`status-pill ${
                          b.status === 'COMPLETED'
                            ? 'success'
                            : b.status === 'IN_PROGRESS' || b.status === 'CONFIRMED'
                            ? 'info'
                            : b.status.startsWith('CANCELLED')
                            ? 'danger'
                            : 'neutral'
                        }`}
                      >
                        {b.status}
                      </span>
                    </td>
                    <td className="strong">${(b.fareAmount || 0).toFixed(2)}</td>
                    <td style={{ fontSize: '0.75rem' }}>{b.createdAt ? new Date(b.createdAt).toLocaleDateString() : 'N/A'}</td>
                    <td>
                      <button
                        className="btn-secondary"
                        style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                        onClick={() => setSelectedBooking(b)}
                      >
                        <Eye size={12} style={{ marginRight: '4px' }} />
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        <div className="pagination-bar">
          <div>
            Showing Page {page + 1} of {bookings?.totalPages || 1} ({bookings?.totalElements || filteredContent.length} total records)
          </div>
          <div className="pagination-controls">
            <button
              className="page-btn"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              <ChevronLeft size={14} />
            </button>
            <button
              className="page-btn"
              onClick={() => setPage((p) => p + 1)}
              disabled={bookings?.isLast ?? true}
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>

      {/* Booking Details Modal */}
      <Modal
        isOpen={!!selectedBooking}
        title="Ride & Booking Telemetry Breakdown"
        onClose={() => setSelectedBooking(null)}
      >
        {selectedBooking && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '0.85rem' }}>
            <div style={{ background: 'rgba(0,0,0,0.3)', padding: '12px', borderRadius: 'var(--radius-sm)' }}>
              <div><strong>Booking UUID:</strong> <span className="code-cell">{selectedBooking.bookingUuid}</span></div>
              <div><strong>Ride UUID:</strong> <span className="code-cell">{selectedBooking.rideUuid || 'N/A'}</span></div>
              <div><strong>Status:</strong> <span className="status-pill neutral">{selectedBooking.status}</span></div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Rider ID:</span>
                <div className="code-cell">{selectedBooking.riderUuid}</div>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Driver ID:</span>
                <div className="code-cell">{selectedBooking.driverUuid || 'Unassigned'}</div>
              </div>
            </div>

            <div>
              <span style={{ color: 'var(--text-muted)' }}>Pickup Location:</span>
              <div style={{ color: '#fff' }}>{selectedBooking.pickupAddress}</div>
            </div>

            <div>
              <span style={{ color: 'var(--text-muted)' }}>Dropoff Destination:</span>
              <div style={{ color: '#fff' }}>{selectedBooking.dropoffAddress}</div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Calculated Fare:</span>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--success)' }}>
                  ${(selectedBooking.fareAmount || 0).toFixed(2)}
                </div>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Payment Status:</span>
                <div>{selectedBooking.paymentStatus || 'PENDING'}</div>
              </div>
            </div>

            {selectedBooking.cancellationReason && (
              <div style={{ background: 'var(--danger-bg)', border: '1px solid var(--danger-border)', padding: '10px', borderRadius: 'var(--radius-sm)' }}>
                <span style={{ color: 'var(--danger)', fontWeight: 600 }}>Cancellation Reason:</span>
                <div style={{ color: '#fff', marginTop: '4px' }}>{selectedBooking.cancellationReason}</div>
              </div>
            )}

            <div className="modal-footer" style={{ marginTop: '12px' }}>
              <button className="btn-secondary" onClick={() => setSelectedBooking(null)}>
                Close
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
