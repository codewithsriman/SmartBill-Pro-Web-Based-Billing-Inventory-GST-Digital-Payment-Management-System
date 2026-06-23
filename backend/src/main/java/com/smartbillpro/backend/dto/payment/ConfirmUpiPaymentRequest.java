package com.smartbillpro.backend.dto.payment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmUpiPaymentRequest {

    /** Optional — the cashier may type in a UPI reference number shown on the customer's
     *  payment app, for record-keeping. Confirmation does not depend on this being present. */
    private String transactionReference;
}
