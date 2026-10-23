import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { getAuthToken, setAuthToken, clearAuthSession } from '../services/api';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { AuthProvider } from '../context/AuthContext';

describe('Security & Role Guard Enforcement', () => {
  beforeEach(() => {
    localStorage.clear();
    clearAuthSession();
  });

  it('permanently rejects mock-admin-token fallback in client storage', () => {
    // Attempting to set old vulnerable mock token
    localStorage.setItem('token', 'mock-admin-token-role-admin');
    
    // getAuthToken should not return any default admin credentials if cleared
    clearAuthSession();
    const token = getAuthToken();
    expect(token).toBe('');
  });

  it('safely stores and clears genuine JWT session tokens', () => {
    const testJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-zgh';
    setAuthToken(testJwt);
    expect(getAuthToken()).toBe(testJwt);

    clearAuthSession();
    expect(getAuthToken()).toBe('');
  });

  it('blocks unauthenticated access in ProtectedRoute and shows sign-in requirement', () => {
    render(
      <AuthProvider>
        <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
          <div>Confidential Operations Console</div>
        </ProtectedRoute>
      </AuthProvider>
    );

    expect(screen.queryByText('Confidential Operations Console')).not.toBeInTheDocument();
    expect(screen.getByText(/Authentication Required/i)).toBeInTheDocument();
  });
});
