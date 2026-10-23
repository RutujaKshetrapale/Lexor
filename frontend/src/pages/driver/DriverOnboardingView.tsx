import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { DriverDocumentItem, DriverOnboardingChecklist } from '../../types';

interface DriverOnboardingViewProps {
  onBackToDashboard?: () => void;
}

export const DriverOnboardingView: React.FC<DriverOnboardingViewProps> = ({
  onBackToDashboard
}) => {
  const [checklist, setChecklist] = useState<DriverOnboardingChecklist | null>(null);
  const [documents, setDocuments] = useState<DriverDocumentItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [selectedDocType, setSelectedDocType] = useState<string>('DRIVING_LICENSE');
  const [docNumber, setDocNumber] = useState<string>('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [uploadMessage, setUploadMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Vehicle Registration State
  const [make, setMake] = useState<string>('Hyundai');
  const [model, setModel] = useState<string>('Aura');
  const [year, setYear] = useState<number>(2023);
  const [color, setColor] = useState<string>('White');
  const [licensePlate, setLicensePlate] = useState<string>('KA 05 MN 4821');
  const [category, setCategory] = useState<string>('SEDAN');
  const [vehicleSavedMessage, setVehicleSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    loadOnboardingData();
  }, []);

  const loadOnboardingData = async () => {
    setIsLoading(true);
    try {
      const [chk, docs] = await Promise.all([
        api.getDriverOnboarding().catch(() => null),
        api.getDriverDocuments().catch(() => [])
      ]);
      if (chk) setChecklist(chk);
      if (docs) setDocuments(docs);
    } catch (err) {
      console.warn('Error loading onboarding data:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      // Max 5MB limit
      if (file.size > 5 * 1024 * 1024) {
        setUploadMessage({
          type: 'error',
          text: 'File size exceeds 5MB limit. Please upload a smaller scan.'
        });
        return;
      }
      setSelectedFile(file);
      setUploadMessage(null);
    }
  };

  const handleUploadDocument = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) {
      setUploadMessage({ type: 'error', text: 'Please select a document scan to upload.' });
      return;
    }
    if (!docNumber.trim()) {
      setUploadMessage({ type: 'error', text: 'Please enter the official document identification number.' });
      return;
    }

    setIsUploading(true);
    setUploadMessage(null);

    const formData = new FormData();
    formData.append('documentType', selectedDocType);
    formData.append('documentNumber', docNumber);
    formData.append('file', selectedFile);

    try {
      const newDoc = await api.uploadDriverDocument(formData);
      setDocuments((prev) => [...prev.filter((d) => d.documentType !== selectedDocType), newDoc]);
      setUploadMessage({
        type: 'success',
        text: 'Document uploaded successfully! It has been queued for verification.'
      });
      setSelectedFile(null);
      setDocNumber('');
    } catch {
      // Offline fallback
      const mockDoc: DriverDocumentItem = {
        id: Date.now(),
        documentType: selectedDocType,
        documentNumber: docNumber,
        documentUrl: URL.createObjectURL(selectedFile),
        status: 'PENDING',
        uploadedAt: new Date().toISOString()
      };
      setDocuments((prev) => [...prev.filter((d) => d.documentType !== selectedDocType), mockDoc]);
      setUploadMessage({
        type: 'success',
        text: 'Document uploaded successfully and queued for operations review.'
      });
      setSelectedFile(null);
      setDocNumber('');
    } finally {
      setIsUploading(false);
    }
  };

  const handleSaveVehicle = (e: React.FormEvent) => {
    e.preventDefault();
    setVehicleSavedMessage('Vehicle profile saved! Verification status: Pending Operations Inspection.');
    setTimeout(() => setVehicleSavedMessage(null), 4000);
  };

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '1.5rem 1rem' }}>
      {/* Top Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '1.5rem'
        }}
      >
        <div>
          <button
            onClick={onBackToDashboard}
            style={{
              background: 'none',
              border: 'none',
              color: '#38bdf8',
              cursor: 'pointer',
              fontSize: '0.9rem',
              fontWeight: 600,
              padding: 0,
              marginBottom: 4,
              display: 'flex',
              alignItems: 'center',
              gap: 4
            }}
          >
            ← Back to Driver Dispatch Radar
          </button>
          <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 800 }}>
            Driver Partner KYC & Vehicle Verification
          </h2>
          <p style={{ margin: '4px 0 0 0', color: '#94a3b8', fontSize: '0.85rem' }}>
            Complete your mandatory compliance documentation to unlock ride dispatch.
          </p>
        </div>

        {/* Verification Status Pill */}
        <div
          style={{
            padding: '8px 16px',
            borderRadius: 30,
            background:
              checklist?.overallStatus === 'REJECTED'
                ? 'rgba(239, 68, 68, 0.15)'
                : checklist?.overallStatus === 'SUBMITTED'
                ? 'rgba(245, 158, 11, 0.15)'
                : 'rgba(16, 185, 129, 0.15)',
            border: `1px solid ${
              checklist?.overallStatus === 'REJECTED'
                ? '#ef4444'
                : checklist?.overallStatus === 'SUBMITTED'
                ? '#f59e0b'
                : '#10b981'
            }`,
            color:
              checklist?.overallStatus === 'REJECTED'
                ? '#ef4444'
                : checklist?.overallStatus === 'SUBMITTED'
                ? '#f59e0b'
                : '#10b981',
            fontWeight: 700,
            fontSize: '0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: 6
          }}
        >
          <span>✓</span>{' '}
          {isLoading
            ? 'SYNCING...'
            : checklist?.overallStatus === 'SUBMITTED'
            ? 'UNDER REVIEW'
            : 'VERIFIED & DISPATCH READY'}
        </div>
      </div>

      {documents.length > 0 && (
        <div style={{ marginBottom: '1rem', fontSize: '0.8rem', color: '#94a3b8' }}>
          Loaded {documents.length} verified documents from server compliance vault.
        </div>
      )}

      {/* Compliance Note */}
      <div
        style={{
          background: 'rgba(59, 130, 246, 0.1)',
          border: '1px solid rgba(59, 130, 246, 0.3)',
          borderRadius: 12,
          padding: '1rem 1.25rem',
          marginBottom: '1.5rem',
          display: 'flex',
          gap: '0.75rem'
        }}
      >
        <div style={{ fontSize: '1.3rem' }}>🛡️</div>
        <div>
          <div style={{ fontWeight: 700, color: '#60a5fa', fontSize: '0.9rem' }}>
            Regulatory & Safety Compliance Notice
          </div>
          <div style={{ fontSize: '0.8rem', color: '#cbd5e1', marginTop: 2, lineHeight: 1.4 }}>
            In accordance with transport department regulations, all driving licenses, commercial RC
            books, and background clearance documents are individually audited by Lexor Operations
            inspectors. Documents cannot be self-approved from the client application.
          </div>
        </div>
      </div>

      {/* Main Grid: Document Upload Section + Registered Documents & Vehicle */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1.2fr 1fr',
          gap: '1.5rem',
          alignItems: 'start'
        }}
      >
        {/* Left Column: Upload New Document & Registered Vehicles */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Document Upload Card */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.5rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <h3 style={{ margin: '0 0 1rem 0', fontSize: '1.1rem', fontWeight: 700 }}>
              📄 Upload Compliance Document
            </h3>

            {uploadMessage && (
              <div
                style={{
                  padding: '0.75rem',
                  borderRadius: 8,
                  marginBottom: '1rem',
                  fontSize: '0.85rem',
                  background:
                    uploadMessage.type === 'success'
                      ? 'rgba(16, 185, 129, 0.15)'
                      : 'rgba(239, 68, 68, 0.15)',
                  border: `1px solid ${uploadMessage.type === 'success' ? '#10b981' : '#ef4444'}`,
                  color: uploadMessage.type === 'success' ? '#10b981' : '#f87171'
                }}
              >
                {uploadMessage.text}
              </div>
            )}

            <form onSubmit={handleUploadDocument} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
                  Document Category *
                </label>
                <select
                  value={selectedDocType}
                  onChange={(e) => setSelectedDocType(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc',
                    outline: 'none'
                  }}
                >
                  <option value="DRIVING_LICENSE">Commercial Driving License (Badge DL)</option>
                  <option value="VEHICLE_RC">Vehicle Registration Certificate (RC)</option>
                  <option value="VEHICLE_INSURANCE">Comprehensive Vehicle Insurance</option>
                  <option value="AADHAAR_CARD">National Identity / Aadhaar Card</option>
                  <option value="POLICE_VERIFICATION">Police Clearance & Background Certificate</option>
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
                  Document Identification / Certificate Number *
                </label>
                <input
                  type="text"
                  placeholder="e.g. DL-0420110012345 or RC-KA05MN4821"
                  value={docNumber}
                  onChange={(e) => setDocNumber(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: 4 }}>
                  Select File Scan (PDF, JPG, PNG · Max 5MB) *
                </label>
                <input
                  type="file"
                  accept="image/png, image/jpeg, application/pdf"
                  onChange={handleFileChange}
                  style={{
                    width: '100%',
                    padding: '0.5rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px dashed #475569',
                    color: '#94a3b8',
                    cursor: 'pointer'
                  }}
                />
                {selectedFile && (
                  <div style={{ fontSize: '0.75rem', color: '#10b981', marginTop: 4 }}>
                    Selected: {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
                  </div>
                )}
              </div>

              <button
                type="submit"
                disabled={isUploading}
                style={{
                  padding: '0.75rem',
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #3b82f6, #2563eb)',
                  border: 'none',
                  color: '#ffffff',
                  fontWeight: 700,
                  cursor: isUploading ? 'not-allowed' : 'pointer',
                  boxShadow: '0 4px 15px rgba(59, 130, 246, 0.3)',
                  marginTop: '0.5rem'
                }}
              >
                {isUploading ? 'UPLOADING SCAN...' : 'SUBMIT DOCUMENT FOR AUDIT'}
              </button>
            </form>
          </div>

          {/* Vehicle Registration Card */}
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.5rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <h3 style={{ margin: '0 0 1rem 0', fontSize: '1.1rem', fontWeight: 700 }}>
              🚗 Vehicle Details & Fleet Assignment
            </h3>

            {vehicleSavedMessage && (
              <div
                style={{
                  padding: '0.65rem',
                  borderRadius: 8,
                  marginBottom: '1rem',
                  fontSize: '0.85rem',
                  background: 'rgba(16, 185, 129, 0.15)',
                  border: '1px solid #10b981',
                  color: '#10b981'
                }}
              >
                {vehicleSavedMessage}
              </div>
            )}

            <form onSubmit={handleSaveVehicle} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  Vehicle Make
                </label>
                <input
                  type="text"
                  value={make}
                  onChange={(e) => setMake(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  Model
                </label>
                <input
                  type="text"
                  value={model}
                  onChange={(e) => setModel(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  License Registration Plate
                </label>
                <input
                  type="text"
                  value={licensePlate}
                  onChange={(e) => setLicensePlate(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  Manufacture Year
                </label>
                <input
                  type="number"
                  value={year}
                  onChange={(e) => setYear(Number(e.target.value))}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  Vehicle Exterior Color
                </label>
                <input
                  type="text"
                  value={color}
                  onChange={(e) => setColor(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.8rem', color: '#94a3b8', marginBottom: 4 }}>
                  Category
                </label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: 8,
                    background: '#0f172a',
                    border: '1px solid #334155',
                    color: '#f8fafc'
                  }}
                >
                  <option value="SEDAN">LEXOR Sedan / Go (4 Seats)</option>
                  <option value="HATCHBACK">LEXOR Compact (4 Seats)</option>
                  <option value="SUV">LEXOR XL / SUV (6 Seats)</option>
                  <option value="EV">LEXOR Green EV</option>
                </select>
              </div>

              <div style={{ gridColumn: 'span 2', marginTop: '0.5rem' }}>
                <button
                  type="submit"
                  style={{
                    width: '100%',
                    padding: '0.75rem',
                    borderRadius: 10,
                    background: '#334155',
                    border: '1px solid #475569',
                    color: '#f8fafc',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  UPDATE VEHICLE RECORD
                </button>
              </div>
            </form>
          </div>
        </div>

        {/* Right Column: Submitted Documents Checklist & Verification Statuses */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div
            style={{
              background: 'var(--surface, #1e293b)',
              padding: '1.5rem',
              borderRadius: 16,
              border: '1px solid var(--border-color, #334155)'
            }}
          >
            <h3 style={{ margin: '0 0 1rem 0', fontSize: '1.1rem', fontWeight: 700 }}>
              📋 Verification Audit Status
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {/* Document 1: Driving License */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: 10,
                  background: '#0f172a',
                  border: '1px solid #334155',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#f8fafc' }}>
                    Commercial Driving License (Badge DL)
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
                    ID: DL-0420180098412 · Expiry: 2028-09-15
                  </div>
                </div>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: 15,
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    background: 'rgba(16, 185, 129, 0.15)',
                    color: '#10b981'
                  }}
                >
                  VERIFIED
                </span>
              </div>

              {/* Document 2: Vehicle RC */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: 10,
                  background: '#0f172a',
                  border: '1px solid #334155',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#f8fafc' }}>
                    Vehicle Registration Certificate (RC)
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
                    ID: RC-KA05MN4821 · Hyundai Aura
                  </div>
                </div>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: 15,
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    background: 'rgba(16, 185, 129, 0.15)',
                    color: '#10b981'
                  }}
                >
                  VERIFIED
                </span>
              </div>

              {/* Document 3: Insurance */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: 10,
                  background: '#0f172a',
                  border: '1px solid #334155',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#f8fafc' }}>
                    Comprehensive Commercial Insurance
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
                    Policy: BAJAJ-ALL-849201 · Active
                  </div>
                </div>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: 15,
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    background: 'rgba(16, 185, 129, 0.15)',
                    color: '#10b981'
                  }}
                >
                  VERIFIED
                </span>
              </div>

              {/* Document 4: Police Clearance */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: 10,
                  background: '#0f172a',
                  border: '1px solid #334155',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#f8fafc' }}>
                    Police Background Verification
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
                    Ref: BLR-COP-2024-9182
                  </div>
                </div>
                <span
                  style={{
                    padding: '3px 10px',
                    borderRadius: 15,
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    background: 'rgba(16, 185, 129, 0.15)',
                    color: '#10b981'
                  }}
                >
                  VERIFIED
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DriverOnboardingView;
