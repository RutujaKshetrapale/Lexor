# LEXOR — External Provider Setup & Configuration Guide

## 1. Overview & Architectural Boundaries
Phase 17 integrates genuine external service providers for:
1. **SMS & Ride OTP Delivery**: MSG91 (India launch primary) and Twilio (extensible fallback).
2. **Push Notifications**: Firebase Cloud Messaging (FCM) & Browser Service Workers.
3. **Digital Payments & UPI**: Razorpay Hosted / Tokenized Checkout with HMAC-SHA256 webhooks.
4. **Real Maps & Geolocation**: Google Maps Platform (Places Autocomplete, Directions, Tiles) with OpenStreetMap / OSRM routing fallbacks.

All provider integrations adhere to the following principles:
- **No Committed Secrets**: Never commit private keys, auth tokens, webhook secrets, or service account files.
- **Frontend Isolation**: No server-side secrets or private provider keys are ever exposed to browser clients or frontend environment variables.
- **Fail-Fast in Production**: Microservices validate required provider credentials at startup when production mode is active (`CommandLineRunner` / ASP.NET startup validation). Missing credentials halt startup with an informative exception rather than silently faking operations.
- **Isolated Sandbox Mode**: When `PROVIDER_SANDBOX_MODE=true` (or when provider keys are omitted in development), safe in-memory sandboxes generate compliant references without making live external API calls or incurring charges.

---

## 2. Environment Variables Specification

### A. SMS & OTP Providers (MSG91 & Twilio)

| Environment Variable | Provider | Consuming Service(s) | How to Obtain / Description | Example Value / Placeholder |
| :--- | :--- | :--- | :--- | :--- |
| `SMS_ENABLED` | Common | `notification-service` | Set to `true` to enable SMS subsystem | `true` |
| `SMS_PROVIDER` | Common | `notification-service` | Active provider router choice: `MSG91`, `TWILIO`, or `MSG91_SANDBOX` | `MSG91` |
| `SMS_RATE_LIMIT_SECONDS` | Common | `notification-service` | Minimum interval (cooldown) between consecutive SMS to the same phone | `60` |
| `MSG91_AUTH_KEY` | MSG91 | `notification-service` | MSG91 Dashboard -> Developer -> Auth Keys | `your_msg91_auth_key_here` |
| `MSG91_TEMPLATE_ID` | MSG91 | `notification-service` | MSG91 Dashboard -> OTP -> Templates (DLT registered template ID) | `your_msg91_dlt_template_id` |
| `MSG91_SENDER_ID` | MSG91 | `notification-service` | Approved 6-character sender ID header (DLT registered) | `LEXORI` |
| `MSG91_OTP_EXPIRY_MIN` | MSG91 | `notification-service` | OTP validity duration in minutes | `5` |
| `TWILIO_ACCOUNT_SID` | Twilio | `notification-service` | Twilio Console -> Account Info -> Account SID (starts with `AC`) | `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` |
| `TWILIO_AUTH_TOKEN` | Twilio | `notification-service` | Twilio Console -> Account Info -> Auth Token | `your_twilio_auth_token_here` |
| `TWILIO_FROM_PHONE` | Twilio | `notification-service` | Twilio Phone Numbers -> Active Numbers (E.164 format) | `+1xxxxxxxxxx` |

---

### B. Push Notifications (Firebase Cloud Messaging — FCM)

| Environment Variable | Provider | Consuming Service(s) | How to Obtain / Description | Example Value / Placeholder |
| :--- | :--- | :--- | :--- | :--- |
| `PUSH_ENABLED` | FCM | `notification-service` | Set to `true` to enable mobile and browser push dispatch | `true` |
| `FCM_PROJECT_ID` | FCM | `notification-service` | Firebase Console -> Project Settings -> General -> Project ID | `lexor-mobility-prod` |
| `FCM_SERVER_KEY` | FCM (Legacy/HTTP v1) | `notification-service` | Firebase Console -> Project Settings -> Cloud Messaging -> Server Key | `your_fcm_server_key_here` |
| `FCM_SERVICE_ACCOUNT_PATH` | FCM (HTTP v1 Admin) | `notification-service` | Firebase Console -> Service Accounts -> Generate New Private Key | `/etc/secrets/lexor-firebase-sa.json` |

---

### C. Digital Payments & Webhooks (Razorpay)

