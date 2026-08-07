package com.hotel.controllers;

import com.hotel.dtos.PromotionCampaignDTO;
import com.hotel.dtos.PromotionCampaignRequest;
import com.hotel.services.PromotionCampaignManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','PROPERTY_OWNER','HOTEL_ADMIN','HOTEL_MANAGER')")
public class PromotionController {

    private final PromotionCampaignManagementService campaignService;

    @GetMapping
    public ResponseEntity<List<PromotionCampaignDTO>> list(@RequestParam(required = false) Long hotelId) {
        return ResponseEntity.ok(campaignService.list(hotelId));
    }

    @PostMapping
    public ResponseEntity<PromotionCampaignDTO> create(@Valid @RequestBody PromotionCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionCampaignDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody PromotionCampaignRequest request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PromotionCampaignDTO> activate(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.activate(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<PromotionCampaignDTO> pause(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.pause(id));
    }
}

