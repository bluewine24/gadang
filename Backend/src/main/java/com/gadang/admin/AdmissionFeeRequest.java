package com.gadang.admin;

public record AdmissionFeeRequest(
        Long placeId,
        Integer fee,
        String feeType
) {
}
