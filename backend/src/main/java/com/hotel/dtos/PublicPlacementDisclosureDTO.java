package com.hotel.dtos;

import java.time.Instant;

/** Public, non-financial disclosure for a governed sponsored placement. */
public record PublicPlacementDisclosureDTO(
        Long placementId,
        String placementKind,
        String disclosureVi,
        String disclosureEn,
        Instant endsAt) {
}
