import React, { useState } from 'react';
import { LucideIcon, HelpCircle } from 'lucide-react';

interface KpiCardProps {
  title: string;
  value: string | number;
  icon: LucideIcon;
  theme?: 'indigo' | 'emerald' | 'amber' | 'rose' | 'cyan';
  subtitle?: string;
  definition?: string;
  isCurrency?: boolean;
}

export const KpiCard: React.FC<KpiCardProps> = ({
  title,
  value,
  icon: Icon,
  theme = 'indigo',
  subtitle,
  definition,
  isCurrency = false
}) => {
  const [showTooltip, setShowTooltip] = useState(false);

  const formattedValue = typeof value === 'number'
    ? isCurrency
      ? `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      : value.toLocaleString()
    : value;

  return (
    <div className={`kpi-card ${theme}`}>
      <div className="kpi-card-header">
        <span className="kpi-title">{title}</span>
        <div className={`kpi-icon-container ${theme}`}>
          <Icon size={18} />
        </div>
      </div>

      <div className="kpi-value">{formattedValue}</div>

      <div className="kpi-footer">
        <span>{subtitle || 'Authoritative data'}</span>
        {definition && (
          <div style={{ position: 'relative' }}>
            <button
              type="button"
              onClick={() => setShowTooltip(!showTooltip)}
              onMouseEnter={() => setShowTooltip(true)}
              onMouseLeave={() => setShowTooltip(false)}
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--text-muted)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center'
              }}
              aria-label={`Definition of ${title}`}
            >
              <HelpCircle size={14} />
            </button>
            {showTooltip && (
              <div
                style={{
                  position: 'absolute',
                  bottom: '24px',
                  right: '0',
                  width: '260px',
                  background: 'var(--bg-modal)',
                  border: '1px solid var(--border-medium)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '10px 12px',
                  fontSize: '0.72rem',
                  color: 'var(--text-secondary)',
                  boxShadow: 'var(--shadow-lg)',
                  zIndex: 20,
                  lineHeight: 1.4
                }}
              >
                <div style={{ fontWeight: 600, color: '#fff', marginBottom: '4px' }}>Metric Definition:</div>
                {definition}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
