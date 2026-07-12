package com.hotel.services;

import java.util.Optional;

public interface GeocodingProvider {
    Optional<GeoCoordinate> geocode(String address);
}
