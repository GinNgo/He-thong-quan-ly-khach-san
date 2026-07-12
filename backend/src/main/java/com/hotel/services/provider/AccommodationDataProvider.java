package com.hotel.services.provider;

import com.hotel.dto.provider.AccommodationProviderSearchRequest;
import com.hotel.dto.provider.ProviderPhoto;
import com.hotel.dto.provider.ProviderPropertyDetail;
import com.hotel.dto.provider.ProviderSearchResult;

import java.util.List;

public interface AccommodationDataProvider {

    String getProviderName();

    List<ProviderSearchResult> search(AccommodationProviderSearchRequest request);

    ProviderPropertyDetail getDetail(String externalId);

    List<ProviderPhoto> getPhotos(String externalId);
}
