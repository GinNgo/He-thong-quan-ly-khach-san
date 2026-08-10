package com.hotel.dtos;

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
}