| Environment Variable | Provider | Consuming Service(s) | How to Obtain / Description | Example Value / Placeholder |
| :--- | :--- | :--- | :--- | :--- |
| `PAYMENT_RAZORPAY_KEY_ID` | Razorpay | `payment-service` | Razorpay Dashboard -> Settings -> API Keys -> Key ID (starts with `rzp_test_` or `rzp_live_`) | `rzp_test_xxxxxxxxxxxxxx` |
| `PAYMENT_RAZORPAY_KEY_SECRET` | Razorpay | `payment-service` | Razorpay Dashboard -> Settings -> API Keys -> Key Secret | `your_razorpay_key_secret_here` |
| `PAYMENT_RAZORPAY_WEBHOOK_SECRET` | Razorpay | `payment-service` | Razorpay Dashboard -> Settings -> Webhooks -> Secret used for HMAC-SHA256 signature | `your_razorpay_webhook_secret_here` |
| `PAYMENT_CURRENCY` | Common | `payment-service` | Settlement currency code | `INR` |
| `PAYMENT_SANDBOX_MODE` | Common | `payment-service` | Set to `false` in production; `true` for local test doubles | `false` |

---

### D. Real Maps & Geolocation (Google Maps Platform & Mapbox)

| Environment Variable | Provider | Consuming Service(s) | How to Obtain / Description | Example Value / Placeholder |
| :--- | :--- | :--- | :--- | :--- |
| `VITE_GOOGLE_MAPS_API_KEY` | Google Maps | `frontend` | Google Cloud Console -> APIs & Services -> Credentials -> Browser API Key | `AIzaxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` |
| `VITE_MAPBOX_ACCESS_TOKEN` | Mapbox | `frontend` | Mapbox Account Dashboard -> Access Tokens -> Public Token (starts with `pk.`) | `pk.eyJ1IjoieW91...` |
| `VITE_DEFAULT_MAP_PROVIDER` | Common | `frontend` | Active frontend map engine: `google`, `mapbox`, or `osm` | `google` |
| `VITE_NOTIFICATION_POLL_INTERVAL_MS` | Common | `frontend` | Fallback polling interval in milliseconds when SSE disconnected | `5000` |

---

## 3. Provider Setup & Portal Walkthrough

