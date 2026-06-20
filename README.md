# SmartBill Pro — Billing, Inventory & GST Management System

A billing, inventory, and GST management system for Indian small businesses. Spring Boot 3 / Java 21
backend, MySQL database, JWT authentication, Bootstrap 5 + vanilla JS frontend, QR/barcode scanning,
PDF invoice generation, and Razorpay online payments.

**This has been built incrementally and confirmed running end-to-end** (login → dashboard → billing →
Razorpay payment) on a real Windows machine. The setup steps below are the actual commands that worked,
not theoretical ones — including the Windows/PowerShell-specific gotchas that came up along the way.

---

## What's actually working

- **Auth**: signup, login, JWT access + refresh tokens, BCrypt password hashing, role-based access
  (Admin / Manager / Cashier)
- **Dashboard**: live stat cards, Chart.js daily/weekly/monthly sales charts, transaction search
- **Billing flow**: New Bill → Scanner (camera-based barcode/QR via `html5-qrcode`) → Add Items →
  Billing (GST calculation, discount, payment) → Share Invoice (PDF download/print)
- **Payments**: Cash, Credit, and Razorpay online payments (UPI/Cards/Netbanking via Checkout, plus a
  standalone scannable QR code option) — see [Payments](#payments-razorpay) below for mode-specific notes
- **Products**: full CRUD, auto-generated barcodes, auto-generated QR codes (ZXing), low-stock flagging
- **Customers**: full CRUD, outstanding balance tracking
- **PDF invoices**: generated server-side with OpenPDF, downloadable/printable

## What's scaffolded but not wired up

Purchases, Bank Accounts, GST report exports, and Reports' Excel/PDF export have frontend pages but no
backend controller yet. WhatsApp/Email/SMS auto-sending and Forgot Password need third-party
credentials (Twilio, an email provider) you'd supply separately — the UI is ready to call those once a
controller exists. Each relevant page in the app says exactly what's missing.

---

## Project Structure

```
smartbill-pro/
├── backend/                          # Spring Boot 3 / Java 21 REST API
│   ├── pom.xml
│   └── src/main/java/com/smartbillpro/backend/
│       ├── config/                   # Security, static file serving
│       ├── controller/               # REST controllers
│       ├── dto/                      # Request/response DTOs by feature
│       ├── entity/                   # JPA entities
│       ├── exception/                # Custom exceptions + global handler
│       ├── repository/               # Spring Data JPA repositories
│       ├── security/                 # JWT provider, filter, UserDetails
│       ├── service/                  # Business logic (incl. Razorpay)
│       └── util/                     # GST calculator, invoice number generator
├── database/
│   └── schema.sql                    # Full MySQL schema + seed data
├── frontend/                         # Static HTML/CSS/JS
│   ├── *.html                        # One file per page
│   ├── css/design-system.css
│   └── js/                           # api-client.js, app-shell.js, bill-draft.js
└── README.md
```

---

## Prerequisites

- **Java 21** (Temurin recommended) — if you also have a newer JDK installed (e.g. Java 25), see the
  [JAVA_HOME note](#java_home-if-you-have-multiple-jdks-installed) below, it matters.
- **Maven 3.8+**
- **MySQL 8 or 9** running locally (this was built and tested against MySQL Server 9.7 on Windows)
- A static file server for the frontend — Python's built-in one is easiest if available

Check what you have:
```powershell
java -version
mvn -version
```

---

## Setup — step by step

### 1. Database

Run the schema (adjust the MySQL path if yours differs):

```powershell
cd smartbill-pro
Get-Content database\schema.sql | & "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe" -u root -p
```

> **Windows/PowerShell note:** PowerShell does **not** support `<` for input redirection like bash does.
> Always pipe through `Get-Content file.sql | mysql.exe ...` instead of `mysql.exe ... < file.sql`.

This creates the `smartbill_pro` database, ~16 tables, and seeds:
- 3 roles (Admin, Manager, Cashier)
- 1 admin user — **username: `admin`, password: `Admin@123`**
- GST slabs, a default Cash Counter account, company settings

Verify it landed:
```powershell
& "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe" -u root -p -e "USE smartbill_pro; SHOW TABLES;"
```

### 2. `JAVA_HOME` (if you have multiple JDKs installed)

If `mvn -version` reports a different Java version than `java -version` does, Maven is picking up the
wrong JDK via `JAVA_HOME`. Fix it for the current terminal session:

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory   # find your JDK 21 folder name
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"   # adjust to your actual path
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
mvn -version   # confirm it now reports Java 21
```

This only lasts for the current terminal window — repeat it (or set `JAVA_HOME` permanently via System
Properties) each time you open a fresh terminal to build/run the backend.

### 3. Backend environment variables

In the same terminal:

```powershell
cd backend
$env:DB_PASSWORD = "your_mysql_root_password"
$env:JWT_SECRET = -join ((48..57)+(65..90)+(97..122)|Get-Random -Count 64|%{[char]$_})
$env:RAZORPAY_KEY_ID = "rzp_test_..."        # type your real key directly, see note below
$env:RAZORPAY_KEY_SECRET = "..."             # same
```

> **Never share or paste real API keys/secrets into chat, files, or version control.** Type them
> directly into your terminal. If a secret is ever pasted somewhere it shouldn't be, treat it as
> compromised and regenerate it from the Razorpay Dashboard immediately.

Razorpay keys are optional if you only plan to test Cash/Credit payments — the app falls back to a
placeholder and only errors out when someone actually tries to use an online payment method.

### 4. Build and run the backend

```powershell
mvn clean install
mvn spring-boot:run
```

Success looks like:
```
Tomcat started on port 8080 (http) with context path '/api'
Started SmartBillProApplication in X.XXX seconds
```

Leave this terminal running. Common failure points and fixes:

| Error | Cause | Fix |
|---|---|---|
| `Port 8080 was already in use` | A previous run didn't shut down cleanly | `Get-NetTCPConnection -LocalPort 8080 -State Listen`, then `Stop-Process -Id <PID> -Force` |
| `Schema-validation: missing table [X]` | Database is out of sync with the entity classes | Re-check you ran the latest `schema.sql`, or run the specific `ALTER TABLE`/`CREATE TABLE` for what's missing |
| Compile errors mentioning `cannot find symbol` for getters/setters/builders on your own classes | Usually a Lombok annotation-processing hiccup after a partial file edit | Re-check the file wasn't accidentally truncated; ask for the affected file in full |
| `reference to Font is ambiguous` (or similar dual-import error) | Wildcard imports from two packages with a same-named class | Replace the wildcard import with an explicit one for just the class you need |

### 5. Frontend

In a **separate** terminal (keep the backend running):

```powershell
cd smartbill-pro\frontend
python -m http.server 5500
```

Open `http://localhost:5500/login.html` and sign in with `admin` / `Admin@123`.

By default the frontend calls the backend at `http://localhost:8080/api`. To point elsewhere, set
`window.SMARTBILL_API_BASE` before `js/api-client.js` loads, and update
`app.cors.allowed-origins` in `backend/src/main/resources/application.yml` to match.

---

## Payments (Razorpay)

Cash and Credit work with no external setup. Online methods (UPI, Cards, Netbanking, QR) go through
Razorpay and have mode-specific behavior worth understanding before you test:

| Mode | Checkout (Cards/Netbanking/Wallets) | UPI Intent / QR in Checkout | Standalone QR Code (own API) |
|---|---|---|---|
| **Test** (`rzp_test_...`) | ✅ Works with [Razorpay's documented test cards](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/) | ❌ Not rendered — Razorpay restricts this to live mode | ✅ Real QR image renders; scanning won't complete a real payment |
| **Live** (`rzp_live_...`) | ✅ Real money moves | ✅ Fully functional | ✅ Fully functional |

**On the Billing page**, selecting "QR Payment" shows a real scannable QR image generated via
Razorpay's QR Code API, with a **Check Status** button to poll for payment (this app runs on
`localhost`, which can't receive Razorpay's webhook push, so status is checked on-demand instead of
pushed automatically).

If QR code creation fails with an authorization-style error, the **QR Codes feature may need to be
enabled on your Razorpay account** — this is a separate toggle from your API keys, requested via the
Razorpay Dashboard or Support.

The invoice's `amountPaid` and `PAID` status for any online payment always comes from the
server-verified `payments` table record, never from client input — this is intentional, to prevent a
tampered request from claiming a payment succeeded when it didn't.

---

## Architecture notes

- **GST math** is centralized in `util/GstCalculator.java` (HALF_UP rounding, 2 decimals) so billing,
  PDFs, and any future reports stay consistent. CGST/SGST is split 50/50 from the combined GST total for
  intra-state sales; IGST defaults to zero — add real state-comparison logic in `BillingService` if you
  need inter-state invoicing.
- **Stock** is deducted on invoice finalize, restored on cancel. Drafts don't touch inventory.
- **Self-signup always gets the Cashier role** — this is intentional, not an oversight, to prevent
  privilege escalation via the public signup endpoint. Promote users to Manager/Admin manually.
- **Scanner** uses `html5-qrcode` (browser camera, no native app needed), falls back gracefully to
  manual product search if camera access is denied or unavailable.
- **In-progress bill state** (New Bill → Scanner → Add Items → Billing) lives in `sessionStorage` via
  `js/bill-draft.js` — survives page navigation, clears on tab close, by design.

---

## Known gaps / next steps

1. Build `PurchaseController`, `BankAccountController`, and `ReportController` following the
   `CustomerController` pattern — schema and frontend are ready for all three.
2. Wire up Apache POI (Excel) and OpenPDF (PDF) exports in the new `ReportController`.
3. Configure an email provider to enable Forgot Password and automated invoice emailing.
4. Configure Twilio (SMS) and the WhatsApp Business API for automated sending — currently these open
   the user's own SMS/WhatsApp/email app with a prefilled message instead of sending automatically.
5. For production: move off a single `schema.sql` to Flyway/Liquibase migrations, set up real webhook
   handling for Razorpay (requires a public URL — a tunnel like ngrok works for staging), and rotate the
   JWT secret and DB credentials out of plain environment variables into a proper secrets manager.
