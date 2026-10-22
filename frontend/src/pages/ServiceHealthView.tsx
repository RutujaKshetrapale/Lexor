import React, { useState, useEffect, useCallback } from 'react';
import {
  Activity,
  CheckCircle,
  AlertTriangle,
  XCircle,
  RefreshCw,
  Server,
  Database
} from 'lucide-react';
import { ServiceHealthStatus } from '../types';
import { api } from '../services/api';
import { KpiCard } from '../components/KpiCard';

export const ServiceHealthView: React.FC = () => {
  const [health, setHealth] = useState<ServiceHealthStatus | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchHealth = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getServiceHealth();
      setHealth(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query service health monitor');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 15000); // 15-second health poll
    return () => clearInterval(interval);
  }, [fetchHealth]);

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Platform Health & Dependency Readiness</h2>
          <p>Real-time HTTP and socket telemetry probing all 15 microservices, databases, and message brokers.</p>
        </div>

        <button className="btn-secondary" onClick={fetchHealth} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'spinner' : ''} />
          <span>Probe Now</span>
        </button>
      </div>

      {health && (
        <div className="kpi-grid">
          <KpiCard
            title="Monitored Components"
            value={health.totalServices}
            icon={Server}
            theme="indigo"
            subtitle="Microservices & Data Stores"
          />
          <KpiCard
            title="Healthy & Online"
            value={health.healthyServices}
            icon={CheckCircle}
            theme="emerald"
            subtitle="Responding < 1000ms"
          />
          <KpiCard
            title="Degraded Probes"
            value={health.degradedServices}
            icon={AlertTriangle}
            theme="amber"
            subtitle="Non-200 or slow latency"
          />
          <KpiCard
            title="Offline / Unreachable"
            value={health.downServices}
            icon={XCircle}
            theme="rose"
            subtitle="Process unreachable"
          />
        </div>
      )}

      {error && (
        <div className="glass-panel" style={{ borderColor: 'var(--danger-border)', background: 'var(--danger-bg)' }}>
          <p style={{ color: 'var(--danger)', fontSize: '0.85rem' }}>{error}</p>
        </div>
      )}

      {/* Service Telemetry Table */}
      <div className="glass-panel">
        <div className="panel-header">
          <div className="panel-title">
            <Activity size={20} color="var(--primary)" />
            <span>Service Diagnostic Matrix</span>
          </div>
          {health && (
            <span
              className={`status-pill ${
                health.overallSystemStatus === 'UP'
                  ? 'success'
                  : health.overallSystemStatus === 'DEGRADED'
                  ? 'warning'
                  : 'danger'
              }`}
            >
              Overall: {health.overallSystemStatus}
            </span>
          )}
        </div>

        {loading && !health ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Probing platform microservices...</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Component / Microservice</th>
                  <th>Port</th>
                  <th>Endpoint Probed</th>
                  <th>Status</th>
                  <th>Latency</th>
                  <th>Readiness</th>
                  <th>Diagnostic Details</th>
                </tr>
              </thead>
              <tbody>
                {health?.services.map((svc) => (
                  <tr key={svc.serviceName}>
                    <td className="strong">
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        {svc.serviceName.includes('MySQL') || svc.serviceName.includes('Redis') ? (
                          <Database size={16} color="var(--primary-light)" />
                        ) : (
                          <Server size={16} color="var(--info)" />
                        )}
                        <span>{svc.serviceName}</span>
                      </div>
                    </td>
                    <td className="code-cell">{svc.port}</td>
                    <td className="code-cell" style={{ fontSize: '0.72rem' }}>{svc.endpointUrl}</td>
                    <td>
                      <span
                        className={`status-pill ${
                          svc.status === 'UP' ? 'success' : svc.status === 'DEGRADED' ? 'warning' : 'danger'
                        }`}
                      >
                        {svc.status}
                      </span>
                    </td>
                    <td className="code-cell">{svc.latencyMs}ms</td>
                    <td>
                      <span className={`status-pill ${svc.readiness === 'READY' ? 'success' : 'neutral'}`}>
                        {svc.readiness}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.72rem', color: svc.errorDetails ? 'var(--danger)' : 'var(--text-muted)' }}>
                      {svc.errorDetails || 'Healthy heartbeat'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