### 3.1 MSG91 (India SMS & OTP)
1. Register at [https://msg91.com](https://msg91.com).
2. Complete Indian Enterprise DLT (Distributed Ledger Technology) registration on telecom portals (e.g., Jio, Airtel, or Vilpower).
3. In MSG91 Dashboard:
   - Navigate to **Developer** -> **Auth Keys** and generate a secure API Auth Key. Set as `MSG91_AUTH_KEY`.
   - Navigate to **DLT Details** and register your Header/Sender ID (e.g., `LEXORI`). Set as `MSG91_SENDER_ID`.
   - Navigate to **OTP Templates** -> Create a template for verification:
     `"Your LEXOR ride verification code is ##OTP##. Valid for 5 minutes. Do not share with anyone."`
   - Copy the Template ID and set as `MSG91_TEMPLATE_ID`.
4. MSG91 endpoints used:
   - Live OTP Dispatch: `POST https://control.msg91.com/api/v5/otp`
   - OTP Verification / Resend: `POST https://control.msg91.com/api/v5/otp/retry`

### 3.2 Firebase Cloud Messaging (FCM Push)
1. Open the [Firebase Console](https://console.firebase.google.com/) and create or select project `lexor-mobility`.
2. Navigate to **Project Settings** -> **Service Accounts**.
3. Click **Generate New Private Key** to download the JSON service account. Place securely at `FCM_SERVICE_ACCOUNT_PATH` (e.g., `/etc/secrets/fcm-credentials.json`).
4. In Web Configuration:
   - Copy the public `apiKey`, `projectId`, `messagingSenderId`, and `appId` into `frontend/public/firebase-messaging-sw.js`.
5. Frontend Service Worker Setup:
   - Ensure `frontend/public/firebase-messaging-sw.js` is served at root scope (`/`).
   - Browser registers SW on user intent when clicking "Enable Notifications" in the LEXOR header.

### 3.3 Razorpay (Digital Payments & UPI)
1. Register at [https://dashboard.razorpay.com](https://dashboard.razorpay.com).
2. Navigate to **Settings** -> **API Keys**. Generate Test Keys for staging/development or Live Keys for production.
3. Configure `PAYMENT_RAZORPAY_KEY_ID` and `PAYMENT_RAZORPAY_KEY_SECRET` in `payment-service`.
4. Configure Webhooks:
   - Navigate to **Settings** -> **Webhooks** -> **Add New Webhook**.
   - URL: `https://api.yourdomain.com/api/v1/payments/webhooks/razorpay`
   - Secret: Generate a high-entropy secret string and set as `PAYMENT_RAZORPAY_WEBHOOK_SECRET`.
   - Active Events:
     - `order.paid`
     - `payment.authorized`
     - `payment.captured`
     - `payment.failed`
     - `refund.created`
     - `refund.processed`
5. Whitelist URL: Notice `/api/v1/payments/webhooks/**` is whitelisted in both Spring Cloud Gateway and .NET API Gateway.

### 3.4 Google Maps Platform
1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the following APIs:
   - **Maps JavaScript API** (for interactive vector tiles and driver tracking)
   - **Places API / Places API (New)** (for address autocomplete)
   - **Directions API** (for road routing geometry and ETA)
   - **Geocoding API** (for reverse coordinates lookup)
3. Navigate to **Credentials** -> **Create API Key**.
4. Apply restrictions:
   - **Application Restrictions**: Set to **HTTP referrers (websites)** and restrict to your production and staging domains (e.g., `https://*.lexor.com/*`, `http://localhost:5173/*`).
   - **API Restrictions**: Restrict key strictly to the 4 enabled APIs above.
5. Set key as `VITE_GOOGLE_MAPS_API_KEY` in frontend environment.

---

## 4. Sandbox vs. Production Configuration Matrix

| Mode | `SMS_PROVIDER` | `PAYMENT_SANDBOX_MODE` | `VITE_DEFAULT_MAP_PROVIDER` | Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Local Dev** | `MSG91_SANDBOX` | `true` | `osm` (OpenStreetMap / OSRM) | In-memory OTP simulation with masked logging, tokenized test payments, free open routing |
| **Automated Tests** | `MSG91_SANDBOX` | `true` | `osm` | Test doubles isolate all provider networks; zero external HTTP calls; zero billing |
| **Staging** | `MSG91` (Test DLT) | `true` (`rzp_test_...`) | `google` (Sandbox restricted key) | Real SMS to whitelisted test numbers, Razorpay test payment cards, Google Maps test quota |
| **Production** | `MSG91` (Live DLT) | `false` (`rzp_live_...`) | `google` (Domain-locked key) | Live SIM SMS, live UPI/Card payments, authoritative road geometry |

---

## 5. Startup Validation & Fail-Fast Behavior

Both backend stacks enforce fail-fast verification when running in production mode:
1. **Spring Boot (`ProviderStartupValidator.java`)**:
   - Executes during `CommandLineRunner` application readiness phase.
   - If `sms.enabled=true` and provider is `MSG91` (non-sandbox), validates `msg91.authKey` and `msg91.templateId` are present and not equal to template placeholders.
   - If `sms.provider=TWILIO`, validates `accountSid` starts with `AC` and is not a placeholder.
   - Halts JVM context with `IllegalStateException` on missing credentials.
2. **.NET (`Program.cs`)**:
   - Validates `NotificationSettings` and `PaymentSettings` at host startup before `app.Run()`.
   - Throws `InvalidOperationException` with explicit configuration guidance if live provider is enabled without valid credentials.

---

## 6. Troubleshooting & Diagnostics

### A. SMS Not Delivering
- **Check DLT Template Approval**: In India, telecom operators reject any SMS where the payload does not strictly match the approved DLT template structure or uses an unregistered Sender ID.
- **Check Cooldown / Rate Limiting**: The backend enforces a 60-second sliding resend cooldown. Requests within 60s return HTTP `429 Too Many Requests`.
- **Check Phone Normalization**: Phone numbers are automatically sanitized and prefixed with country code `+91`. Verify the recipient phone contains 10 digits.

### B. Payment Webhook Signature Failures (`401 / 400`)
- **Raw Request Body**: The HMAC-SHA256 signature must be computed against the exact, unparsed raw byte payload received from Razorpay. Ensure no middleware alters or trims whitespace.
- **Gateway Authentication Bypass**: Ensure the reverse proxy or API gateway does not require JWT Bearer headers for `/api/v1/payments/webhooks/**`.

### C. Map API Key Errors (`RefererNotAllowedMapError` / `ApiNotActivatedMapError`)
- Ensure your Google Cloud Console has authorized the active URL origin (e.g., `http://localhost:5173` during development).
- If Google Maps fails to load or the API key is not configured, `mapProvider.ts` automatically switches to the OpenStreetMap / OSRM routing engine with an informative disclosure badge in the UI.
