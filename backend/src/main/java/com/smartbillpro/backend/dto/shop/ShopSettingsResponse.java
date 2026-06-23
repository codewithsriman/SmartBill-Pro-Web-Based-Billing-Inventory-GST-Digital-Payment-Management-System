package com.smartbillpro.backend.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ShopSettingsResponse {
    private Long id;
    private String shopName;
    private String shopAddress;
    private String mobileNumber;
    private String gstNumber;
    private String upiId;
    private String qrCodeImageUrl; // full /uploads/... URL, ready to use in an <img> src
}
