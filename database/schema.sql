-- ============================================================================
-- SmartBill Pro - Billing, Inventory & GST Management System
-- Complete MySQL 8 Database Schema
-- ============================================================================
-- Run as: mysql -u root -p < schema.sql
-- ============================================================================

DROP DATABASE IF EXISTS smartbill_pro;
CREATE DATABASE smartbill_pro CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartbill_pro;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 1. ROLES
-- ============================================================================
CREATE TABLE roles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE,         -- ROLE_ADMIN, ROLE_MANAGER, ROLE_CASHIER
    description     VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================================
-- 2. USERS
-- ============================================================================
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(150) NOT NULL,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,               -- BCrypt hashed
    phone_number    VARCHAR(20),
    role_id         BIGINT NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    last_login_at   TIMESTAMP NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      BIGINT NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id),
    INDEX idx_users_email (email),
    INDEX idx_users_username (username)
) ENGINE=InnoDB;

-- ============================================================================
-- 3. CATEGORIES
-- ============================================================================
CREATE TABLE categories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(255),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================================
-- 4. PRODUCTS
-- ============================================================================
CREATE TABLE products (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name        VARCHAR(200) NOT NULL,
    barcode             VARCHAR(100) UNIQUE,
    qr_code_data         VARCHAR(255) UNIQUE,             -- encoded payload string for QR
    qr_code_image_path  VARCHAR(255),                     -- path/URL to generated QR image
    category_id         BIGINT,
    unit                VARCHAR(20) NOT NULL DEFAULT 'PCS', -- PCS, KG, LTR, BOX, etc.
    price               DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    gst_percentage      DECIMAL(5,2) NOT NULL DEFAULT 0.00, -- e.g. 5.00, 12.00, 18.00, 28.00
    stock_quantity      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    reorder_level       DECIMAL(12,2) DEFAULT 0.00,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT NULL,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT fk_products_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_products_barcode (barcode),
    INDEX idx_products_name (product_name)
) ENGINE=InnoDB;

-- ============================================================================
-- 5. CUSTOMERS
-- ============================================================================
CREATE TABLE customers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name       VARCHAR(150) NOT NULL,
    mobile_number       VARCHAR(20) NOT NULL,
    email               VARCHAR(150),
    address             VARCHAR(255),
    gst_number          VARCHAR(20),
    outstanding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          BIGINT NULL,
    CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_customers_mobile (mobile_number),
    INDEX idx_customers_name (customer_name)
) ENGINE=InnoDB;

-- ============================================================================
-- 6. SUPPLIERS
-- ============================================================================
CREATE TABLE suppliers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_name   VARCHAR(150) NOT NULL,
    mobile_number   VARCHAR(20),
    email           VARCHAR(150),
    address         VARCHAR(255),
    gst_number      VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================================
-- 7. GST SETTINGS
-- ============================================================================
CREATE TABLE gst_settings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    gst_slab_name       VARCHAR(50) NOT NULL,             -- e.g. "GST 18%"
    cgst_percentage     DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    sgst_percentage     DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    igst_percentage     DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    is_default          BOOLEAN DEFAULT FALSE,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Company GST profile (single-row config table)
CREATE TABLE company_settings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name        VARCHAR(200) NOT NULL,
    company_gst_number  VARCHAR(20),
    company_address     VARCHAR(255),
    company_phone       VARCHAR(20),
    company_email       VARCHAR(150),
    logo_path           VARCHAR(255),
    invoice_prefix      VARCHAR(10) DEFAULT 'INV',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================================
-- 8. INVOICES
-- ============================================================================
CREATE TABLE invoices (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_number      VARCHAR(50) NOT NULL UNIQUE,
    invoice_date        DATE NOT NULL,
    customer_id         BIGINT,
    customer_name_snap  VARCHAR(150),                     -- snapshot at time of billing
    customer_phone_snap VARCHAR(20),
    subtotal            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    cgst_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    sgst_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    igst_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    gst_total           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    grand_total         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    amount_paid         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    balance_due         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_method      ENUM('CASH','CREDIT','UPI','QR','DEBIT_CARD','CREDIT_CARD') NOT NULL DEFAULT 'CASH',
    payment_status      ENUM('PAID','PARTIAL','PENDING') NOT NULL DEFAULT 'PENDING',
    status              ENUM('DRAFT','FINALIZED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    notes               VARCHAR(500),
    created_by          BIGINT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    CONSTRAINT fk_invoices_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_invoices_number (invoice_number),
    INDEX idx_invoices_date (invoice_date),
    INDEX idx_invoices_customer (customer_id)
) ENGINE=InnoDB;

-- ============================================================================
-- 9. INVOICE ITEMS
-- ============================================================================
CREATE TABLE invoice_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id      BIGINT NOT NULL,
    product_id      BIGINT,
    product_name_snap VARCHAR(200) NOT NULL,              -- snapshot at billing time
    quantity        DECIMAL(12,2) NOT NULL,
    unit            VARCHAR(20),
    price_per_unit  DECIMAL(12,2) NOT NULL,
    gst_percentage  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    gst_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    line_total      DECIMAL(12,2) NOT NULL DEFAULT 0.00,  -- (qty * price) + gst_amount
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    INDEX idx_invoice_items_invoice (invoice_id)
) ENGINE=InnoDB;

