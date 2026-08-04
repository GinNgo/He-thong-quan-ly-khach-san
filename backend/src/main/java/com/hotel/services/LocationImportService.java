package com.hotel.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Location;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.LocationRepository;
import com.hotel.util.VietnameseTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class LocationImportService {

    private static final Logger log = LoggerFactory.getLogger(LocationImportService.class);

    private final LocationRepository locationRepository;
    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.location-import.enabled:false}")
    private boolean importEnabled;

    @Value("${app.location-import.file-path:}")
    private String jsonFilePath;

    @Value("${app.location-import.resource:classpath:data/locations.json}")
    private Resource locationResource;

    @Value("${app.location-import.current-province-resource:classpath:data/provinces-current-34.json}")
    private Resource currentProvinceResource;

    @Value("${app.location-import.landmark-resource:classpath:data/landmarks.json}")
    private Resource landmarkResource;

    @Value("${app.location-import.cleanup-obsolete:false}")
    private boolean cleanupObsolete;

    public LocationImportService(LocationRepository locationRepository, HotelRepository hotelRepository, ObjectMapper objectMapper) {
        this.locationRepository = locationRepository;
        this.hotelRepository = hotelRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    @Transactional
    public void importDataOnStartup() {
        if (!importEnabled) {
            log.info("Location import is disabled.");
            return;
        }
        ImportReport report = importData(cleanupObsolete);
        log.info("LOCATION_IMPORT added={} updated={} skipped={} removed={} errors={} provinces={} wards={} landmarks={}",
                report.added(), report.updated(), report.skipped(), report.removed(), report.errors(),
                report.provinces(), report.wards(), report.landmarks());
    }

    @Transactional
    public ImportReport importData(boolean removeObsolete) {
        MutableReport report = new MutableReport();
        Set<String> canonicalKeys = new HashSet<>();
        Map<String, Location> existingByKey = new HashMap<>();
        for (Location location : locationRepository.findAll()) {
            existingByKey.put(key(location.getLocationType(), location.getSourceCode()), location);
        }

        try (InputStream source = openLocationSource(); InputStream input = openUtf8BomSafe(source)) {
            List<Map<String, Object>> provinces = objectMapper.readValue(input, new TypeReference<>() { });
            for (Map<String, Object> provinceData : provinces) {
                String sourceCode = requiredText(provinceData, "code");
                String name = requiredVietnameseName(provinceData);
                Location province = upsert(existingByKey, "PROVINCE", sourceCode, "P-" + sourceCode, name, null, null, name, report);
                canonicalKeys.add(key("PROVINCE", sourceCode));
                report.provinces++;

                for (Map<String, Object> district : childList(provinceData, "districts")) {
                    String legacyParentName = requiredVietnameseName(district);
                    for (Map<String, Object> wardData : childList(district, "wards")) {
                        String wardCode = requiredText(wardData, "code");
                        String wardName = requiredVietnameseName(wardData);
                        upsert(existingByKey, "WARD", wardCode, "W-" + wardCode, wardName, province, legacyParentName,
                                wardName + ", " + name, report);
                        canonicalKeys.add(key("WARD", wardCode));
                        report.wards++;
                    }
                }
            }

            Map<String, Set<String>> currentProvinceAliases = importCurrentProvinces(
                    existingByKey, canonicalKeys, report);
            importLandmarks(existingByKey, currentProvinceAliases, canonicalKeys, report);

            if (removeObsolete) {
                report.removed = removeObsoleteRows(canonicalKeys);
            }
            locationRepository.flush();
            return report.toImmutable();
        } catch (Exception exception) {
            log.error("Location import failed", exception);
            throw new IllegalStateException("Cannot import the packaged UTF-8 location catalog.", exception);
        }
    }

    private Map<String, Set<String>> importCurrentProvinces(Map<String, Location> existingByKey,
                                                             Set<String> canonicalKeys,
                                                             MutableReport report) throws IOException {
        Resource resource = requireReadable(currentProvinceResource, "current province");
        Map<String, Set<String>> aliasesByCurrentCode = new HashMap<>();
        Set<String> allLegacyCodes = new HashSet<>();
        try (InputStream input = openUtf8BomSafe(resource.getInputStream())) {
            List<Map<String, Object>> provinces = objectMapper.readValue(input, new TypeReference<>() { });
            if (provinces.size() != 34) {
                throw new IllegalArgumentException("Current province catalog must contain exactly 34 rows.");
            }
            for (Map<String, Object> provinceData : provinces) {
                String sourceCode = requiredText(provinceData, "sourceCode");
                if (!sourceCode.startsWith(ProvinceCompatibilityService.CURRENT_PREFIX)) {
                    throw new IllegalArgumentException("Invalid current province code: " + sourceCode);
                }
                String officialCode = requiredText(provinceData, "officialCode");
                Set<String> legacyCodes = new java.util.LinkedHashSet<>(stringList(
                        provinceData, "legacyProvinceCodes"));
                if (legacyCodes.isEmpty() || aliasesByCurrentCode.putIfAbsent(sourceCode, legacyCodes) != null) {
                    throw new IllegalArgumentException("Invalid or duplicate current province mapping: " + sourceCode);
                }
                for (String legacyCode : legacyCodes) {
                    if (!allLegacyCodes.add(legacyCode)) {
                        throw new IllegalArgumentException("Legacy province code is mapped more than once: " + legacyCode);
                    }
                    if (!existingByKey.containsKey(key("PROVINCE", legacyCode))) {
                        throw new IllegalArgumentException("Legacy province is absent from the baseline: " + legacyCode);
                    }
                }

                String name = requiredVietnameseName(provinceData);
                Location current = upsert(existingByKey, "PROVINCE", sourceCode,
                        "P-" + sourceCode, name, null, null, name, report);
                current.setSortOrder(Integer.parseInt(officialCode));
                locationRepository.save(current);
                canonicalKeys.add(key("PROVINCE", sourceCode));
                report.provinces++;
            }
        }
        if (allLegacyCodes.size() != 63) {
            throw new IllegalArgumentException("Current province catalog must map exactly 63 legacy codes.");
        }
        return aliasesByCurrentCode;
    }

    private void importLandmarks(Map<String, Location> existingByKey,
                                 Map<String, Set<String>> currentProvinceAliases,
                                 Set<String> canonicalKeys,
                                 MutableReport report) throws IOException {
        Resource resource = requireReadable(landmarkResource, "landmark");
        Set<String> seenCodes = new HashSet<>();
        int errorsBeforeImport = report.errors;
        try (InputStream input = openUtf8BomSafe(resource.getInputStream())) {
            List<Map<String, Object>> landmarks = objectMapper.readValue(input, new TypeReference<>() { });
            for (Map<String, Object> landmarkData : landmarks) {
                try {
                    String sourceCode = requiredText(landmarkData, "code");
                    if (!seenCodes.add(sourceCode)) {
                        throw new IllegalArgumentException("Duplicate landmark code: " + sourceCode);
                    }
                    String provinceCode = requiredText(landmarkData, "provinceCode");
                    Location province = existingByKey.get(key("PROVINCE", provinceCode));
                    if (province == null) {
                        throw new IllegalArgumentException("Unknown landmark province: " + provinceCode);
                    }

                    String status = optionalText(landmarkData, "status", "ACTIVE").toUpperCase();
                    Double latitude = number(landmarkData, "latitude");
                    Double longitude = number(landmarkData, "longitude");
                    if ("ACTIVE".equals(status) && !validCoordinates(latitude, longitude)) {
                        throw new IllegalArgumentException("Active landmark has invalid coordinates: " + sourceCode);
                    }
                    Double defaultRadiusKm = number(landmarkData, "defaultRadiusKm");
                    if (defaultRadiusKm != null && (!Double.isFinite(defaultRadiusKm)
                            || defaultRadiusKm <= 0 || defaultRadiusKm > 50)) {
                        throw new IllegalArgumentException("Landmark radius must be within 0..50 km: " + sourceCode);
                    }

                    Location parent = province;
                    String wardCode = optionalText(landmarkData, "wardCode", null);
                    if (wardCode != null) {
                        parent = existingByKey.get(key("WARD", wardCode));
                        if (parent == null || parent.getParent() == null) {
                            throw new IllegalArgumentException("Unknown landmark ward: " + wardCode);
                        }
                        String legacyParentCode = parent.getParent().getSourceCode();
                        if (!provinceCode.equals(legacyParentCode)
                                && !currentProvinceAliases.getOrDefault(provinceCode, Set.of()).contains(legacyParentCode)) {
                            throw new IllegalArgumentException("Landmark ward/province mismatch: " + sourceCode);
                        }
                    }

                    upsertLandmark(existingByKey, sourceCode, landmarkData, parent,
                            latitude, longitude, defaultRadiusKm, status, report);
                    canonicalKeys.add(key("LANDMARK", sourceCode));
                    report.landmarks++;
                } catch (IllegalArgumentException exception) {
                    report.errors++;
                    log.warn("Skipping invalid landmark fixture: {}", exception.getMessage());
                }
            }
        }
        if (report.errors > errorsBeforeImport) {
            throw new IllegalArgumentException("Landmark catalog contains invalid rows; import was rolled back.");
        }
    }

    private void upsertLandmark(Map<String, Location> existingByKey,
                                String sourceCode,
                                Map<String, Object> data,
                                Location parent,
                                Double latitude,
                                Double longitude,
                                Double defaultRadiusKm,
                                String status,
                                MutableReport report) {
        String naturalKey = key("LANDMARK", sourceCode);
        Location location = existingByKey.get(naturalKey);
        boolean created = location == null;
        if (created) {
            location = new Location();
            location.setLocationType("LANDMARK");
            location.setSourceCode(sourceCode);
        }

        String nameVi = requiredText(data, "nameVi");
        String nameEn = optionalText(data, "nameEn", null);
        String category = optionalText(data, "category", "OTHER").toUpperCase();
        Integer popularityScore = integer(data, "popularityScore", 0);
        if (popularityScore < 0) {
            throw new IllegalArgumentException("Landmark popularity cannot be negative: " + sourceCode);
        }
        String fullPath = nameVi + ", " + provinceFor(parent).getNameVi();
        boolean changed = created
                || !Objects.equals(location.getCode(), sourceCode)
                || !Objects.equals(location.getNameVi(), nameVi)
                || !Objects.equals(location.getNameEn(), nameEn)
                || !Objects.equals(location.getParent() == null ? null : location.getParent().getId(), parent.getId())
                || !Objects.equals(location.getLatitude(), latitude)
                || !Objects.equals(location.getLongitude(), longitude)
                || !Objects.equals(location.getCategory(), category)
                || !Objects.equals(location.getDefaultRadiusKm(), defaultRadiusKm)
                || !Objects.equals(location.getPopularityScore(), popularityScore)
                || !Objects.equals(location.getStatus(), status);

        location.setCode(sourceCode);
        location.setSourceCode(sourceCode);
        location.setNameVi(nameVi);
        location.setNameEn(nameEn);
        location.setNormalizedName(VietnameseTextNormalizer.normalize(nameVi));
        location.setParent(parent);
        location.setFullPath(fullPath);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setCategory(category);
        location.setDefaultRadiusKm(defaultRadiusKm);
        location.setPopularityScore(popularityScore);
        location.setDescriptionVi(optionalText(data, "descriptionVi", null));
        location.setDescriptionEn(optionalText(data, "descriptionEn", null));
        location.setStatus(status);
        Location saved = locationRepository.save(location);
        existingByKey.put(naturalKey, saved);

        if (created) report.added++;
        else if (changed) report.updated++;
        else report.skipped++;
    }

    private Location provinceFor(Location location) {
        Location cursor = location;
        for (int depth = 0; cursor != null && depth < 3; depth++) {
            if ("PROVINCE".equals(cursor.getLocationType())) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalArgumentException("Location has no province parent.");
    }

    private Location upsert(Map<String, Location> existingByKey, String type, String sourceCode, String code, String name, Location parent,
                            String legacyParentName, String fullPath, MutableReport report) {
        String naturalKey = key(type, sourceCode);
        Location location = existingByKey.get(naturalKey);
        boolean created = location == null;
        if (created) {
            location = new Location();
            location.setLocationType(type);
            location.setSourceCode(sourceCode);
        }

        boolean changed = created || !Objects.equals(location.getCode(), code)
                || !Objects.equals(location.getNameVi(), name)
                || !Objects.equals(location.getParent() == null ? null : location.getParent().getId(), parent == null ? null : parent.getId())
                || !Objects.equals(location.getLegacyParentName(), legacyParentName)
                || !Objects.equals(location.getFullPath(), fullPath)
                || !Objects.equals(location.getNormalizedName(), VietnameseTextNormalizer.normalize(name))
                || !"ACTIVE".equals(location.getStatus());

        location.setCode(code);
        location.setNameVi(name);
        location.setNormalizedName(VietnameseTextNormalizer.normalize(name));
        location.setParent(parent);
        location.setLegacyParentName(legacyParentName);
        location.setFullPath(fullPath);
        location.setStatus("ACTIVE");
        Location saved = locationRepository.save(location);
        existingByKey.put(naturalKey, saved);

        if (created) report.added++;
        else if (changed) report.updated++;
        else report.skipped++;
        return saved;
    }

    private int removeObsoleteRows(Set<String> canonicalKeys) {
        Set<Long> referencedIds = new HashSet<>();
        for (Hotel hotel : hotelRepository.findAll()) {
            if (hotel.getProvinceId() != null) referencedIds.add(hotel.getProvinceId());
            if (hotel.getWardId() != null) referencedIds.add(hotel.getWardId());
        }
        List<Location> staleWards = new ArrayList<>();
        List<Location> staleParents = new ArrayList<>();
        for (Location location : locationRepository.findAll()) {
            if (!canonicalKeys.contains(key(location.getLocationType(), location.getSourceCode())) && !referencedIds.contains(location.getId())) {
                if (location.getParent() == null) staleParents.add(location); else staleWards.add(location);
            }
        }
        locationRepository.deleteAll(staleWards);
        locationRepository.flush();
        locationRepository.deleteAll(staleParents);
        return staleWards.size() + staleParents.size();
    }

    private InputStream openLocationSource() throws IOException {
        if (jsonFilePath != null && !jsonFilePath.isBlank()) {
            Path configuredPath = Path.of(jsonFilePath).toAbsolutePath().normalize();
            if (Files.isRegularFile(configuredPath)) return Files.newInputStream(configuredPath);
        }
        return requireReadable(locationResource, "location").getInputStream();
    }

    private Resource requireReadable(Resource resource, String catalogName) {
        if (resource == null || !resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Packaged " + catalogName + " catalog is unavailable.");
        }
        return resource;
    }

    private InputStream openUtf8BomSafe(InputStream source) throws IOException {
        PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(source), 3);
        byte[] bom = input.readNBytes(3);
        if (!(bom.length == 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)) {
            input.unread(bom);
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childList(Map<String, Object> parent, String field) {
        Object value = parent.get(field);
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String requiredText(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Thiếu " + field);
        return value.toString().trim();
    }

    private String optionalText(Map<String, Object> source, String field, String fallback) {
        Object value = source.get(field);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private Double number(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be numeric.");
        }
    }

    private Integer integer(Map<String, Object> source, String field, int fallback) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) return fallback;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer.");
        }
    }

    private List<String> stringList(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    private String requiredVietnameseName(Map<String, Object> source) {
        String name = requiredText(source, "name");
        if (name.contains("?") || name.contains("\uFFFD")) {
            throw new IllegalArgumentException("Nguồn địa giới đã lỗi encoding: " + name);
        }
        return name;
    }

    private String key(String type, String sourceCode) {
        return type + "|" + sourceCode;
    }

    public record ImportReport(int added, int updated, int skipped, int removed, int errors,
                               int provinces, int wards, int landmarks) { }

    private static final class MutableReport {
        int added;
        int updated;
        int skipped;
        int removed;
        int errors;
        int provinces;
        int wards;
        int landmarks;
        ImportReport toImmutable() {
            return new ImportReport(added, updated, skipped, removed, errors, provinces, wards, landmarks);
        }
    }
}
