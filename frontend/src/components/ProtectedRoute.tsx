import React, { ReactNode } from 'react';
import { useAuth } from '../context/AuthContext';
import { UserRole } from '../types';
import { ShieldAlert, LogIn, RefreshCw } from 'lucide-react';

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: UserRole[];
  onNavigateLogin?: () => void;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  allowedRoles,
  onNavigateLogin
}) => {
  const { user, isAuthenticated, isLoading, switchDemoRole } = useAuth();

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] text-slate-400 gap-3">
        <RefreshCw className="w-8 h-8 text-indigo-400 animate-spin" />
        <p className="text-sm font-medium">Validating cryptographic credentials...</p>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px] p-6 text-center max-w-md mx-auto">
        <div className="p-4 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 mb-4">
          <LogIn className="w-8 h-8" />
        </div>
        <h2 className="text-lg font-bold text-white mb-1">Authentication Required</h2>
        <p className="text-xs text-slate-400 mb-6">
          You must be signed in to access this mobility workflow. Please authenticate using your credentials or launch in demonstration mode.
        </p>

        <div className="flex flex-col w-full gap-2.5">
          {onNavigateLogin && (
            <button
              type="button"
              onClick={onNavigateLogin}
              className="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition-colors shadow-lg shadow-indigo-900/30"
            >
              Sign In to LEXOR
            </button>
          )}

          <div className="flex items-center gap-2 pt-2">
            <button
              type="button"
              onClick={() => switchDemoRole('RIDER')}
              className="flex-1 py-2 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium border border-slate-700"
            >
              Demo Rider
            </button>
            <button
              type="button"
              onClick={() => switchDemoRole('DRIVER')}
              className="flex-1 py-2 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium border border-slate-700"
            >
              Demo Driver
            </button>
            <button
              type="button"
              onClick={() => switchDemoRole('ADMIN')}
              className="flex-1 py-2 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium border border-slate-700"
            >
              Demo Admin
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Check role eligibility if specified
  if (allowedRoles && allowedRoles.length > 0) {
    const userRole = user.role.toUpperCase();
    const hasRole = allowedRoles.some((r) => {
      const normalized = r.toUpperCase();
      return (
        userRole === normalized ||
        userRole === `ROLE_${normalized}` ||
        `ROLE_${userRole}` === normalized
      );
    });

    if (!hasRole) {
      return (
        <div className="flex flex-col items-center justify-center min-h-[400px] p-6 text-center max-w-md mx-auto">
          <div className="p-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400 mb-4">
            <ShieldAlert className="w-8 h-8" />
          </div>
          <h2 className="text-lg font-bold text-white mb-1">Access Restricted</h2>
          <p className="text-xs text-slate-400 mb-6">
            Your current account role (<span className="text-amber-400 font-semibold">{user.role}</span>) does not possess authority to access this space.
          </p>

          <div className="flex flex-col w-full gap-2">
            <p className="text-[11px] text-slate-500 uppercase tracking-wider font-semibold mb-1">
              Switch to an authorized role for this workflow:
            </p>
            <div className="flex items-center gap-2">
              {allowedRoles.map((r) => {
                const roleKey = r.replace('ROLE_', '') as 'RIDER' | 'DRIVER' | 'ADMIN';
                return (
                  <button
                    key={r}
                    type="button"
                    onClick={() => switchDemoRole(roleKey)}
                    className="flex-1 py-2 px-3 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
                  >
                    Switch to {roleKey}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      );
    }
  }

  return <>{children}</>;
};
