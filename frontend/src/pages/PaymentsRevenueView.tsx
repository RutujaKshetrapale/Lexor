import React, { useState, useEffect, useCallback } from 'react';
import {
  CreditCard,
  DollarSign,
  RotateCcw,
  Percent,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { PaymentRecord, PageResponse } from '../types';
import { api } from '../services/api';
import { KpiCard } from '../components/KpiCard';

export const PaymentsRevenueView: React.FC = () => {
  const [payments, setPayments] = useState<PageResponse<PaymentRecord> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [methodFilter, setMethodFilter] = useState<string>('');
  const [page, setPage] = useState<number>(0);

  const fetchPayments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getPayments(page, 10);
      setPayments(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query payment ledger');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchPayments();
  }, [fetchPayments]);

  const items = payments?.content || [];
  const completedItems = items.filter((p) => p.status === 'COMPLETED');
  const refundedItems = items.filter((p) => p.status === 'REFUNDED');

  const totalCollected = completedItems.reduce((acc, curr) => acc + (curr.amount || 0), 0);
  const totalRefunded = refundedItems.reduce((acc, curr) => acc + (curr.amount || 0), 0);
  const successRate = items.length > 0 ? (completedItems.length / items.length) * 100 : 100;
  const estimatedCommission = totalCollected * 0.20; // 20% platform commission standard

  const filteredItems = items.filter((p) => {
    const matchesSearch =
      !searchQuery.trim() ||
      p.paymentUuid?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.transactionId?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.bookingUuid?.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesMethod = !methodFilter || p.paymentMethod === methodFilter;
    return matchesSearch && matchesMethod;
  });

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Payments & Financial Reconciliation</h2>
          <p>Authoritative payment records, payment gateway receipts, refunds, and commission accounting.</p>
        </div>

        <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <div className="search-input-box">
            <Search size={16} className="search-icon" />
            <input
              type="text"
              placeholder="Search by Payment UUID or Txn ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Filter size={16} color="var(--text-muted)" />
            <select
              className="select-filter"
              value={methodFilter}
              onChange={(e) => setMethodFilter(e.target.value)}
            >
              <option value="">All Payment Methods</option>
              <option value="CARD">Credit / Debit Card</option>
              <option value="UPI">UPI / Instant Pay</option>
              <option value="WALLET">Digital Wallet</option>
              <option value="CASH">Cash Collection</option>
            </select>
          </div>
        </div>
      </div>

      {/* Financial KPI Cards */}
      <div className="kpi-grid">
        <KpiCard
          title="Successful Collections"
          value={totalCollected}
          isCurrency={true}
          icon={CreditCard}
          theme="emerald"
          subtitle="Cleared through gateway"
        />
        <KpiCard
          title="Platform Commission"
          value={estimatedCommission}
          isCurrency={true}
          icon={DollarSign}
          theme="indigo"
          subtitle="20% standard cut retained"
        />
        <KpiCard
          title="Disbursed Refunds"
          value={totalRefunded}
          isCurrency={true}
          icon={RotateCcw}
          theme="rose"
          subtitle="Customer refunds"
        />
        <KpiCard
          title="Payment Success Rate"
          value={`${successRate.toFixed(1)}%`}
          icon={Percent}
          theme="cyan"
          subtitle="First-attempt authorization"
        />
      </div>

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)', marginBottom: '16px' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      {/* Transaction Table */}
      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">Authoritative Payment Transactions</div>
        </div>

        {loading && !payments ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Auditing transaction ledgers...</p>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="empty-box">
            <CreditCard size={36} />
            <p>No transactions found matching criteria.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Payment UUID</th>
                  <th>Booking Reference</th>
                  <th>Amount</th>
                  <th>Method</th>
                  <th>Status</th>
                  <th>Gateway Txn ID</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((p) => (
                  <tr key={p.paymentUuid}>
                    <td className="code-cell">{p.paymentUuid.substring(0, 8)}...</td>
                    <td className="code-cell">{p.bookingUuid ? p.bookingUuid.substring(0, 8) + '...' : 'N/A'}</td>
                    <td className="strong">${(p.amount || 0).toFixed(2)}</td>
                    <td>
                      <span className="status-pill neutral">{p.paymentMethod || 'CARD'}</span>
                    </td>
                    <td>
                      <span
                        className={`status-pill ${
                          p.status === 'COMPLETED'
                            ? 'success'
                            : p.status === 'PENDING'
                            ? 'warning'
                            : 'danger'
                        }`}
                      >
                        {p.status}
                      </span>
                    </td>
                    <td className="code-cell">{p.transactionId || 'TXN-LOCAL'}</td>
                    <td>{p.createdAt ? new Date(p.createdAt).toLocaleString() : 'N/A'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="pagination-bar">
          <div>
            Showing Page {page + 1} of {payments?.totalPages || 1} ({payments?.totalElements || filteredItems.length} transactions)
          </div>
          <div className="pagination-controls">
            <button className="page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              <ChevronLeft size={14} />
            </button>
            <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={payments?.isLast ?? true}>
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
