import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { UserPlus, LogIn, AlertCircle, Navigation, Car, ShieldCheck } from 'lucide-react';

interface RegisterViewProps {
  onSuccess?: () => void;
  onNavigateLogin?: () => void;
}

export const RegisterView: React.FC<RegisterViewProps> = ({ onSuccess, onNavigateLogin }) => {
  const { register, isLoading } = useAuth();
  const [firstName, setFirstName] = useState<string>('');
  const [lastName, setLastName] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [phoneNumber, setPhoneNumber] = useState<string>('+91');
  const [password, setPassword] = useState<string>('');
  const [role, setRole] = useState<'RIDER' | 'DRIVER'>('RIDER');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    if (password.length < 8) {
      setErrorMsg('Password must be at least 8 characters in length.');
      return;
    }

    try {
      await register({
        firstName,
        lastName,
        email,
        phoneNumber,
        password,
        role: `ROLE_${role}`
      });
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setErrorMsg(err.message || 'Registration failed. Please check field validation.');
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-140px)] p-4">
      <div className="w-full max-w-md rounded-2xl bg-[#0c1220]/95 backdrop-blur-md border border-slate-800 p-6 shadow-2xl">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 mb-3 shadow-lg shadow-indigo-950/50">
            <UserPlus className="w-6 h-6" />
          </div>
          <h1 className="text-xl font-bold text-white tracking-tight">Create LEXOR Account</h1>
          <p className="text-xs text-slate-400 mt-1">
            Join the smart urban mobility platform
          </p>
        </div>

        {/* Role Choice */}
        <div className="grid grid-cols-2 gap-2 mb-4 p-1 rounded-xl bg-slate-900 border border-slate-800">
          <button
            type="button"
            onClick={() => setRole('RIDER')}
            className={`flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-semibold transition-all ${
              role === 'RIDER'
                ? 'bg-emerald-500 text-slate-950 shadow-md font-bold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Navigation className="w-4 h-4" />
            <span>I Want to Ride</span>
          </button>

          <button
            type="button"
            onClick={() => setRole('DRIVER')}
            className={`flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-semibold transition-all ${
              role === 'DRIVER'
                ? 'bg-amber-500 text-slate-950 shadow-md font-bold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Car className="w-4 h-4" />
            <span>I Want to Drive</span>
          </button>
        </div>

        {errorMsg && (
          <div className="mb-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3.5">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">First Name</label>
              <input
                type="text"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                placeholder="Ramesh"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Last Name</label>
              <input
                type="text"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                placeholder="Patil"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              placeholder="ramesh@example.com"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Mobile Phone (with +91)</label>
            <input
              type="tel"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              required
              className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              placeholder="+919876543210"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Password (min 8 chars)</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              placeholder="••••••••"
            />
          </div>

          <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-900/60 border border-slate-800 text-[11px] text-slate-400">
            <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Passwords are hashed with BCrypt. Plaintext credentials are never saved.</span>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white font-bold text-xs transition-all shadow-lg shadow-indigo-950/50 flex items-center justify-center gap-2 disabled:opacity-50"
          >
            <UserPlus className="w-4 h-4" />
            <span>{isLoading ? 'Creating Account...' : `Register as ${role}`}</span>
          </button>
        </form>

        {onNavigateLogin && (
          <div className="mt-4 pt-4 border-t border-slate-800 text-center">
            <button
              type="button"
              onClick={onNavigateLogin}
              className="text-xs text-indigo-400 hover:text-indigo-300 font-medium inline-flex items-center gap-1"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Already registered? Sign in instead</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
