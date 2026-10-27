/**
 * LEXOR Payment Gateway Integration Boundary & Tokenized Checkout Handler
 *
 * Implements tokenized UPI and Card checkout via Razorpay SDK
 * without exposing raw card data or merchant secrets to the browser.
 *
 * All financial state is reconciled authoritatively via backend webhooks
 * and signature verification.
 */

import { api } from './api';

export interface PaymentIntentDetails {
  paymentUuid: string;
  orderId?: string;
  keyId?: string;
  amount: number;
  amountPaise: number;
  currency: string;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  paymentMethod: string;
}

export interface CheckoutResult {
  success: boolean;
  paymentUuid: string;
  providerPaymentId?: string;
  providerOrderId?: string;
  providerSignature?: string;
  isTestMode: boolean;
  status: 'SUCCEEDED' | 'PENDING' | 'FAILED' | 'CANCELLED';
  message: string;
}

declare global {
  interface Window {
    Razorpay?: any;
  }
}

class PaymentProviderService {
  private isConfigured: boolean = false;
  private keyId: string = '';
  private scriptLoadingPromise: Promise<boolean> | null = null;

  constructor() {
    const env = (import.meta as any).env || {};
    this.keyId = (env.VITE_RAZORPAY_KEY_ID || '').trim();
    this.isConfigured = Boolean(this.keyId && !this.keyId.startsWith('your_'));
  }

  public isLiveGatewayConfigured(): boolean {
    return this.isConfigured;
  }

  public getGatewayInfo(): { name: string; isLive: boolean; keyIdPreview?: string } {
    return {
      name: 'Razorpay / UPI Payment Suite',
      isLive: this.isConfigured,
      keyIdPreview: this.isConfigured ? `${this.keyId.substring(0, 8)}...` : undefined
    };
  }

  /**
   * Dynamically loads Razorpay checkout script
   */
  public async loadRazorpayScript(): Promise<boolean> {
    if (typeof window === 'undefined') return false;
    if (typeof window.Razorpay !== 'undefined') return true;

    if (this.scriptLoadingPromise) {
      return this.scriptLoadingPromise;
    }

    this.scriptLoadingPromise = new Promise<boolean>((resolve) => {
      const scriptId = 'razorpay-checkout-sdk';
      if (document.getElementById(scriptId)) {
        resolve(typeof window.Razorpay !== 'undefined');
        return;
      }

      const script = document.createElement('script');
      script.id = scriptId;
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.onload = () => resolve(true);
      script.onerror = () => {
        console.warn('Razorpay checkout script failed to load, falling back to sandbox mode.');
        resolve(false);
      };
      document.head.appendChild(script);
    });

    return this.scriptLoadingPromise;
  }

  /**
   * Launch tokenized hosted checkout modal
   * Falls back to sandbox simulation boundary if no merchant key is provided in .env
   */
  public async openCheckout(intent: PaymentIntentDetails): Promise<CheckoutResult> {
    // Attempt dynamic script loading if key is configured
    if (this.isConfigured && typeof window.Razorpay === 'undefined') {
      await this.loadRazorpayScript();
    }

    // 1. If real Razorpay SDK & client key exist in window
    if (this.isConfigured && typeof window.Razorpay !== 'undefined') {
      return new Promise<CheckoutResult>((resolve) => {
        const options = {
          key: this.keyId,
          amount: intent.amountPaise,
          currency: intent.currency || 'INR',
          name: 'LEXOR Mobility',
          description: `Ride Fare Payment - ${intent.paymentUuid.substring(0, 8)}`,
          order_id: intent.orderId,
          prefill: {
            name: intent.customerName || 'Rider',
            email: intent.customerEmail || 'rider@lexor.com',
            contact: intent.customerPhone || '+919876543211'
          },
          theme: {
            color: '#6366f1'
          },
          handler: (response: any) => {
            resolve({
              success: true,
              paymentUuid: intent.paymentUuid,
              providerPaymentId: response.razorpay_payment_id,
              providerOrderId: response.razorpay_order_id,
              providerSignature: response.razorpay_signature,
              isTestMode: false,
              status: 'SUCCEEDED',
              message: 'Tokenized checkout authorized. Signature submitted for authoritative verification.'
            });
          },
          modal: {
            ondismiss: () => {
              resolve({
                success: false,
                paymentUuid: intent.paymentUuid,
                isTestMode: false,
                status: 'CANCELLED',
                message: 'Payment was dismissed by user.'
              });
            }
          }
        };

        const rzp = new window.Razorpay(options);
        rzp.open();
      });
    }

    // 2. Safe Sandbox Boundary / Mock Gateway (Explicitly labeled)
    return new Promise<CheckoutResult>((resolve) => {
      // Simulate asynchronous gateway roundtrip
      setTimeout(() => {
        const mockProviderPaymentId = `pay_sim_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
        resolve({
          success: true,
          paymentUuid: intent.paymentUuid,
          providerPaymentId: mockProviderPaymentId,
          providerOrderId: intent.orderId || `order_sim_${Date.now()}`,
          providerSignature: `sig_sandbox_valid_${Date.now()}`,
          isTestMode: true,
          status: 'SUCCEEDED',
          message: 'Payment completed via Sandbox Gateway boundary. Awaiting authoritative webhook confirmation.'
        });
      }, 750);
    });
  }

  /**
   * Reconcile payment status authoritatively with backend transaction record
   */
  public async verifyPaymentStatus(paymentUuid: string): Promise<{ status: string; isComplete: boolean }> {
    try {
      const payment = await api.getPaymentDetails(paymentUuid);
      const isComplete = (payment.status as string) === 'SUCCEEDED' || payment.status === 'COMPLETED';
      return {
        status: payment.status,
        isComplete
      };
    } catch {
      return {
        status: 'PENDING',
        isComplete: false
      };
    }
  }
}

export const paymentProvider = new PaymentProviderService();
