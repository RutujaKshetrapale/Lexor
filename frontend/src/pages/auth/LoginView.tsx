import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { LogIn, UserPlus, Shield, Car, Navigation, AlertCircle, Sparkles } from 'lucide-react';

interface LoginViewProps {
  onSuccess?: () => void;
  onNavigateRegister?: () => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onSuccess, onNavigateRegister }) => {
  const { login, switchDemoRole, isLoading } = useAuth();
  const [email, setEmail] = useState<string>('ananya.rider@lexor.mobility');
  const [password, setPassword] = useState<string>('Pass@1234');
  const [selectedRole, setSelectedRole] = useState<'RIDER' | 'DRIVER' | 'ADMIN'>('RIDER');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    try {
      await login(email, password, selectedRole);
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to authenticate. Please verify email and password.');
    }
  };

  const handleQuickLoad = (role: 'RIDER' | 'DRIVER' | 'ADMIN') => {
    setSelectedRole(role);
    if (role === 'RIDER') {
      setEmail('ananya.rider@lexor.mobility');
      setPassword('Pass@1234');
    } else if (role === 'DRIVER') {
      setEmail('vikram.driver@lexor.mobility');
      setPassword('Pass@1234');
    } else {
      setEmail('admin.ops@lexor.mobility');
      setPassword('Pass@1234');
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-140px)] p-4">
      <div className="w-full max-w-md rounded-2xl bg-[#0c1220]/95 backdrop-blur-md border border-slate-800 p-6 shadow-2xl">
        {/* Header Badge */}
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 mb-3 shadow-lg shadow-indigo-950/50">
            <LogIn className="w-6 h-6" />
          </div>
          <h1 className="text-xl font-bold text-white tracking-tight">Sign In to LEXOR</h1>
          <p className="text-xs text-slate-400 mt-1">
            Access Rider booking, Driver dispatch console, or Operations HQ
          </p>
        </div>

        {/* Quick Demo Pre-fill Bar */}
        <div className="mb-5 p-3 rounded-xl bg-slate-900/80 border border-slate-800">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1">
              <Sparkles className="w-3 h-3 text-indigo-400" />
              <span>One-Click Role Profiles:</span>
            </span>
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            <button
              type="button"
              onClick={() => handleQuickLoad('RIDER')}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 border transition-all ${
                selectedRole === 'RIDER'
                  ? 'bg-emerald-500 text-slate-950 border-emerald-400 font-bold'
                  : 'bg-slate-800 hover:bg-slate-700 text-slate-300 border-slate-700'
              }`}
            >
              <Navigation className="w-3 h-3" />
              <span>Rider</span>
            </button>

            <button
              type="button"
              onClick={() => handleQuickLoad('DRIVER')}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 border transition-all ${
                selectedRole === 'DRIVER'
                  ? 'bg-amber-500 text-slate-950 border-amber-400 font-bold'
                  : 'bg-slate-800 hover:bg-slate-700 text-slate-300 border-slate-700'
              }`}
            >
              <Car className="w-3 h-3" />
              <span>Driver</span>
            </button>

            <button
              type="button"
              onClick={() => handleQuickLoad('ADMIN')}
              className={`py-1.5 px-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1 border transition-all ${
                selectedRole === 'ADMIN'
                  ? 'bg-indigo-600 text-white border-indigo-400 font-bold'
                  : 'bg-slate-800 hover:bg-slate-700 text-slate-300 border-slate-700'
              }`}
            >
              <Shield className="w-3 h-3" />
              <span>Admin</span>
            </button>
          </div>
        </div>

        {errorMsg && (
          <div className="mb-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition-colors"
              placeholder="name@lexor.mobility"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 transition-colors"
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs transition-all shadow-lg shadow-indigo-950/50 flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <LogIn className="w-4 h-4" />
            <span>{isLoading ? 'Authenticating...' : `Sign In as ${selectedRole}`}</span>
          </button>
        </form>

        {/* Direct Instant Demo Bypass */}
        <div className="mt-4 pt-4 border-t border-slate-800 text-center">
          <p className="text-[11px] text-slate-400 mb-2">Want to evaluate without entering credentials?</p>
          <button
            type="button"
            onClick={() => {
              switchDemoRole(selectedRole);
              if (onSuccess) onSuccess();
            }}
            className="w-full py-2 px-3 rounded-xl bg-slate-800/90 hover:bg-slate-700 border border-slate-700 text-xs font-semibold text-indigo-300 transition-colors"
          >
            ⚡ Launch Verified Demo Session ({selectedRole})
          </button>
        </div>

        {onNavigateRegister && (
          <div className="mt-4 text-center">
            <button
              type="button"
              onClick={onNavigateRegister}
              className="text-xs text-indigo-400 hover:text-indigo-300 font-medium inline-flex items-center gap-1"
            >
              <UserPlus className="w-3.5 h-3.5" />
              <span>Need an account? Register as Rider or Driver</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
