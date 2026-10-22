# LEXOR Platform — Production Credential Rotation Runbook

## 1. Executive Summary & Compromise Context

Because historical repository commits contained default/hardcoded values (such as `lexor-default-jwt-secret-key-32-chars-long!` and `lexor-internal-service-secret-key-2026`), all such credentials committed to public/shared version control must be treated as **fully compromised**.

This runbook provides an operational, zero-downtime ordered checklist for operations and platform engineers to rotate:
1. **JWT Symmetric Signing Secret (`JWT_SECRET`)**
2. **Internal Service Authentication Key (`INTERNAL_SERVICE_API_KEY`)**
3. **MySQL Database User Password (`DB_PASSWORD`)**
4. **Third-Party Vendor Credentials (Twilio, Firebase, SMTP)**

> [!WARNING]
> Do NOT rewrite Git history or rotate production database credentials automatically without following this procedure. Never write the newly generated secret values into tracked Git files.

---

## 2. Inventory of Credentials Requiring Rotation

| Credential Name | Scope | Services Requiring Update | Rotation Severity |
| :--- | :--- | :--- | :--- |
| `JWT_SECRET` | Auth token signing & verification | Auth Service, API Gateway (Spring & .NET), All Domain Microservices | **CRITICAL** |
| `INTERNAL_SERVICE_API_KEY` | Inter-service authorization (`X-Internal-Service-Key`) | API Gateway, Admin Operations, Booking, Payment, Feedback, Notification | **HIGH** |
| `DB_PASSWORD` | MySQL data tier access | All microservices connecting to `lexor_db` | **HIGH** |
| `FCM_SERVER_KEY` | Push notification delivery | Notification Service | **MEDIUM** |
| `TWILIO_AUTH_TOKEN` | SMS OTP delivery | Notification Service | **MEDIUM** |

---

## 3. Ordered Rotation Procedures

### Procedure A: Rotating the JWT Signing Key (`JWT_SECRET`)

To rotate the symmetric JWT secret without abruptly invalidating active rider and driver mobile sessions:

#### Step 1: Generate New Cryptographic Secret
Generate a new high-entropy, 256-bit secret (never commit this value to Git):
```bash
# On secure deployment machine or secret manager
openssl rand -base64 32
```

#### Step 2: Secret Manager Injection
Store the newly generated secret in your production secrets provider (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault, or Kubernetes Secret) under key `LEXOR_JWT_SECRET_V2`.

#### Step 3: Rolling Deployment Sequence
1. **Deploy Verification Services First:**
   Update `API Gateway` (both Spring Boot and .NET) and downstream consumer microservices with dual-validation support or the updated secret.
2. **Deploy Issuing Service Last:**
   Update `Auth Service` with the new secret so newly issued access tokens use the rotated key.
3. **Session Re-Authentication:**
   Tokens signed with the old compromised secret will expire within their expiration window (`86400000` ms / 24 hours), after which clients transparently refresh or re-login.

#### Step 4: Verification
Verify token validation against the API Gateway:
```bash
# Obtain token from Auth Service using the new key
TOKEN=$(curl -s -X POST "https://api.lexor.com/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@lexor.com","password":"<SECURE_PASSWORD>"}' | jq -r '.data.accessToken')

# Test protected admin endpoint
curl -i -H "Authorization: Bearer $TOKEN" "https://api.lexor.com/api/v1/admin/operations/overview"
# Expect: HTTP/1.1 200 OK
```

---

### Procedure B: Rotating the Internal Microservice Key (`INTERNAL_SERVICE_API_KEY`)

#### Step 1: Generate New Key
```bash
openssl rand -hex 32
```

#### Step 2: Deployment Sequence
1. Update downstream microservices (`booking-service`, `payment-service`, `notification-service`, `feedback-service`) to accept the new key.
2. Update calling client services (`admin-operations-service`, `booking-service`) to transmit the new key in `X-Internal-Service-Key`.
3. Verify edge protection: Ensure direct external requests to `/api/v1/internal/**` through the API Gateway continue to return `HTTP 403 Forbidden`.

#### Step 3: Verification
Verify inter-service dispatch from Admin Operations to live snapshot:
```bash
curl -i -H "Authorization: Bearer $TOKEN" "https://api.lexor.com/api/v1/admin/operations/live"
# Expect: HTTP/1.1 200 OK with live operational metrics
```

---

### Procedure C: Rotating MySQL Database Passwords

#### Step 1: Create Secondary Database User (Zero Downtime)
In MySQL 8.0+:
```sql
-- 1. Create a temporary dual user or set dual password in MySQL 8.0
ALTER USER 'lexor_user'@'%' IDENTIFIED BY '<NEW_STRONG_PASSWORD>' RETAIN CURRENT PASSWORD;
FLUSH PRIVILEGES;
```

#### Step 2: Update Microservice Environment Variables
Set `DB_PASSWORD=<NEW_STRONG_PASSWORD>` in the orchestration environment (Kubernetes / ECS / Docker Compose) and perform a rolling restart of all microservices.

#### Step 3: Discard Old Database Password
Once all services have successfully reconnected using the new password:
```sql
ALTER USER 'lexor_user'@'%' DISCARD OLD PASSWORD;
FLUSH PRIVILEGES;
```

#### Step 4: Verification
Check health status of all database pools via `/actuator/health` or `/health`:
```bash
curl -s "http://localhost:8103/actuator/health" | jq '.components.db.status'
# Expect: "UP"
```

---

## 4. Git History Sanitization Recommendations

Removing secrets from the latest commit does **not** erase them from past Git commits. If the repository was pushed to a public remote, follow these remediation options:

### Option 1: Git-Filter-Repo (Recommended Standard)
```bash
# 1. Install git-filter-repo (Python tool)
pip install git-filter-repo

# 2. In a fresh, separate clone of the repository:
git clone https://github.com/RutujaKshetrapale/Lexor.git lexor-clean
cd lexor-clean

# 3. Create expressions file with target text to redact
cat << 'EOF' > replace.txt
lexor-default-jwt-secret-key-32-chars-long! ==> [REDACTED_HISTORICAL_SECRET]
lexor-internal-service-secret-key-2026 ==> [REDACTED_HISTORICAL_KEY]
lexor_secure_password_2026 ==> [REDACTED_HISTORICAL_PASSWORD]
EOF

# 4. Execute filter replacement
git-filter-repo --replace-text replace.txt

# 5. Coordinate force push with repository owner only after full team alignment
```

### Option 2: Automated Secret Scanning (CI/CD)
To prevent accidental future credential commits, integrate GitHub Secret Scanning or `gitleaks`:
```bash
# Install and run gitleaks locally
gitleaks detect --source . --verbose
```
Add `.github/workflows/secret-scan.yml` to automatically reject pull requests containing high-entropy keys or passwords.
