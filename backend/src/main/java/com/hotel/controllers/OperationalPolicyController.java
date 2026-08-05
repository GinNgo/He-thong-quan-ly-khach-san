package com.hotel.controllers;

import com.hotel.dtos.OperationalPolicyDTO;
import com.hotel.dtos.OperationalPolicyRequest;
import com.hotel.dtos.PublicOperationalPolicyDTO;
import com.hotel.services.OperationalPolicyService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class OperationalPolicyController {

    private final OperationalPolicyService service;

    @GetMapping("/public/{hotelId}/policies/current")
    @PreAuthorize("permitAll()")
    public PublicOperationalPolicyDTO current(
            @PathVariable Long hotelId,
            @RequestParam(defaultValue = "vi") String locale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate stayDate) {
        return service.currentPublic(hotelId, locale, stayDate);
    }

    @GetMapping("/{hotelId}/policies")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','HOTEL_ADMIN','SUPER_ADMIN')")
    public List<OperationalPolicyDTO> list(@PathVariable Long hotelId) {
        return service.list(hotelId);
    }

    @PostMapping("/{hotelId}/policies")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','HOTEL_ADMIN','SUPER_ADMIN')")
    public OperationalPolicyDTO create(@PathVariable Long hotelId, @Valid @RequestBody OperationalPolicyRequest request) {
        return service.createDraft(hotelId, request);
    }

    @PutMapping("/{hotelId}/policies/{policyId}")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','HOTEL_ADMIN','SUPER_ADMIN')")
    public OperationalPolicyDTO update(@PathVariable Long hotelId, @PathVariable Long policyId,
                                       @Valid @RequestBody OperationalPolicyRequest request) {
        return service.updateDraft(hotelId, policyId, request);
    }

    @PostMapping("/{hotelId}/policies/{policyId}/publish")
    @PreAuthorize("hasAnyAuthority('PROPERTY_OWNER','HOTEL_MANAGER','HOTEL_ADMIN','SUPER_ADMIN')")
    public OperationalPolicyDTO publish(@PathVariable Long hotelId, @PathVariable Long policyId) {
        return service.publish(hotelId, policyId);
    }
}
