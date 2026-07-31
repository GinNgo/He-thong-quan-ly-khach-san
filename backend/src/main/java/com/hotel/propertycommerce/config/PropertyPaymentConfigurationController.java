package com.hotel.propertycommerce.config;

import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/properties/{propertyId}/payment-configuration")
public class PropertyPaymentConfigurationController {
    private final PropertyPaymentConfigurationService service;
    public PropertyPaymentConfigurationController(PropertyPaymentConfigurationService service) { this.service = service; }
    @GetMapping
    @Permission(function = FunctionCode.PROPERTY_PAYMENT_CONFIG, action = ActionCode.VIEW)
    public ResponseEntity<PropertyPaymentConfigurationService.ConfigurationResponse> get(@PathVariable Long propertyId) { return ResponseEntity.ok(service.get(propertyId)); }
    @PutMapping
    @Permission(function = FunctionCode.PROPERTY_PAYMENT_CONFIG, action = ActionCode.UPDATE)
    public ResponseEntity<PropertyPaymentConfigurationService.ConfigurationResponse> update(@PathVariable Long propertyId, @RequestBody PropertyPaymentConfigurationService.UpdateRequest request) { return ResponseEntity.ok(service.update(propertyId, request)); }
    @PostMapping("/validate")
    @Permission(function = FunctionCode.PROPERTY_PAYMENT_CONFIG, action = ActionCode.UPDATE)
    public ResponseEntity<PropertyPaymentConfigurationService.ReadinessResponse> validate(@PathVariable Long propertyId, @RequestBody(required = false) PropertyPaymentConfigurationService.UpdateRequest request) { return ResponseEntity.ok(service.validate(propertyId, request)); }
}
