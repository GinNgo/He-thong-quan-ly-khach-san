package com.hotel.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProvinceCompatibilityService {

    static final String CURRENT_PREFIX = "VN34-";

    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.location-import.current-province-resource:classpath:data/provinces-current-34.json}")
    private Resource currentProvinceResource;

    private volatile Catalog catalog;

    public ProvinceCompatibilityService(LocationRepository locationRepository, ObjectMapper objectMapper) {
        this.locationRepository = locationRepository;
        this.objectMapper = objectMapper;
    }

    public List<Location> currentProvinces() {
        return locationRepository
                .findByLocationTypeAndStatusAndSourceCodeInOrderBySortOrderAscNameViAsc(
                        "PROVINCE", "ACTIVE", currentSourceCodes());
    }

    public List<Location> wardsFor(Long provinceId) {
        Set<Long> scopeIds = provinceScopeIds(provinceId);
        if (scopeIds.isEmpty()) return List.of();
        return locationRepository.findByParentIdInAndLocationTypeAndStatusOrderByNameViAsc(
                scopeIds, "WARD", "ACTIVE");
    }

    public Set<Long> provinceScopeIds(Long provinceId) {
        if (provinceId == null) return Set.of();
        Optional<Location> selected = locationRepository.findByIdAndLocationType(provinceId, "PROVINCE");
        if (selected.isEmpty()) return Set.of(provinceId);

        ProvinceDefinition definition = definitionFor(selected.get().getSourceCode()).orElse(null);
        if (definition == null) return Set.of(provinceId);

        Set<String> sourceCodes = new LinkedHashSet<>(definition.legacyProvinceCodes());
        sourceCodes.add(definition.sourceCode());
        Set<Long> ids = new LinkedHashSet<>();
        locationRepository.findByLocationTypeAndSourceCodeIn("PROVINCE", sourceCodes).stream()
                .map(Location::getId)
                .filter(java.util.Objects::nonNull)
                .forEach(ids::add);
        ids.add(provinceId);
        return ids;
    }

    public boolean sameProvinceScope(Long leftProvinceId, Long rightProvinceId) {
        if (leftProvinceId == null || rightProvinceId == null) return leftProvinceId == rightProvinceId;
        return provinceScopeIds(leftProvinceId).contains(rightProvinceId)
                || provinceScopeIds(rightProvinceId).contains(leftProvinceId);
    }

    public Location currentProvinceFor(Location location) {
        Location province = provinceFor(location);
        if (province == null || isCurrent(province)) return province;
        ProvinceDefinition definition = definitionFor(province.getSourceCode()).orElse(null);
        if (definition == null) return province;
        return locationRepository.findByLocationTypeAndSourceCode("PROVINCE", definition.sourceCode())
                .orElse(province);
    }

    public Location currentProvinceForId(Long provinceId) {
        if (provinceId == null) return null;
        return locationRepository.findByIdAndLocationType(provinceId, "PROVINCE")
                .map(this::currentProvinceFor)
                .orElse(null);
    }

    public CatalogValidation validateCatalog() {
        Catalog loaded = catalog();
        return new CatalogValidation(loaded.byCurrentCode().size(), loaded.byLegacyCode().size());
    }

    public Set<String> currentSourceCodes() {
        return catalog().byCurrentCode().keySet();
    }

    public Optional<String> currentSourceCodeFor(String sourceCode) {
        return definitionFor(sourceCode).map(ProvinceDefinition::sourceCode);
    }

    public boolean isCurrentProvince(Location province) {
        return province != null && currentSourceCodes().contains(province.getSourceCode());
    }

    private Optional<ProvinceDefinition> definitionFor(String sourceCode) {
        if (sourceCode == null) return Optional.empty();
        Catalog loaded = catalog();
        ProvinceDefinition current = loaded.byCurrentCode().get(sourceCode);
        return current != null ? Optional.of(current) : Optional.ofNullable(loaded.byLegacyCode().get(sourceCode));
    }

    private boolean isCurrent(Location province) {
        return isCurrentProvince(province);
    }

    private Location provinceFor(Location location) {
        Location cursor = location;
        for (int depth = 0; cursor != null && depth < 3; depth++) {
            if ("PROVINCE".equals(cursor.getLocationType())) return cursor;
            cursor = cursor.getParent();
        }
        return null;
    }

    private Catalog catalog() {
        Catalog loaded = catalog;
        if (loaded != null) return loaded;
        synchronized (this) {
            if (catalog == null) catalog = loadCatalog();
            return catalog;
        }
    }

    private Catalog loadCatalog() {
        if (currentProvinceResource == null || !currentProvinceResource.exists()
                || !currentProvinceResource.isReadable()) {
            throw new IllegalStateException("Current province compatibility catalog is unavailable.");
        }
        try (InputStream input = currentProvinceResource.getInputStream()) {
            List<ProvinceDefinition> definitions = objectMapper.readValue(input, new TypeReference<>() { });
            Map<String, ProvinceDefinition> byCurrent = new LinkedHashMap<>();
            Map<String, ProvinceDefinition> byLegacy = new LinkedHashMap<>();
            for (ProvinceDefinition definition : definitions) {
                if (definition.sourceCode() == null || !definition.sourceCode().startsWith(CURRENT_PREFIX)) {
                    throw new IllegalStateException("Invalid current province code: " + definition.sourceCode());
                }
                if (byCurrent.putIfAbsent(definition.sourceCode(), definition) != null) {
                    throw new IllegalStateException("Duplicate current province code: " + definition.sourceCode());
                }
                if (definition.legacyProvinceCodes().isEmpty()) {
                    throw new IllegalStateException("Missing legacy mapping: " + definition.sourceCode());
                }
                for (String legacyCode : definition.legacyProvinceCodes()) {
                    if (byLegacy.putIfAbsent(legacyCode, definition) != null) {
                        throw new IllegalStateException("Legacy province code mapped more than once: " + legacyCode);
                    }
                }
            }
            if (byCurrent.size() != 34 || byLegacy.size() != 63) {
                throw new IllegalStateException("Province catalog must contain 34 current and 63 legacy codes.");
            }
            return new Catalog(Map.copyOf(byCurrent), Map.copyOf(byLegacy));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot parse current province compatibility catalog.", exception);
        }
    }

    private record Catalog(Map<String, ProvinceDefinition> byCurrentCode,
                           Map<String, ProvinceDefinition> byLegacyCode) { }

    public record CatalogValidation(int currentProvinceCount, int legacyProvinceCount) { }

    public record ProvinceDefinition(String sourceCode, String officialCode, String name, String codename,
                                     String divisionType, Integer phoneCode, List<String> legacyProvinceCodes) {
        public ProvinceDefinition {
            legacyProvinceCodes = legacyProvinceCodes == null
                    ? List.of() : List.copyOf(new ArrayList<>(legacyProvinceCodes));
        }
    }
}
