package com.hotel.dto.provider;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProviderPhoto {
    private String externalPhotoId;
    private String photoReference;
    private String displayUrl;
    private String attributionText;
    private String attributionUrl;
    private LocalDateTime expiresAt;
}
