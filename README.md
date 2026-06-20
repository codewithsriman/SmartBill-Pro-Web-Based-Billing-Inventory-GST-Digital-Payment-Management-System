# SmartBill Pro — Billing, Inventory & GST Management System

A web-based billing and inventory management system built with Spring Boot 3 / Java 21 (backend) and
Bootstrap 5 / vanilla JS (frontend).

## ⚠️ Status: this is a working thin slice, not the full spec

This build covers **Authentication, Dashboard, New Bill → Scanner → Add Items → Billing → Share Invoice,
Product Management, and Customer Management** end-to-end — real Spring Boot services, a real MySQL schema,
JWT security, GST math, and PDF invoice generation, plus a fully wired frontend for all of those flows.

**Not built in this session** (UI scaffolding exists, but no backend controller/service yet):
Purchase Management, Bank Accounts, GST Report exports, Reports' Excel/PDF export, WhatsApp/Email/SMS
auto-sending (these need third-party API credentials), and Forgot Password (needs an email provider).
Each relevant frontend page says exactly what's missing and which existing controller to copy the pattern
from (e.g. `CustomerController`) to finish it.

**I was not able to compile or run this backend in my own sandbox.** My build environment only allows
network access to a small domain allowlist for security reasons, and Maven Central isn't on it — so I
could install Maven itself but not download Spring Boot/Hibernate/etc. to actually compile. I wrote the
code carefully, but you should expect to fix a handful of typos/import issues on first `mvn compile`.
**Please paste me any compiler errors and I'll fix them immediately.**

---

## Project Structure

```
smartbill-pro/
├── backend/                   # Spring Boot 3 / Java 21 REST API
│   ├── pom.xml
│   └── src/main/java/com/smartbillpro/backend/
│       ├── config/            # Security + static file serving config
│       ├── controller/        # REST controllers
│       ├── dto/                # Request/response DTOs, organized by feature
│       ├── entity/             # JPA entities
│       ├── exception/          # Custom exceptions + global handler
│       ├── repository/         # Spring Data JPA repositories
│       ├── security/           # JWT provider, filter, UserDetails
│       ├── service/            # Business logic
│       └── util/               # GST calculator, invoice number generator
├── database/
│   └── schema.sql              # Full MySQL schema + seed data
├── frontend/                   # Static HTML/CSS/JS (Bootstrap 5 + Chart.js)
│   ├── *.html                  # One file per page
│   ├── css/design-system.css
│   └── js/                     # api-client.js, app-shell.js, bill-draft.js
└── README.md                   # You are here
```

---

## Setup Instructions

### 1. Database

```bash
mysql -u root -p < database/schema.sql
```

This creates the `smartbill_pro` database, all 13+ tables, and seeds:
- 3 roles (Admin, Manager, Cashier)
- 1 admin user — **username: `admin`, password: `Admin@123`** (change immediately after first login)
- GST slabs (0/5/12/18/28%)
- A default "Cash Counter" account and company settings row

### 2. Backend

Requires **Java 21** and **Maven 3.8+**.

```bash
cd backend
# Set your real DB password and a long random JWT secret before running in anything beyond local dev:
export DB_PASSWORD=your_mysql_password
export JWT_SECRET=$(openssl rand -base64 48)

mvn clean install
mvn spring-boot:run
```

The API will be available at `http://localhost:8080/api`. Health check: `GET /api/dashboard` (requires a
JWT — log in via `/api/auth/login` first, or just open the frontend, which handles this for you).

**If `mvn clean install` fails**, it's most likely either a missing semicolon/import I couldn't catch
without a compiler, or a Lombok annotation-processing hiccup. Send me the error and I'll patch it.

### 3. Frontend

No build step — it's static HTML/CSS/JS. Two options:

**Option A — quick local preview:**
```bash
cd frontend
python3 -m http.server 5500
# open http://localhost:5500/login.html
```

**Option B — any static file server** (VS Code Live Server, nginx, etc.) pointed at the `frontend/` folder.

By default the frontend calls the backend at `http://localhost:8080/api`. To point it elsewhere, set this
before the other scripts load on each page (or edit directly in `js/api-client.js`):
```html
<script>window.SMARTBILL_API_BASE = 'https://your-api-host/api';</script>
```

Also update `app.cors.allowed-origins` in `backend/src/main/resources/application.yml` to include
whatever origin you're serving the frontend from.

### 4. Log in

Open `login.html` → sign in with `admin` / `Admin@123` → you'll land on the Dashboard.

---

## Architecture notes

- **Auth**: JWT access token (24h) + refresh token (7d), BCrypt password hashing (strength 12), stateless
  sessions, role-based access control via `@PreAuthorize` (Admin / Manager / Cashier). Self-signup always
  gets Cashier role — role escalation must be done by an Admin (this is intentional, not an oversight, to
  prevent privilege escalation via the public signup endpoint).
- **GST math**: centralized in `util/GstCalculator.java` so every caller (billing, future reports, PDF)
  rounds the same way (HALF_UP, 2 decimals). CGST/SGST is split 50/50 from the combined GST total for
  intra-state sales; IGST is left at zero by default — wire in your own state-comparison logic in
  `BillingService` if you need real inter-state invoicing.
- **Stock**: deducted on invoice finalize, restored on invoice cancel. Draft invoices don't touch stock.
- **Scanner**: uses `html5-qrcode` (camera-based, browser-only, no native app) — works on desktop, tablet,
  and mobile browsers that support `getUserMedia`. Falls back gracefully to manual search if camera access
  is denied or unavailable.
- **QR codes**: generated server-side with ZXing on product creation, saved to `./uploads/qrcodes/` and
  served via the `/uploads/**` static mapping in `WebConfig.java`.
- **PDF invoices**: generated server-side with OpenPDF in `InvoicePdfService.java`, streamed directly for
  download/print — no disk write needed for the download flow (though `generateAndSave()` is available if
  you want to persist them for the Share Invoice module).
- **Frontend state**: the in-progress bill (New Bill → Scanner → Add Items → Billing) is held in
  `sessionStorage` via `js/bill-draft.js`, so it survives page navigation but clears on tab close — by
  design, so an abandoned bill doesn't leak into the next session.

## Known gaps / next steps

1. Run `mvn clean install` and send me any compiler errors.
2. Build `PurchaseController` / `BankAccountController` / `ReportController` following the
   `CustomerController` pattern — the DB schema and frontend UI are ready for all three.
3. Wire up Apache POI (Excel) and OpenPDF (PDF) report exports in the new `ReportController`.
4. Configure an email provider (SMTP/SendGrid) to enable Forgot Password and automated invoice emailing.
5. Configure Twilio (or similar) for automated SMS, and the WhatsApp Business API for automated WhatsApp
   sending — both currently open the user's own app with a prefilled message instead.
6. Consider adding Flyway/Liquibase for schema migrations instead of a single `schema.sql` once this goes
   to a real team environment.
