package com.hotel.dtos;

<<<<<<< HEAD
import java.time.LocalDateTime;

public record AccountSubscriptionDTO(
        Long targetHotelId,
        String source,
        boolean platformAuthoritative,
        Long planId,
        String planCode,
        String planName,
        String status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        boolean lifetime,
        String sourceReference,
        String migrationBlocker) {
=======
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountSubscriptionDTO {
    private Long id;
    private SubscriptionPlanDTO plan;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isLifetime;
    private String status;
>>>>>>> codex/ui-functional-audit-polish
}
