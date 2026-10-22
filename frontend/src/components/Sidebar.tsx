import {
  LayoutDashboard,
  Radio,
  Car,
  Users,
  UserCheck,
  ShieldAlert,
  CreditCard,
  MessageSquare,
  BarChart3,
  Activity,
  FileText,
  Settings,
  Shield,
  LucideIcon
} from 'lucide-react';

export type NavTab =
  | 'overview'
  | 'live-ops'
  | 'rides'
  | 'riders'
  | 'drivers'
  | 'vehicles'
  | 'payments'
  | 'feedback'
  | 'analytics'
  | 'health'
  | 'audit-logs'
  | 'settings';

interface SidebarProps {
  currentTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  mobileOpen: boolean;
  onCloseMobile: () => void;
  pendingReportsCount?: number;
  unhealthyServicesCount?: number;
}

interface NavItem {
  id: NavTab;
  label: string;
  icon: LucideIcon;
  pulse?: boolean;
  badge?: number;
  badgeType?: 'warning' | 'danger';
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentTab,
  onSelectTab,
  mobileOpen,
  pendingReportsCount = 0,
  unhealthyServicesCount = 0
}) => {
  const navItems: NavItem[] = [
    { id: 'overview', label: 'Executive Overview', icon: LayoutDashboard },
    { id: 'live-ops', label: 'Live Operations', icon: Radio, pulse: true },
    { id: 'rides', label: 'Rides & Bookings', icon: Car },
    { id: 'riders', label: 'Riders', icon: Users },
    { id: 'drivers', label: 'Drivers', icon: UserCheck },
    { id: 'vehicles', label: 'Vehicles', icon: ShieldAlert },
    { id: 'payments', label: 'Payments & Revenue', icon: CreditCard },
    {
      id: 'feedback',
      label: 'Feedback & Moderation',
      icon: MessageSquare,
      badge: pendingReportsCount > 0 ? pendingReportsCount : undefined,
      badgeType: 'warning'
    },
    { id: 'analytics', label: 'Reports & Analytics', icon: BarChart3 },
    {
      id: 'health',
      label: 'Service Health',
      icon: Activity,
      badge: unhealthyServicesCount > 0 ? unhealthyServicesCount : undefined,
      badgeType: 'danger'
    },
    { id: 'audit-logs', label: 'Audit Logs', icon: FileText },
    { id: 'settings', label: 'Settings', icon: Settings }
  ];

  return (
    <aside className={`sidebar ${mobileOpen ? 'mobile-open' : ''}`}>
      <div className="sidebar-header">
        <div className="brand-icon-box">
          <Shield size={22} color="#ffffff" />
        </div>
        <div className="brand-text">
          <h1>LEXOR</h1>
          <span>Operations HQ</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        <div className="nav-section-title">Operations Console</div>
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              className={`nav-link-btn ${isActive ? 'active' : ''}`}
              onClick={() => onSelectTab(item.id as NavTab)}
            >
              <Icon size={18} />
              <span>{item.label}</span>
              {item.badge && (
                <span className={`nav-badge ${item.badgeType === 'danger' ? 'danger' : ''}`}>
                  {item.badge}
                </span>
              )}
              {item.pulse && (
                <span className="pulse-dot" style={{ marginLeft: 'auto' }} />
              )}
            </button>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div className="admin-pill">
          <div className="admin-avatar">AD</div>
          <div className="admin-info">
            <div className="name">Ops Administrator</div>
            <div className="role">ROLE_ADMIN</div>
          </div>
        </div>
      </div>
    </aside>
  );
};
