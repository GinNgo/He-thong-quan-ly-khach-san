package com.hotel.repositories;

import com.hotel.entities.PropertyExternalPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyExternalPhotoRepository extends JpaRepository<PropertyExternalPhoto, Long> {
    List<PropertyExternalPhoto> findByPropertyId(Long propertyId);
}
