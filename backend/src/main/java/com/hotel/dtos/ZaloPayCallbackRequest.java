package com.hotel.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZaloPayCallbackRequest {
    private String data;
    private String mac;
    private Integer type;
}
