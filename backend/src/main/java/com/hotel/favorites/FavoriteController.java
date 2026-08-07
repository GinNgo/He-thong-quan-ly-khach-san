package com.hotel.favorites;

import com.hotel.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CUSTOMER')")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<FavoritePropertyResponse>> list(
            @AuthenticationPrincipal CustomUserDetails customer) {
        return ResponseEntity.ok(favoriteService.listForCustomer(customer.getUserId()));
    }

    @PostMapping("/{hotelId}")
    public ResponseEntity<FavoritePropertyResponse> add(
            @PathVariable Long hotelId,
            @AuthenticationPrincipal CustomUserDetails customer) {
        return ResponseEntity.ok(favoriteService.addForCustomer(customer.getUserId(), hotelId));
    }

    @DeleteMapping("/{hotelId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long hotelId,
            @AuthenticationPrincipal CustomUserDetails customer) {
        favoriteService.removeForCustomer(customer.getUserId(), hotelId);
        return ResponseEntity.noContent().build();
    }
}
