package com.hotel.dtos;

<<<<<<< HEAD
public record SubscriptionFeatureDTO(
        String code,
        String nameVi,
        String nameEn,
        String valueType,
        Integer limit) {
=======
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionFeatureDTO {
    private String code;
    private String nameVi;
    private String nameEn;
    private String valueType;
    private Integer limit;
>>>>>>> codex/ui-functional-audit-polish
}
