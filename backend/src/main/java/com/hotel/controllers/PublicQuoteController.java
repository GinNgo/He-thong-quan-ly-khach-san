package com.hotel.controllers;

import com.hotel.dtos.PromotionQuoteDTO;
import com.hotel.dtos.PromotionQuoteRequest;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PromotionQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/quotes")
@RequiredArgsConstructor
public class PublicQuoteController {

    private final PromotionQuoteService promotionQuoteService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<PromotionQuoteDTO> quote(
            @Valid @RequestBody PromotionQuoteRequest request,
            Authentication authentication) {
        Long customerId = resolveCustomerId(authentication);
        return ResponseEntity.ok(promotionQuoteService.quote(request, customerId));
    }

    private Long resolveCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }
}
