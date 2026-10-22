import React, { useState, useEffect, useCallback } from 'react';
import { Settings, Save, AlertCircle } from 'lucide-react';
import { AdminSetting } from '../types';
import { api } from '../services/api';
import { Modal } from '../components/Modal';

export const SettingsView: React.FC = () => {
  const [settings, setSettings] = useState<AdminSetting[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  // Edit setting modal
  const [selectedSetting, setSelectedSetting] = useState<AdminSetting | null>(null);
  const [newValue, setNewValue] = useState<string>('');
  const [newDesc, setNewDesc] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  const fetchSettings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getSettings();
      setSettings(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to query system dashboard settings');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSettings();
  }, [fetchSettings]);

  const handleOpenEdit = (s: AdminSetting) => {
    setSelectedSetting(s);
    setNewValue(s.settingValue);
    setNewDesc(s.description);
    setActionSuccess(null);
  };

  const handleSaveSetting = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSetting) return;

    setIsSubmitting(true);
    try {
      await api.updateSetting(selectedSetting.settingKey, newValue.trim(), newDesc.trim());
      await api.recordAuditLog(
        'UPDATE_SETTING',
        'SETTING',
        selectedSetting.settingKey,
        `Updated value from ${selectedSetting.settingValue} to ${newValue.trim()}`
      );

      setActionSuccess(`Setting ${selectedSetting.settingKey} successfully updated to "${newValue.trim()}".`);
      setSelectedSetting(null);
      fetchSettings();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Failed to update setting');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page-container">
      <div className="view-header">
        <div>
          <h2>Operational Platform Settings</h2>
          <p>Fine-tune live polling intervals, alert surge thresholds, and platform monitoring policies.</p>
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
        <div className="panel-header">
          <div className="panel-title">
            <Settings size={20} color="var(--primary)" />
            <span>Configurable Operational Parameters</span>
          </div>
        </div>

        {loading && settings.length === 0 ? (
          <div className="loading-box">
            <div className="spinner" />
            <p>Loading platform configuration parameters...</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Configuration Parameter</th>
                  <th>Current Value</th>
                  <th>Parameter Description</th>
                  <th>Last Updated</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {settings.map((s) => (
                  <tr key={s.settingKey}>
                    <td className="strong code-cell">{s.settingKey}</td>
                    <td>
                      <span className="status-pill neutral" style={{ fontFamily: 'var(--font-mono)' }}>
                        {s.settingValue}
                      </span>
                    </td>
                    <td>{s.description}</td>
                    <td style={{ fontSize: '0.75rem' }}>
                      {s.updatedAt ? new Date(s.updatedAt).toLocaleString() : 'System default'}
                    </td>
                    <td>
                      <button
                        className="btn-secondary"
                        style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                        onClick={() => handleOpenEdit(s)}
                      >
                        Modify
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Edit Setting Modal */}
      <Modal
        isOpen={!!selectedSetting}
        title="Update Operational Parameter"
        onClose={() => setSelectedSetting(null)}
      >
        <form onSubmit={handleSaveSetting}>
          <div style={{ marginBottom: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--primary-light)', marginBottom: '12px' }}>
              <AlertCircle size={16} />
              <span style={{ fontWeight: 600 }}>{selectedSetting?.settingKey}</span>
            </div>

            <div style={{ marginBottom: '12px' }}>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                New Parameter Value:
              </label>
              <input
                type="text"
                required
                className="select-filter"
                style={{ width: '100%', fontFamily: 'var(--font-mono)' }}
                value={newValue}
                onChange={(e) => setNewValue(e.target.value)}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                Description:
              </label>
              <input
                type="text"
                className="select-filter"
                style={{ width: '100%' }}
                value={newDesc}
                onChange={(e) => setNewDesc(e.target.value)}
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn-secondary" onClick={() => setSelectedSetting(null)} disabled={isSubmitting}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={isSubmitting || !newValue.trim()}>
              <Save size={14} style={{ marginRight: '4px' }} />
              {isSubmitting ? 'Saving...' : 'Save Configuration'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
