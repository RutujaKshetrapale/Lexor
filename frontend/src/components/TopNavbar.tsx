import React from 'react';
import { Menu, RefreshCw, Clock, Globe } from 'lucide-react';

interface TopNavbarProps {
  pageTitle: string;
  onToggleMobileMenu: () => void;
  lastUpdated?: string;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const TopNavbar: React.FC<TopNavbarProps> = ({
  pageTitle,
  onToggleMobileMenu,
  lastUpdated,
  onRefresh,
  isRefreshing = false
}) => {
  return (
    <header className="top-navbar">
      <div className="navbar-left">
        <button
          className="btn-secondary"
          style={{ display: 'none', padding: '6px' }}
          onClick={onToggleMobileMenu}
          aria-label="Toggle Navigation"
        >
          <Menu size={18} />
        </button>
        <h2 className="page-title">{pageTitle}</h2>
        <div className="env-tag">
          <span className="pulse-dot" />
          <span>PROD-CLUSTER</span>
        </div>
      </div>

      <div className="navbar-right">
        {lastUpdated && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            <Clock size={14} />
            <span>Updated: {new Date(lastUpdated).toLocaleTimeString()}</span>
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
          <Globe size={14} />
          <span>UTC (ISO-8601)</span>
        </div>

        {onRefresh && (
          <button
            className="btn-secondary"
            onClick={onRefresh}
            disabled={isRefreshing}
            title="Refresh current dataset"
          >
            <RefreshCw size={14} className={isRefreshing ? 'spinner' : ''} />
            <span style={{ fontSize: '0.8rem' }}>Sync</span>
          </button>
        )}
      </div>
    </header>
  );
};
