package com.hotel.dtos;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class BulkRoomRequest {
    @NotNull private Long hotelId;
    @NotNull private Long roomTypeId;
    @NotNull @Min(0) @Max(999999) private Integer fromNumber;
    @NotNull @Min(0) @Max(999999) private Integer toNumber;
    @NotNull @Min(-10) @Max(500) private Integer floor;
    @Size(max = 20) @Pattern(regexp = "[\\p{L}\\p{N}_-]*") private String prefix;
    @Pattern(regexp = "AVAILABLE") private String status = "AVAILABLE";
}
