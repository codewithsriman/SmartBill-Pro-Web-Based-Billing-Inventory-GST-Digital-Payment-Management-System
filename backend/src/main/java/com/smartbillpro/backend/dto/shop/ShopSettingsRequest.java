package com.smartbillpro.backend.dto.shop;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopSettingsRequest {

    @NotBlank(message = "Shop name is required")
    private String shopName;

    private String shopAddress;
    private String mobileNumber;
    private String gstNumber;

    @NotBlank(message = "UPI ID is required")
    private String upiId;
}
