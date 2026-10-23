import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';
import { SavedPlace, EmergencyContactItem } from '../../types';
import { User, MapPin, Phone, ShieldCheck, Plus, Trash2, CheckCircle2 } from 'lucide-react';

export const RiderProfileView: React.FC = () => {
  const { user, updateUserProfile } = useAuth();
  const [firstName, setFirstName] = useState<string>(user?.firstName || '');
  const [lastName, setLastName] = useState<string>(user?.lastName || '');
  const [phoneNumber, setPhoneNumber] = useState<string>(user?.phoneNumber || '');
  const [savedPlaces, setSavedPlaces] = useState<SavedPlace[]>([]);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [savedNotice, setSavedNotice] = useState<string | null>(null);

  // Add Place Form State
  const [addPlaceOpen, setAddPlaceOpen] = useState<boolean>(false);
  const [newPlaceName, setNewPlaceName] = useState<string>('');
  const [newPlaceAddress, setNewPlaceAddress] = useState<string>('');

  // Emergency Contacts
  const [emergencyContacts, setEmergencyContacts] = useState<EmergencyContactItem[]>([
    { id: 1, name: 'Sanjay Sharma (Father)', phoneNumber: '+919876540001', relationship: 'Parent' },
    { id: 2, name: 'Pooja Iyer (Sister)', phoneNumber: '+919876540002', relationship: 'Sibling' }
  ]);

  const handleRemoveContact = (id?: number) => {
    if (!id) return;
    setEmergencyContacts((prev) => prev.filter((c) => c.id !== id));
  };

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName);
      setLastName(user.lastName);
      setPhoneNumber(user.phoneNumber);
    }
    setSavedPlaces(api.getSavedPlaces());
  }, [user]);

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setSavedNotice(null);
    try {
      await updateUserProfile({ firstName, lastName, phoneNumber });
      setSavedNotice('Profile information saved successfully.');
      setTimeout(() => setSavedNotice(null), 3000);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddPlace = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPlaceName.trim() || !newPlaceAddress.trim()) return;

    const newPlace: SavedPlace = {
      id: `place-${Date.now()}`,
      name: newPlaceName,
      address: newPlaceAddress,
      latitude: 12.9716,
      longitude: 77.5946,
      type: 'favorite'
    };

    const updated = api.savePlace(newPlace);
    setSavedPlaces(updated);
    setNewPlaceName('');
    setNewPlaceAddress('');
    setAddPlaceOpen(false);
  };

  const handleDeletePlace = (id: string) => {
    const updated = api.deleteSavedPlace(id);
    setSavedPlaces(updated);
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-4 space-y-4 animate-fade-in">
      <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 shadow-xl">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-lg">
            <User className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-base font-extrabold text-white">Rider Account & Safety Preferences</h1>
            <p className="text-xs text-slate-400">
              Manage personal details, favorite places, and trusted emergency contacts
            </p>
          </div>
        </div>
      </div>

      {savedNotice && (
        <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{savedNotice}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Personal Details Form */}
        <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 shadow-xl space-y-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <ShieldCheck className="w-4 h-4 text-indigo-400" />
            <span>Profile Details</span>
          </h2>

          <form onSubmit={handleSaveProfile} className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">First Name</label>
                <input
                  type="text"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Last Name</label>
                <input
                  type="text"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Email (Authenticated)</label>
              <input
                type="email"
                value={user?.email || ''}
                disabled
                className="w-full px-3 py-2 rounded-xl bg-slate-950/60 border border-slate-800 text-xs text-slate-400 cursor-not-allowed font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Mobile Number</label>
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-xs text-white focus:outline-none focus:border-indigo-500 font-mono"
              />
            </div>

            <button
              type="submit"
              disabled={isSaving}
              className="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs transition-colors shadow-lg shadow-indigo-950/40 disabled:opacity-50"
            >
              {isSaving ? 'Saving Changes...' : 'Update Profile'}
            </button>
          </form>
        </div>

        {/* Saved Places (Home, Work, Favorites) */}
        <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 shadow-xl space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <MapPin className="w-4 h-4 text-emerald-400" />
              <span>Saved Places</span>
            </h2>
            <button
              type="button"
              onClick={() => setAddPlaceOpen(true)}
              className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Place</span>
            </button>
          </div>

          {/* Add Place Drawer */}
          {addPlaceOpen && (
            <form onSubmit={handleAddPlace} className="p-3 rounded-xl bg-slate-900 border border-slate-700 space-y-2">
              <input
                type="text"
                value={newPlaceName}
                onChange={(e) => setNewPlaceName(e.target.value)}
                placeholder="Label (e.g. Gym, Parents' House)"
                className="w-full px-2.5 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white"
                required
              />
              <input
                type="text"
                value={newPlaceAddress}
                onChange={(e) => setNewPlaceAddress(e.target.value)}
                placeholder="Address or landmark in Bengaluru..."
                className="w-full px-2.5 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white"
                required
              />
              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setAddPlaceOpen(false)}
                  className="px-2.5 py-1 rounded text-xs text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-3 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold"
                >
                  Save Place
                </button>
              </div>
            </form>
          )}

          <div className="space-y-2">
            {savedPlaces.map((place) => (
              <div
                key={place.id}
                className="p-3 rounded-xl bg-slate-900/60 border border-slate-800/80 flex items-center justify-between gap-3"
              >
                <div className="flex items-center gap-2.5 truncate">
                  <div className="w-8 h-8 rounded-lg bg-slate-800 flex items-center justify-center text-xs shrink-0">
                    {place.type === 'home' ? '🏠' : place.type === 'work' ? '🏢' : '📍'}
                  </div>
                  <div className="truncate">
                    <h4 className="text-xs font-bold text-white">{place.name}</h4>
                    <p className="text-[11px] text-slate-400 truncate">{place.address}</p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => handleDeletePlace(place.id)}
                  className="p-1.5 text-slate-500 hover:text-rose-400 transition-colors shrink-0"
                  title="Remove saved place"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Emergency Contacts Section */}
      <div className="p-4 rounded-2xl bg-[#0c1220] border border-slate-800 shadow-xl space-y-3">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
          <Phone className="w-4 h-4 text-rose-400" />
          <span>Emergency Contacts (Automated SOS Dispatch)</span>
        </h2>
        <p className="text-xs text-slate-400">
          These contacts are automatically notified via SMS with your live GPS location if you trigger the SOS Safety button during any trip.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
          {emergencyContacts.map((c) => (
            <div key={c.id} className="p-3 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center justify-between">
              <div>
                <h4 className="text-xs font-bold text-slate-200">{c.name}</h4>
                <p className="text-[11px] text-slate-400 font-mono">{c.phoneNumber}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 font-semibold border border-indigo-500/20">
                  {c.relationship}
                </span>
                <button
                  type="button"
                  onClick={() => handleRemoveContact(c.id)}
                  className="p-1 text-slate-500 hover:text-rose-400 transition-colors"
                  title="Remove emergency contact"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
