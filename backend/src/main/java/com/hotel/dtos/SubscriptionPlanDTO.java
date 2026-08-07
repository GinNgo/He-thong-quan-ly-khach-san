package com.hotel.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    private Long id;
    private String code;
    private String nameVi;
    private String nameEn;
    private String billingType;
    private BigDecimal price;
    private String currency = "VND";
    private Boolean isLifetime;
    private String status;
    private List<SubscriptionFeatureDTO> features = new ArrayList<>();
}
