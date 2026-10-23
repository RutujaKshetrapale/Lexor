import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Navigation, User, Car, BarChart3, LogOut, ChevronDown, CheckCircle2, Shield } from 'lucide-react';

interface GlobalHeaderProps {
  onOpenAuthModal?: () => void;
}

export const GlobalHeader: React.FC<GlobalHeaderProps> = ({ onOpenAuthModal }) => {
  const { user, isAuthenticated, experience, setExperience, switchDemoRole, logout } = useAuth();
  const [profileDropdownOpen, setProfileDropdownOpen] = useState<boolean>(false);

  return (
    <header className="sticky top-0 z-40 w-full bg-[#080d18]/90 backdrop-blur-md border-b border-slate-800/80 px-4 py-2.5 transition-all">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
        {/* Brand & Platform Badge */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-900/40 text-white font-extrabold text-sm tracking-wider">
              LX
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold tracking-tight text-white text-base">LEXOR</span>
                <span className="hidden sm:inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  SMART MOBILITY
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Experience Selector Tabs (Rider, Driver, Operations HQ) */}
        <nav className="flex items-center p-1 rounded-xl bg-slate-900/90 border border-slate-800/90 shadow-inner" aria-label="Portal Navigation">
          <button
            type="button"
            onClick={() => setExperience('rider')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              experience === 'rider'
                ? 'bg-emerald-500 text-slate-950 shadow-md font-bold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Navigation className="w-3.5 h-3.5" />
            <span>Rider</span>
          </button>

          <button
            type="button"
            onClick={() => setExperience('driver')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              experience === 'driver'
                ? 'bg-amber-500 text-slate-950 shadow-md font-bold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Car className="w-3.5 h-3.5" />
            <span>Driver</span>
          </button>

          <button
            type="button"
            onClick={() => setExperience('admin')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              experience === 'admin'
                ? 'bg-indigo-600 text-white shadow-md font-bold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <BarChart3 className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Operations</span>
            <span className="sm:hidden">HQ</span>
          </button>
        </nav>

        {/* User Profile & Demo Role Switcher */}
        <div className="flex items-center gap-3">
          {isAuthenticated && user ? (
            <div className="relative">
              <button
                type="button"
                onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
                className="flex items-center gap-2 py-1.5 px-2.5 rounded-xl bg-slate-900/80 hover:bg-slate-800 border border-slate-700/80 text-left transition-colors"
              >
                <div className="w-6 h-6 rounded-full bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-xs shrink-0">
                  {user.firstName[0]}
                </div>
                <div className="hidden md:flex flex-col">
                  <span className="text-xs font-semibold text-slate-200 leading-tight">
                    {user.firstName} {user.lastName}
                  </span>
                  <span className="text-[10px] text-slate-400 font-mono">
                    {user.role.replace('ROLE_', '')}
                  </span>
                </div>
                <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
              </button>

              {/* Profile Dropdown */}
              {profileDropdownOpen && (
                <div className="absolute right-0 mt-2 w-64 rounded-2xl bg-[#0f172a] border border-slate-700/90 shadow-2xl p-2 z-50 animate-slide-down">
                  <div className="p-2 border-b border-slate-800">
                    <p className="text-xs font-bold text-white">
                      {user.firstName} {user.lastName}
                    </p>
                    <p className="text-[11px] text-slate-400 truncate">{user.email}</p>
                    <div className="mt-1 flex items-center gap-1.5 text-[10px] text-emerald-400">
                      <CheckCircle2 className="w-3 h-3" />
                      <span>Verified Active Session</span>
                    </div>
                  </div>

                  {/* Quick Role Switcher for Demo / Pair Programming */}
                  <div className="p-2">
                    <p className="text-[10px] uppercase font-bold text-slate-400 tracking-wider mb-1.5 flex items-center gap-1">
                      <Shield className="w-3 h-3 text-indigo-400" />
                      <span>Switch Session Role:</span>
                    </p>
                    <div className="grid grid-cols-3 gap-1">
                      <button
                        type="button"
                        onClick={() => {
                          switchDemoRole('RIDER');
                          setProfileDropdownOpen(false);
                        }}
                        className={`py-1 px-1.5 rounded-lg text-[11px] font-semibold border ${
                          user.role.includes('RIDER')
                            ? 'bg-emerald-500/20 border-emerald-500 text-emerald-300'
                            : 'bg-slate-800/80 border-slate-700 text-slate-300 hover:bg-slate-700'
                        }`}
                      >
                        Rider
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          switchDemoRole('DRIVER');
                          setProfileDropdownOpen(false);
                        }}
                        className={`py-1 px-1.5 rounded-lg text-[11px] font-semibold border ${
                          user.role.includes('DRIVER')
                            ? 'bg-amber-500/20 border-amber-500 text-amber-300'
                            : 'bg-slate-800/80 border-slate-700 text-slate-300 hover:bg-slate-700'
                        }`}
                      >
                        Driver
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          switchDemoRole('ADMIN');
                          setProfileDropdownOpen(false);
                        }}
                        className={`py-1 px-1.5 rounded-lg text-[11px] font-semibold border ${
                          user.role.includes('ADMIN')
                            ? 'bg-indigo-500/20 border-indigo-500 text-indigo-300'
                            : 'bg-slate-800/80 border-slate-700 text-slate-300 hover:bg-slate-700'
                        }`}
                      >
                        Admin
                      </button>
                    </div>
                  </div>

                  <div className="pt-1 border-t border-slate-800">
                    <button
                      type="button"
                      onClick={() => {
                        logout();
                        setProfileDropdownOpen(false);
                      }}
                      className="w-full flex items-center gap-2 px-3 py-2 text-xs font-semibold text-rose-400 hover:bg-rose-500/10 rounded-xl transition-colors"
                    >
                      <LogOut className="w-4 h-4" />
                      <span>Sign Out</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <button
              type="button"
              onClick={onOpenAuthModal}
              className="flex items-center gap-1.5 py-1.5 px-3.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs transition-colors shadow-md shadow-indigo-900/30"
            >
              <User className="w-3.5 h-3.5" />
              <span>Sign In</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
