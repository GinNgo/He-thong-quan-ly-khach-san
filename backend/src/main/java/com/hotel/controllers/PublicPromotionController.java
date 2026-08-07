package com.hotel.controllers;

import com.hotel.dtos.PublicPromotionDTO;
import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.services.PublicPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/promotions")
@RequiredArgsConstructor
public class PublicPromotionController {

    private final PublicPromotionService publicPromotionService;

    @GetMapping
    public ResponseEntity<List<PublicPromotionDTO>> list(
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(publicPromotionService.list(limit));
    }

    @GetMapping("/membership")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PromotionQuoteDTO.MemberBenefit> membership(Authentication authentication) {
        return ResponseEntity.ok(publicPromotionService.membership(authentication.getName()));
    }
}
