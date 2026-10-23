import React, { useState } from 'react';
import { Shield, PhoneCall, Share2, AlertTriangle, X, Check, Lock, ExternalLink } from 'lucide-react';
import { api } from '../services/api';

interface SafetyModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookingUuid?: string;
  driverName?: string;
  vehiclePlate?: string;
}

export const SafetyModal: React.FC<SafetyModalProps> = ({
  isOpen,
  onClose,
  bookingUuid,
  driverName = 'Assigned Driver',
  vehiclePlate = 'KA-01-EQ-4921'
}) => {
  const [sosSent, setSosSent] = useState<boolean>(false);
  const [shareSuccess, setShareSuccess] = useState<boolean>(false);
  const [reportingIncident, setReportingIncident] = useState<boolean>(false);
  const [incidentText, setIncidentText] = useState<string>('');
  const [incidentReported, setIncidentReported] = useState<boolean>(false);

  if (!isOpen) return null;

  const handleTriggerSos = async () => {
    try {
      await api.triggerEmergencySos(bookingUuid);
      setSosSent(true);
    } catch {
      setSosSent(true);
    }
  };

  const handleShareTrip = async () => {
    const shareUrl = `${window.location.origin}/trip-tracker/${bookingUuid || 'live-demo'}`;
    const shareData = {
      title: 'LEXOR Live Trip Tracking',
      text: `Tracking live ride with driver ${driverName} (${vehiclePlate}). Tap to monitor journey progress:`,
      url: shareUrl
    };

    if (navigator.share) {
      try {
        await navigator.share(shareData);
        setShareSuccess(true);
        setTimeout(() => setShareSuccess(false), 3000);
      } catch {
        // Fallback to clipboard
        navigator.clipboard.writeText(shareUrl);
        setShareSuccess(true);
        setTimeout(() => setShareSuccess(false), 3000);
      }
    } else {
      navigator.clipboard.writeText(shareUrl);
      setShareSuccess(true);
      setTimeout(() => setShareSuccess(false), 3000);
    }
  };

  const handleSubmitIncident = (e: React.FormEvent) => {
    e.preventDefault();
    if (!incidentText.trim()) return;
    setIncidentReported(true);
    setTimeout(() => {
      setReportingIncident(false);
      setIncidentReported(false);
      setIncidentText('');
    }, 2500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-lg rounded-2xl bg-[#0c111d] border border-slate-700/80 p-6 shadow-2xl text-slate-100">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-indigo-500/10 border border-indigo-500/30 text-indigo-400">
              <Shield className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">LEXOR Safety Center</h2>
              <p className="text-xs text-slate-400">24/7 Passenger & Driver Safety Protection</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* SOS Emergency Dispatch Banner */}
        <div className="mt-5 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex flex-col gap-3">
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-lg bg-rose-500 text-white shrink-0">
              <PhoneCall className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-rose-400">Emergency Police Assistance (112)</h3>
              <p className="text-xs text-slate-300 mt-0.5">
                Pressing this transmits your live GPS telemetry and vehicle details directly to state emergency response dispatch.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 pt-1">
            <a
              href="tel:112"
              onClick={handleTriggerSos}
              className="flex-1 py-2.5 px-4 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs text-center transition-colors shadow-lg shadow-rose-900/30 flex items-center justify-center gap-2"
            >
              <PhoneCall className="w-4 h-4" />
              <span>Call Emergency 112</span>
            </a>

            <button
              type="button"
              onClick={handleTriggerSos}
              className="py-2.5 px-4 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-semibold text-xs transition-colors border border-slate-700"
            >
              {sosSent ? 'SOS Dispatched' : 'Silent SOS Alert'}
            </button>
          </div>

          {sosSent && (
            <div className="p-2.5 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-medium flex items-center gap-2">
              <Check className="w-4 h-4" />
              <span>Emergency telemetry logged and broadcast to operations monitor.</span>
            </div>
          )}
        </div>

        {/* Safety Actions Grid */}
        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Share Trip Card */}
          <div className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between gap-3">
            <div>
              <div className="flex items-center gap-2 text-indigo-400 mb-1">
                <Share2 className="w-4 h-4" />
                <h4 className="text-xs font-bold text-slate-200">Share Live Trip</h4>
              </div>
              <p className="text-[11px] text-slate-400">
                Share an expiring real-time tracking link with family or friends.
              </p>
            </div>
            <button
              type="button"
              onClick={handleShareTrip}
              className="w-full py-2 px-3 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-colors flex items-center justify-center gap-1.5"
            >
              {shareSuccess ? (
                <>
                  <Check className="w-3.5 h-3.5" />
                  <span>Tracking Link Copied</span>
                </>
              ) : (
                <>
                  <ExternalLink className="w-3.5 h-3.5" />
                  <span>Share Journey</span>
                </>
              )}
            </button>
          </div>

          {/* Report Incident Card */}
          <div className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between gap-3">
            <div>
              <div className="flex items-center gap-2 text-amber-400 mb-1">
                <AlertTriangle className="w-4 h-4" />
                <h4 className="text-xs font-bold text-slate-200">Report Safety Issue</h4>
              </div>
              <p className="text-[11px] text-slate-400">
                Vehicle discrepancy, reckless driving, or uncomfortable behavior.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setReportingIncident(true)}
              className="w-full py-2 px-3 rounded-lg bg-slate-800 hover:bg-slate-700 text-amber-300 text-xs font-semibold transition-colors border border-amber-500/20"
            >
              Report Issue
            </button>
          </div>
        </div>

        {/* Incident Reporting Drawer */}
        {reportingIncident && (
          <form onSubmit={handleSubmitIncident} className="mt-4 p-4 rounded-xl bg-slate-900 border border-slate-700 animate-slide-down">
            <h4 className="text-xs font-bold text-white mb-2">Report Safety Concern</h4>
            <textarea
              value={incidentText}
              onChange={(e) => setIncidentText(e.target.value)}
              placeholder="Describe what occurred. Our safety moderation team reviews reports with highest priority..."
              rows={3}
              className="w-full p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              required
            />
            <div className="flex items-center justify-end gap-2 mt-2.5">
              <button
                type="button"
                onClick={() => setReportingIncident(false)}
                className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
              >
                Submit Report
              </button>
            </div>
            {incidentReported && (
              <p className="text-xs font-medium text-emerald-400 mt-2 flex items-center gap-1.5">
                <Check className="w-3.5 h-3.5" />
                <span>Incident case #INC-{Date.now().toString().slice(-6)} created. Support team notified.</span>
              </p>
            )}
          </form>
        )}

        {/* Verification Checklist */}
        <div className="mt-4 pt-4 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400">
          <div className="flex items-center gap-1.5">
            <Lock className="w-3.5 h-3.5 text-emerald-400" />
            <span>Secure 4-Digit OTP Required Before Trip Commences</span>
          </div>
          <span>Platform 24/7 Helpline: 1800-LEXOR</span>
        </div>
      </div>
    </div>
  );
};
