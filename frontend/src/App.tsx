import React, { useState } from 'react';
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

export const App: React.FC = () => {
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
  );
};

export default App;
