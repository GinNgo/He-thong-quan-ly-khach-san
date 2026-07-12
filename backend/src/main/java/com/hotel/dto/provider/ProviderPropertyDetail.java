package com.hotel.dto.provider;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProviderPropertyDetail extends ProviderSearchResult {
    private String description;
    // other detailed fields
}
