package com.hotel.controllers;

import com.hotel.dtos.home.HomeSpotlightDTO;
import com.hotel.services.HomeSpotlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/home")
@RequiredArgsConstructor
public class PublicHomeSpotlightController {

    private final HomeSpotlightService spotlightService;

    @GetMapping("/spotlights")
    public ResponseEntity<List<HomeSpotlightDTO>> spotlights(
            @RequestParam(defaultValue = "6") int limit,
            @RequestParam(defaultValue = "vi") String locale) {
        return ResponseEntity.ok(spotlightService.publicSpotlights(limit, locale));
    }
}

