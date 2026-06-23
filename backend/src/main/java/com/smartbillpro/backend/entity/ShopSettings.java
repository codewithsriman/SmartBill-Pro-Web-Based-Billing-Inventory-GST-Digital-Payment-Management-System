package com.smartbillpro.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name", nullable = false, length = 200)
    private String shopName;

    @Column(name = "shop_address", length = 255)
    private String shopAddress;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "qr_code_image", length = 255)
    private String qrCodeImage; // relative path, served via /uploads/**

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