-- ============================================================================
-- 10. PURCHASES
-- ============================================================================
CREATE TABLE purchases (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_number     VARCHAR(50) NOT NULL UNIQUE,
    supplier_id         BIGINT NOT NULL,
    purchase_date       DATE NOT NULL,
    total_cost          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes               VARCHAR(500),
    created_by          BIGINT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_purchases_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_purchases_date (purchase_date)
) ENGINE=InnoDB;

CREATE TABLE purchase_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id     BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    quantity        DECIMAL(12,2) NOT NULL,
    purchase_cost   DECIMAL(12,2) NOT NULL,               -- cost per unit
    line_total      DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    INDEX idx_purchase_items_purchase (purchase_id)
) ENGINE=InnoDB;

-- ============================================================================
-- 11. BANK ACCOUNTS
-- ============================================================================
CREATE TABLE bank_accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_name    VARCHAR(150) NOT NULL,
    account_type    ENUM('BANK','CASH','UPI') NOT NULL DEFAULT 'BANK',
    account_number  VARCHAR(50),
    ifsc_code       VARCHAR(20),
    upi_id          VARCHAR(100),
    opening_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    current_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================================
-- 12. TRANSACTIONS (ledger of income/expense across bank accounts)
-- ============================================================================
CREATE TABLE transactions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_account_id     BIGINT NOT NULL,
    transaction_type    ENUM('INCOME','EXPENSE') NOT NULL,
    amount              DECIMAL(12,2) NOT NULL,
    reference_type      VARCHAR(50),                      -- INVOICE, PURCHASE, MANUAL
    reference_id        BIGINT,                            -- invoices.id or purchases.id
    description         VARCHAR(255),
    transaction_date    DATE NOT NULL,
    created_by          BIGINT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT fk_transactions_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_transactions_account (bank_account_id),
    INDEX idx_transactions_date (transaction_date)
) ENGINE=InnoDB;

-- ============================================================================
-- 13. REPORTS (saved/generated report metadata log)
-- ============================================================================
CREATE TABLE reports (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type     ENUM('DAILY_SALES','WEEKLY_SALES','MONTHLY_SALES','PRODUCT_WISE',
                         'CUSTOMER_WISE','GST_REPORT','PROFIT_LOSS') NOT NULL,
    period_start    DATE,
    period_end      DATE,
    export_format   ENUM('PDF','EXCEL') NULL,
    file_path       VARCHAR(255),
    generated_by    BIGINT,
    generated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reports_generated_by FOREIGN KEY (generated_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_reports_type (report_type)
) ENGINE=InnoDB;

-- ============================================================================
-- 14. PAYMENTS (online payment transactions via Razorpay; also logs offline payments)
-- ============================================================================
CREATE TABLE payments (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id              BIGINT NULL,                      -- NULL until invoice is generated (online flow creates payment before invoice)
    amount                  DECIMAL(12,2) NOT NULL,
    payment_method          ENUM('CASH','CREDIT','UPI','QR','DEBIT_CARD','CREDIT_CARD') NOT NULL,
    payment_status          ENUM('PENDING','PAID','FAILED') NOT NULL DEFAULT 'PENDING',
    transaction_id          VARCHAR(100),                     -- Razorpay payment ID once captured (rzp_payment_id)
    razorpay_order_id       VARCHAR(100),
    razorpay_payment_id     VARCHAR(100),
    razorpay_signature      VARCHAR(255),                     -- stored for audit trail; verification happens server-side at capture time
    created_by              BIGINT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL,
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_payments_razorpay_order (razorpay_order_id),
    INDEX idx_payments_invoice (invoice_id)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- SEED DATA
-- ============================================================================

INSERT INTO roles (name, description) VALUES
    ('ROLE_ADMIN', 'Full system access'),
    ('ROLE_MANAGER', 'Manage products, customers, reports'),
    ('ROLE_CASHIER', 'Create bills and view products');

-- Default admin user — password is "Admin@123" (BCrypt hash below, verified to match)
-- IMPORTANT: change this password immediately after first login in production.
INSERT INTO users (full_name, username, email, password, role_id, is_active) VALUES
    ('System Admin', 'admin', 'admin@smartbillpro.com',
     '$2a$12$lrilXGByARrL2kerLuvAReUrf0NkaJjdi4bx2PZX6xFMesMOL6ltC', -- bcrypt('Admin@123'), cost 12
     1, TRUE);

INSERT INTO categories (name, description) VALUES
    ('General', 'Default category'),
    ('Groceries', 'Grocery items'),
    ('Electronics', 'Electronic items'),
    ('Stationery', 'Stationery and office supplies');

INSERT INTO gst_settings (gst_slab_name, cgst_percentage, sgst_percentage, igst_percentage, is_default) VALUES
    ('GST 0%', 0.00, 0.00, 0.00, FALSE),
    ('GST 5%', 2.50, 2.50, 5.00, FALSE),
    ('GST 12%', 6.00, 6.00, 12.00, FALSE),
    ('GST 18%', 9.00, 9.00, 18.00, TRUE),
    ('GST 28%', 14.00, 14.00, 28.00, FALSE);

INSERT INTO company_settings (company_name, company_gst_number, company_address, company_phone, company_email, invoice_prefix) VALUES
    ('Your Company Name', '29ABCDE1234F1Z5', '123 Business Street, City, State, 560001', '+91 9999999999', 'billing@yourcompany.com', 'INV');

INSERT INTO bank_accounts (account_name, account_type, opening_balance, current_balance) VALUES
    ('Cash Counter', 'CASH', 0.00, 0.00);
