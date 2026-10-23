import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { GlobalHeader } from './components/GlobalHeader';
import { RiderAppShell } from './pages/rider/RiderAppShell';
import { DriverAppShell } from './pages/driver/DriverAppShell';
import { LoginView } from './pages/auth/LoginView';
import { RegisterView } from './pages/auth/RegisterView';

// Existing Admin Dashboard Components & Pages (Preserved Exactly)
import { Sidebar, NavTab } from './components/Sidebar';
import { TopNavbar } from './components/TopNavbar';
import { OverviewView } from './pages/OverviewView';
import { LiveOpsView } from './pages/LiveOpsView';
import { RidesBookingsView } from './pages/RidesBookingsView';
import { RidersView } from './pages/RidersView';
import { DriversView } from './pages/DriversView';
import { VehiclesView } from './pages/VehiclesView';
import { PaymentsRevenueView } from './pages/PaymentsRevenueView';
import { FeedbackModerationView } from './pages/FeedbackModerationView';
import { ReportsAnalyticsView } from './pages/ReportsAnalyticsView';
import { ServiceHealthView } from './pages/ServiceHealthView';
import { AuditLogsView } from './pages/AuditLogsView';
import { SettingsView } from './pages/SettingsView';

const LexorAppShell: React.FC = () => {
  const { experience } = useAuth();
  const [showAuthModal, setShowAuthModal] = useState<boolean>(false);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');

  // Admin Dashboard State (Preserving all original admin layout states)
  const [currentTab, setCurrentTab] = useState<NavTab>('overview');
  const [mobileOpen, setMobileOpen] = useState<boolean>(false);
  const [refreshKey, setRefreshKey] = useState<number>(0);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const getPageTitle = (tab: NavTab): string => {
    switch (tab) {
      case 'overview': return 'Executive Overview';
      case 'live-ops': return 'Live Operations Console';
      case 'rides': return 'Rides & Bookings';
      case 'riders': return 'Riders Administration';
      case 'drivers': return 'Drivers & KYC Verification';
      case 'vehicles': return 'Vehicle Fleet Management';
      case 'payments': return 'Payments & Revenue';
      case 'feedback': return 'Ratings & Feedback Moderation';
      case 'analytics': return 'Reports & Analytics';
      case 'health': return 'Service Health & Diagnostics';
      case 'audit-logs': return 'Operations Audit Trail';
      case 'settings': return 'Platform Settings';
      default: return 'Enterprise Operations';
    }
  };

  const handleManualSync = () => {
    setIsRefreshing(true);
    setRefreshKey((k) => k + 1);
    setTimeout(() => {
      setIsRefreshing(false);
    }, 600);
  };

  return (
    <div style={{ minHeight: '100vh', background: '#070b14', color: '#f8fafc', display: 'flex', flexDirection: 'column' }}>
      <GlobalHeader onOpenAuthModal={() => setShowAuthModal(true)} />

      {/* Experience Body */}
      <div style={{ flex: 1 }}>
        {experience === 'rider' && <RiderAppShell />}

        {experience === 'driver' && <DriverAppShell />}

        {experience === 'admin' && (
          <div className="app-container">
            <Sidebar
              currentTab={currentTab}
              onSelectTab={(tab) => {
                setCurrentTab(tab);
                setMobileOpen(false);
              }}
              mobileOpen={mobileOpen}
              onCloseMobile={() => setMobileOpen(false)}
            />

            <div className="main-wrapper">
              <TopNavbar
                pageTitle={getPageTitle(currentTab)}
                onToggleMobileMenu={() => setMobileOpen(!mobileOpen)}
                lastUpdated={new Date().toISOString()}
                onRefresh={handleManualSync}
                isRefreshing={isRefreshing}
              />

              <main key={refreshKey}>
                {currentTab === 'overview' && <OverviewView />}
                {currentTab === 'live-ops' && <LiveOpsView />}
                {currentTab === 'rides' && <RidesBookingsView />}
                {currentTab === 'riders' && <RidersView />}
                {currentTab === 'drivers' && <DriversView />}
                {currentTab === 'vehicles' && <VehiclesView />}
                {currentTab === 'payments' && <PaymentsRevenueView />}
                {currentTab === 'feedback' && <FeedbackModerationView />}
                {currentTab === 'analytics' && <ReportsAnalyticsView />}
                {currentTab === 'health' && <ServiceHealthView />}
                {currentTab === 'audit-logs' && <AuditLogsView />}
                {currentTab === 'settings' && <SettingsView />}
              </main>
            </div>
          </div>
        )}
      </div>

      {/* Auth Modal Overlay */}
      {showAuthModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(7, 11, 20, 0.85)',
            backdropFilter: 'blur(8px)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem'
          }}
        >
          <div style={{ position: 'relative', width: '100%', maxWidth: 440 }}>
            <button
              onClick={() => setShowAuthModal(false)}
              style={{
                position: 'absolute',
                top: 12,
                right: 12,
                background: '#1e293b',
                border: '1px solid #334155',
                color: '#cbd5e1',
                borderRadius: 8,
                padding: '6px 10px',
                cursor: 'pointer',
                zIndex: 10,
                fontSize: '0.9rem'
              }}
              aria-label="Close authentication modal"
            >
              ✕
            </button>
            {authMode === 'login' ? (
              <LoginView
                onSuccess={() => setShowAuthModal(false)}
                onNavigateRegister={() => setAuthMode('register')}
              />
            ) : (
              <RegisterView
                onSuccess={() => setShowAuthModal(false)}
                onNavigateLogin={() => setAuthMode('login')}
              />
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <LexorAppShell />
    </AuthProvider>
  );
};

export default App;
