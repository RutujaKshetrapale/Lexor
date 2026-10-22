import React, { useState, useEffect, useCallback } from 'react';
import {
  Download,
  Clock,
  Car,
  DollarSign
} from 'lucide-react';
import { PlatformTrends } from '../types';
import { api, getAdminToken } from '../services/api';

export const ReportsAnalyticsView: React.FC = () => {
  const [timeRange, setTimeRange] = useState<string>('7D');
  const [trends, setTrends] = useState<PlatformTrends | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);

  const fetchTrends = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getTrends(timeRange);
      setTrends(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query analytics trends');
    } finally {
      setLoading(false);
    }
  }, [timeRange]);

  useEffect(() => {
    fetchTrends();
  }, [fetchTrends]);

  const handleExportCsv = async (reportType: 'RIDES' | 'PAYMENTS' | 'DRIVERS') => {
    setExporting(reportType);
    try {
      const url = api.getExportCsvUrl(reportType, timeRange);
      const res = await fetch(url, {
        headers: {
          Authorization: `Bearer ${getAdminToken()}`
        }
      });

      if (!res.ok) throw new Error(`Export failed: ${res.statusText}`);

      const blob = await res.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = `lexor-${reportType.toLowerCase()}-${timeRange.toLowerCase()}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(downloadUrl);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'CSV export failed');
    } finally {
      setExporting(null);
    }
  };

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Operational Analytics & Reports</h2>
          <p>Time-series demand curves, ride completion ratios, financial revenue trends, and streaming CSV data exports.</p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div className="time-filter-group">
            {['7D', '30D', '90D'].map((range) => (
              <button
                key={range}
                className={`filter-pill-btn ${timeRange === range ? 'active' : ''}`}
                onClick={() => setTimeRange(range)}
              >
                {range}
              </button>
            ))}
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              className="btn-secondary"
              onClick={() => handleExportCsv('RIDES')}
              disabled={!!exporting}
            >
              <Download size={14} />
              <span>{exporting === 'RIDES' ? 'Exporting...' : 'Export Rides'}</span>
            </button>
            <button
              className="btn-secondary"
              onClick={() => handleExportCsv('PAYMENTS')}
              disabled={!!exporting}
            >
              <Download size={14} />
              <span>{exporting === 'PAYMENTS' ? 'Exporting...' : 'Export Payments'}</span>
            </button>
            <button
              className="btn-secondary"
              onClick={() => handleExportCsv('DRIVERS')}
              disabled={!!exporting}
            >
              <Download size={14} />
              <span>{exporting === 'DRIVERS' ? 'Exporting...' : 'Export Drivers'}</span>
            </button>
          </div>
        </div>
      </div>

      {loading && !trends && (
        <div className="loading-box">
          <div className="spinner" />
          <p>Compiling time-series trend analysis...</p>
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--warning-border)', background: 'var(--warning-bg)' }}>
          <p style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      {trends && (
        <>
          {/* Hourly Demand Profile Visualizer */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <Clock size={20} color="var(--primary)" />
                <span>Hourly Demand Curve (24-Hour Dispatch Volume)</span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-end', gap: '6px', height: '160px', padding: '10px 0' }}>
              {trends.hourlyDemand.map((d) => {
                const maxVol = Math.max(...trends.hourlyDemand.map((h) => h.requestVolume), 1);
                const heightPct = Math.max(12, (d.requestVolume / maxVol) * 100);
                const isPeak = d.hourOfDay >= 8 && d.hourOfDay <= 10 || d.hourOfDay >= 17 && d.hourOfDay <= 20;

                return (
                  <div
                    key={d.hourOfDay}
                    style={{
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      height: '100%',
                      justifyContent: 'flex-end'
                    }}
                  >
                    <div
                      title={`Hour ${d.hourOfDay}:00 — ${d.requestVolume} requests`}
                      style={{
                        width: '100%',
                        height: `${heightPct}%`,
                        background: isPeak ? 'var(--primary-gradient)' : 'rgba(99, 102, 241, 0.3)',
                        borderRadius: '3px 3px 0 0',
                        transition: 'height 0.3s ease'
                      }}
                    />
                    <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '6px' }}>
                      {d.hourOfDay % 3 === 0 ? `${d.hourOfDay}h` : ''}
                    </span>
                  </div>
                );
              })}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '8px' }}>
              <span>12:00 AM (Low night volume)</span>
              <span style={{ color: 'var(--primary-light)' }}>★ Morning Peak (8-10 AM) & Evening Peak (5-8 PM)</span>
              <span>11:59 PM</span>
            </div>
          </div>

          {/* Daily Rides & Completion Ratios */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <Car size={20} color="var(--success)" />
                <span>Daily Ride Volumes & Fulfillment ({trends.timeRange})</span>
              </div>
            </div>

            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Total Ride Requests</th>
                    <th>Completed Fulfillment</th>
                    <th>Cancellations</th>
                    <th>Fulfillment Rate</th>
                  </tr>
                </thead>
                <tbody>
                  {trends.dailyRides.map((row) => {
                    const rate = row.totalRides > 0 ? (row.completedRides / row.totalRides) * 100 : 100;
                    return (
                      <tr key={row.date}>
                        <td className="strong">{row.date}</td>
                        <td>{row.totalRides}</td>
                        <td style={{ color: 'var(--success)' }}>{row.completedRides}</td>
                        <td style={{ color: 'var(--danger)' }}>{row.cancelledRides}</td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <div style={{ flex: 1, height: '6px', background: 'rgba(255,255,255,0.06)', borderRadius: '3px', overflow: 'hidden' }}>
                              <div style={{ width: `${rate}%`, height: '100%', background: 'var(--success)' }} />
                            </div>
                            <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>{rate.toFixed(0)}%</span>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Daily Financial Accruals */}
          <div className="glass-panel">
            <div className="panel-header">
              <div className="panel-title">
                <DollarSign size={20} color="var(--emerald)" />
                <span>Financial Telemetry Trends</span>
              </div>
            </div>

            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Gross Booking Value</th>
                    <th>Driver Earnings (80%)</th>
                    <th>Platform Commission (20%)</th>
                  </tr>
                </thead>
                <tbody>
                  {trends.dailyFinancials.map((fin) => (
                    <tr key={fin.date}>
                      <td className="strong">{fin.date}</td>
                      <td className="strong" style={{ color: 'var(--success)' }}>${fin.grossBookingValue.toFixed(2)}</td>
                      <td>${fin.driverPayouts.toFixed(2)}</td>
                      <td style={{ color: 'var(--primary-light)', fontWeight: 600 }}>${fin.platformCommission.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
