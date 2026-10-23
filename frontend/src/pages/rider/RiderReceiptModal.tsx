import React from 'react';
import { X, Receipt, Download, CheckCircle2, ShieldCheck } from 'lucide-react';
import { DetailedInvoiceReceipt } from '../../types';

interface RiderReceiptModalProps {
  isOpen: boolean;
  onClose: () => void;
  receipt: DetailedInvoiceReceipt | null;
}

export const RiderReceiptModal: React.FC<RiderReceiptModalProps> = ({ isOpen, onClose, receipt }) => {
  if (!isOpen || !receipt) return null;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-md rounded-2xl bg-[#0c1220] border border-slate-700/80 p-6 shadow-2xl text-slate-100">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <Receipt className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Ride Invoice & Receipt</h3>
              <p className="text-[11px] text-slate-400 font-mono">Invoice #{receipt.paymentUuid.slice(0, 8)}</p>
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

        {/* Total Paid Header */}
        <div className="my-5 p-4 rounded-xl bg-slate-900/80 border border-slate-800 text-center">
          <span className="text-xs text-slate-400 uppercase font-semibold tracking-wider">Total Paid</span>
          <div className="text-3xl font-extrabold text-white mt-1">
            ₹{receipt.totalFare.toFixed(2)}
          </div>
          <div className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-xs font-semibold border border-emerald-500/20">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>Paid via {receipt.paymentMethod}</span>
          </div>
        </div>

        {/* Route Details */}
        <div className="space-y-2 mb-4 text-xs">
          <div className="flex items-start gap-2.5">
            <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 mt-1 shrink-0" />
            <div>
              <span className="text-slate-400">Pickup: </span>
              <span className="text-white font-medium">{receipt.pickupAddress}</span>
            </div>
          </div>
          <div className="flex items-start gap-2.5">
            <div className="w-2.5 h-2.5 rounded-full bg-rose-400 mt-1 shrink-0" />
            <div>
              <span className="text-slate-400">Drop: </span>
              <span className="text-white font-medium">{receipt.dropAddress}</span>
            </div>
          </div>
          <div className="flex items-center justify-between text-slate-400 pt-1 text-[11px]">
            <span>Distance: {receipt.distanceKm} km</span>
            <span>Duration: {receipt.durationMinutes} mins</span>
          </div>
        </div>

        {/* Granular Transparent Fare Breakdown */}
        <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800/80 space-y-2 text-xs">
          <div className="flex justify-between text-slate-300">
            <span>Base Fare</span>
            <span className="font-mono">₹{receipt.baseFare.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-slate-300">
            <span>Distance Rate ({receipt.distanceKm} km)</span>
            <span className="font-mono">₹{receipt.distanceFare.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-slate-300">
            <span>Time Rate ({receipt.durationMinutes} min)</span>
            <span className="font-mono">₹{receipt.timeFare.toFixed(2)}</span>
          </div>
          {receipt.surgeAmount > 0 && (
            <div className="flex justify-between text-amber-400">
              <span>Surge Multiplier ({receipt.surgeMultiplier}x)</span>
              <span className="font-mono">+₹{receipt.surgeAmount.toFixed(2)}</span>
            </div>
          )}
          <div className="flex justify-between text-slate-400">
            <span>Applicable GST / Taxes (5%)</span>
            <span className="font-mono">₹{receipt.taxAmount.toFixed(2)}</span>
          </div>
          {receipt.discountAmount > 0 && (
            <div className="flex justify-between text-emerald-400">
              <span>Promo Discount</span>
              <span className="font-mono">-₹{receipt.discountAmount.toFixed(2)}</span>
            </div>
          )}
          <div className="pt-2 border-t border-slate-800 flex justify-between font-bold text-white text-sm">
            <span>Final Amount</span>
            <span className="font-mono">₹{receipt.totalFare.toFixed(2)}</span>
          </div>
        </div>

        {/* Driver Earnings Transparency */}
        <div className="mt-3 p-2.5 rounded-xl bg-indigo-500/5 border border-indigo-500/20 flex items-center justify-between text-xs text-indigo-300">
          <div className="flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-indigo-400" />
            <span>Driver Net Earnings (80%):</span>
          </div>
          <span className="font-mono font-bold">₹{receipt.driverEarnings.toFixed(2)}</span>
        </div>

        {/* Footer Actions */}
        <div className="mt-5 flex items-center gap-3">
          <button
            type="button"
            onClick={handlePrint}
            className="flex-1 py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors border border-slate-700 flex items-center justify-center gap-1.5"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Download Invoice</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="flex-1 py-2 px-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
