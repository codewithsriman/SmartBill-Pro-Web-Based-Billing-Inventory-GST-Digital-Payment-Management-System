-- ============================================================================
-- SmartBill Pro — Migration: Remove Razorpay, Add Shop QR Payment System
-- Run against your existing smartbill_pro database.
-- ============================================================================

USE smartbill_pro;

-- Step 1: Recreate the payments table (clean, no Razorpay columns)
-- We drop and recreate since MySQL doesn't support DROP COLUMN on enum-linked cols easily.
-- If you have existing payment data you want to preserve, back it up first.
DROP TABLE IF EXISTS payments;

CREATE TABLE payments (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id              BIGINT NULL,
    amount                  DECIMAL(12,2) NOT NULL,
    payment_method          ENUM('CASH','CREDIT','UPI') NOT NULL,
    payment_status          ENUM('PENDING','PAID','FAILED') NOT NULL DEFAULT 'PENDING',
    transaction_reference   VARCHAR(100),
    created_by              BIGINT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL,
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_payments_invoice (invoice_id)
) ENGINE=InnoDB;

-- Step 2: Update invoices table's payment_method enum
ALTER TABLE invoices
    MODIFY COLUMN payment_method ENUM('CASH','CREDIT','UPI') NOT NULL DEFAULT 'CASH';

-- Step 3: Create shop_settings table
CREATE TABLE IF NOT EXISTS shop_settings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_name       VARCHAR(200) NOT NULL,
    shop_address    VARCHAR(255),
    mobile_number   VARCHAR(20),
    gst_number      VARCHAR(20),
    upi_id          VARCHAR(100),
    qr_code_image   VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Step 4: Seed a default shop_settings row if none exists
INSERT INTO shop_settings (shop_name, shop_address, mobile_number, gst_number, upi_id)
SELECT 'Your Shop Name', '123 Business Street, City, State, 560001',
       '+91 9999999999', '29ABCDE1234F1Z5', 'yourshop@upi'
WHERE NOT EXISTS (SELECT 1 FROM shop_settings LIMIT 1);

-- Done. Verify:
SHOW TABLES LIKE 'shop_settings';
DESCRIBE payments;
