package com.smartbillpro.backend.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CustomerResponse {
    private Long id;
    private String customerName;
    private String mobileNumber;
    private String email;
    private String address;
    private String gstNumber;
    private BigDecimal outstandingBalance;
    private boolean active;
}
