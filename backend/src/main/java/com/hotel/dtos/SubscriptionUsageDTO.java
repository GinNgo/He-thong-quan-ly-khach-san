package com.hotel.dtos;

<<<<<<< HEAD
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SubscriptionUsageDTO(
        Long targetHotelId,
        String source,
        boolean platformAuthoritative,
        String planCode,
        String subscriptionStatus,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        boolean lifetime,
        Map<String, Integer> limits,
        Map<String, Long> usage,
        List<SubscriptionEntitlementDTO> features,
        String migrationBlocker) {
=======
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUsageDTO {
    private String planCode;
    private String subscriptionStatus;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private boolean lifetime;
    private Map<String, Integer> limits = new LinkedHashMap<>();
    private Map<String, Long> usage = new LinkedHashMap<>();
    private List<SubscriptionEntitlementDTO> features = List.of();
>>>>>>> codex/ui-functional-audit-polish
}
