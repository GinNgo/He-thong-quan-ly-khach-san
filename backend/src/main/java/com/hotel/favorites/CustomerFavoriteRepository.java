package com.hotel.favorites;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerFavoriteRepository extends JpaRepository<CustomerFavorite, Long> {

    @Query("""
            select favorite from CustomerFavorite favorite
            join fetch favorite.hotel hotel
            where favorite.customer.id = :customerId
              and hotel.approvalStatus = 'APPROVED'
              and hotel.operationStatus = 'ACTIVE'
            order by favorite.createdAt desc, favorite.id desc
            """)
    List<CustomerFavorite> findPublicFavorites(@Param("customerId") Long customerId);

    @Query("""
            select favorite from CustomerFavorite favorite
            join fetch favorite.hotel
            where favorite.customer.id = :customerId
              and favorite.hotel.id = :hotelId
            """)
    Optional<CustomerFavorite> findOwnedFavorite(
            @Param("customerId") Long customerId,
            @Param("hotelId") Long hotelId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CustomerFavorite favorite
            where favorite.customer.id = :customerId
              and favorite.hotel.id = :hotelId
            """)
    int deleteOwnedFavorite(
            @Param("customerId") Long customerId,
            @Param("hotelId") Long hotelId);
}
