package com.hotel.dtos;

import java.math.BigDecimal;

public record DepositPolicySnapshotDTO(
        Long configurationId,
        Long configurationVersion,
        String policyType,
        BigDecimal policyValue,
        BigDecimal bookingTotal,
        BigDecimal requiredDeposit,
        String currency) {
}
