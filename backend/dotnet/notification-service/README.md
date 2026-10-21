# LEXOR Notification Service (ASP.NET Core .NET 9)
## Event-Driven, Multi-Channel Mobility Notification Architecture

### 1. Architecture Overview
The LEXOR .NET Notification Service coordinates rider, driver, and administrative communications across the entire ride lifecycle. It supports four delivery channels:
- **Persistent In-App Notifications**: Backed by MySQL with read/unread tracking, category filtering, and paginated user inboxes.
- **Push Notifications (FCM)**: Platform-aware dispatch to Android, iOS, and Web clients with device token lifecycle management and automatic token invalidation.
- **SMS Notifications**: High-priority alerts and transactional OTP delivery with destination rate limiting and masking.
- **Transactional Email**: Formatted HTML and plain-text booking receipts, payment confirmations, and account security notices.
- **Real-Time Delivery**: Server-Sent Events (SSE) streaming for connected client apps (`/api/v1/notifications/me/stream`).

### 2. Port & Configuration
- **Port**: `8100` (Configurable via `PORT` or `ASPNETCORE_URLS`)
- **Database**: MySQL `lexor_db`
- **Internal Service Key**: `X-Internal-Service-Key: lexor-internal-service-secret-key-2026`

### 3. Key Endpoints
| Verb | Route | Description | Auth |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/notifications/me` | Paginated in-app inbox | Rider/Driver JWT |
| `GET` | `/api/v1/notifications/me/unread-count` | Unread notifications count | Rider/Driver JWT |
| `GET` | `/api/v1/notifications/me/{uuid}` | Single notification details | Owner JWT |
| `PATCH` | `/api/v1/notifications/me/{uuid}/read` | Mark single notification read | Owner JWT |
| `PATCH` | `/api/v1/notifications/me/read-all` | Mark all notifications read | Owner JWT |
| `GET` | `/api/v1/notifications/me/preferences` | Retrieve notification preferences | Rider/Driver JWT |
| `PUT` | `/api/v1/notifications/me/preferences` | Update preferences (security mandatory) | Rider/Driver JWT |
| `GET` | `/api/v1/notifications/me/stream` | Server-Sent Events (SSE) live stream | Rider/Driver JWT |
| `POST` | `/api/v1/notifications/devices` | Register/refresh FCM device token | Authenticated |
| `GET` | `/api/v1/notifications/devices` | List active device registrations | Authenticated |
| `DELETE` | `/api/v1/notifications/devices/{uuid}` | Deactivate device on logout | Authenticated |
| `POST` | `/api/v1/internal/notifications/events` | Ingest upstream business events (idempotent) | Internal Key |
| `POST` | `/api/v1/internal/notifications/dispatch` | Direct transactional dispatch | Internal Key |
| `POST` | `/api/v1/internal/notifications/otp` | Deliver short-lived OTP (rate limited) | Internal Key |
| `GET` | `/api/v1/admin/notifications` | Admin global notification inquiry | Admin JWT |
| `GET` | `/api/v1/admin/notifications/{uuid}/audit` | Delivery attempts and provider logs | Admin JWT |
| `POST` | `/api/v1/admin/notifications/{uuid}/retry` | Retry failed deliveries | Admin JWT |
| `GET` | `/api/v1/admin/notifications/metrics` | Platform notification metrics | Admin JWT |
| `GET` | `/health`, `/health/ready` | Liveness and readiness probes | Public |

### 4. Running Tests
```bash
cd backend/dotnet/notification-service
dotnet test
```
