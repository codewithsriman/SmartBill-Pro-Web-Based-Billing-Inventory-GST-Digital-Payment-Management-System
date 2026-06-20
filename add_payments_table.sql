USE smartbill_pro;

CREATE TABLE payments (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id              BIGINT NULL,
    amount                  DECIMAL(12,2) NOT NULL,
    payment_method          ENUM('CASH','CREDIT','UPI','QR','DEBIT_CARD','CREDIT_CARD') NOT NULL,
    payment_status          ENUM('PENDING','PAID','FAILED') NOT NULL DEFAULT 'PENDING',
    transaction_id          VARCHAR(100),
    razorpay_order_id       VARCHAR(100),
    razorpay_payment_id     VARCHAR(100),
    razorpay_signature      VARCHAR(255),
    created_by              BIGINT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL,
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_payments_razorpay_order (razorpay_order_id),
    INDEX idx_payments_invoice (invoice_id)
) ENGINE=InnoDB;
