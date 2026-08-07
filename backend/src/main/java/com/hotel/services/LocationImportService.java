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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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

    @Value("${app.location-import.resource:classpath:data/locations.json}")
    private Resource locationResource;

    @Value("${app.location-import.landmark-resource:classpath:data/landmarks.json}")
    Resource landmarkResource;

    @Value("${app.location-import.current-province-resource:classpath:data/provinces-current-34.json}")
    Resource currentProvinceResource;

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
        Resource source = resolveSourceResource();
        MutableReport report = new MutableReport();
        Set<String> canonicalKeys = new HashSet<>();
        Map<String, Location> existingByKey = new HashMap<>();
        Map<String, Location> existingLandmarksBySourceIdentity = new HashMap<>();
        for (Location location : locationRepository.findAll()) {
            existingByKey.put(key(location.getLocationType(), location.getSourceCode()), location);
            String sourceIdentity = sourceIdentity(location.getSourceProvider(), location.getSourceObjectType(), location.getSourceObjectId());
            if ("LANDMARK".equals(location.getLocationType()) && sourceIdentity != null) {
                existingLandmarksBySourceIdentity.put(sourceIdentity, location);
            }
        }

        try (InputStream input = openUtf8BomSafe(source.getInputStream())) {
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

            importCurrentProvinces(existingByKey, canonicalKeys, report);
            importLandmarks(existingByKey, existingLandmarksBySourceIdentity, canonicalKeys, report);

            if (removeObsolete) {
                report.removed = removeObsoleteRows(canonicalKeys);
            }
            locationRepository.flush();
            return report.toImmutable();
        } catch (Exception exception) {
            log.error("Location import failed for {}", source.getDescription(), exception);
            throw new IllegalStateException(
                    "Không thể import địa giới từ resource UTF-8: " + source.getDescription(),
                    exception);
        }
    }

    private void importCurrentProvinces(Map<String, Location> existingByKey,
                                        Set<String> canonicalKeys, MutableReport report) {
        Resource resource = resolveCurrentProvinceResource();
        try (InputStream input = openUtf8BomSafe(resource.getInputStream())) {
            List<Map<String, Object>> provinces = objectMapper.readValue(input, new TypeReference<>() { });
            Set<String> seenLegacyCodes = new HashSet<>();
            for (Map<String, Object> provinceData : provinces) {
                String sourceCode = requiredText(provinceData, "sourceCode");
                if (!sourceCode.startsWith("VN34-")) {
                    throw new IllegalArgumentException("Mã tỉnh hiện hành phải bắt đầu bằng VN34-: " + sourceCode);
                }
                String officialCode = requiredText(provinceData, "officialCode");
                String name = requiredVietnameseName(provinceData);
                List<String> legacyCodes = stringList(provinceData, "legacyProvinceCodes");
                if (legacyCodes.isEmpty()) {
                    throw new IllegalArgumentException("Tỉnh hiện hành phải có ít nhất một mã tỉnh cũ: " + sourceCode);
                }
                for (String legacyCode : legacyCodes) {
                    if (!seenLegacyCodes.add(legacyCode)) {
                        throw new IllegalArgumentException("Mã tỉnh cũ được ánh xạ nhiều lần: " + legacyCode);
                    }
                    if (!existingByKey.containsKey(key("PROVINCE", legacyCode))) {
                        throw new IllegalArgumentException("Không tìm thấy tỉnh cũ trong baseline: " + legacyCode);
                    }
                }

                Location current = upsert(existingByKey, "PROVINCE", sourceCode, "P-" + sourceCode,
                        name, null, null, name, report);
                current.setNameEn(optionalText(provinceData, "nameEn", null));
                current.setSourceProvider("PROVINCES_OPEN_API_V2");
                current.setSourceObjectType("PROVINCE");
                current.setSourceObjectId(officialCode);
                current.setDataQualityStatus("VERIFIED");
                current.setLastSeenAt(LocalDateTime.now());
                current.setSortOrder(Integer.parseInt(officialCode));
                locationRepository.save(current);
                canonicalKeys.add(key("PROVINCE", sourceCode));
                report.provinces++;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc danh mục 34 tỉnh/thành hiện hành: "
                    + resource.getDescription(), exception);
        }
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

    private void importLandmarks(Map<String, Location> existingByKey,
                                 Map<String, Location> existingLandmarksBySourceIdentity,
                                 Set<String> canonicalKeys, MutableReport report) {
        if (landmarkResource == null || !landmarkResource.exists() || !landmarkResource.isReadable()) {
            log.info("Landmark import resource is not configured; skipping landmark fixtures.");
            return;
        }
        try (InputStream input = openUtf8BomSafe(landmarkResource.getInputStream())) {
            List<Map<String, Object>> landmarks = objectMapper.readValue(input, new TypeReference<>() { });
            Set<String> seenSourceIdentities = new HashSet<>();
            for (Map<String, Object> landmarkData : landmarks) {
                try {
                    String sourceCode = requiredText(landmarkData, "code");
                    String nameVi = requiredText(landmarkData, "nameVi");
                    String sourceProvider = optionalText(landmarkData, "sourceProvider", null);
                    String sourceObjectType = optionalText(landmarkData, "sourceObjectType", null);
                    String sourceObjectId = optionalText(landmarkData, "sourceObjectId", null);
                    String sourceIdentity = sourceIdentity(sourceProvider, sourceObjectType, sourceObjectId);
                    if (sourceIdentity != null && !seenSourceIdentities.add(sourceIdentity)) {
                        throw new IllegalArgumentException("Trùng khóa nguồn landmark: " + sourceIdentity);
                    }
                    String provinceCode = requiredText(landmarkData, "provinceCode");
                    Location province = existingByKey.get(key("PROVINCE", provinceCode));
                    if (province == null) throw new IllegalArgumentException("Không tìm thấy tỉnh của landmark " + sourceCode);

                    String status = optionalText(landmarkData, "status", "ACTIVE").toUpperCase();
                    Double latitude = number(landmarkData, "latitude");
                    Double longitude = number(landmarkData, "longitude");
                    if ("ACTIVE".equals(status) && !validCoordinates(latitude, longitude)) {
                        throw new IllegalArgumentException("Landmark ACTIVE phải có tọa độ hợp lệ: " + sourceCode);
                    }
                    Double defaultRadiusKm = number(landmarkData, "defaultRadiusKm");
                    if (defaultRadiusKm != null && (defaultRadiusKm <= 0 || defaultRadiusKm > 50)) {
                        throw new IllegalArgumentException("defaultRadiusKm phải nằm trong khoảng 0 đến 50: " + sourceCode);
                    }

                    Location parent = province;
                    String wardCode = optionalText(landmarkData, "wardCode", null);
                    if (wardCode != null) {
                        parent = existingByKey.get(key("WARD", wardCode));
                        if (parent == null) throw new IllegalArgumentException("Không tìm thấy phường/xã của landmark " + sourceCode);
                        if (!province.equals(parent.getParent())) {
                            throw new IllegalArgumentException("Phường/xã của landmark không thuộc tỉnh đã chọn: " + sourceCode);
                        }
                    }

                    Location saved = upsertLandmark(existingByKey, existingLandmarksBySourceIdentity,
                            sourceCode, nameVi, landmarkData, parent,
                            latitude, longitude, defaultRadiusKm, status, report);
                    canonicalKeys.add(key("LANDMARK", sourceCode));
                    report.landmarks++;
                } catch (IllegalArgumentException exception) {
                    report.errors++;
                    log.warn("Skipping invalid landmark fixture: {}", exception.getMessage());
                }
            }
        } catch (IOException exception) {
            report.errors++;
            throw new IllegalStateException("Không thể đọc fixture landmark: " + landmarkResource.getDescription(), exception);
        }
    }

    private Location upsertLandmark(Map<String, Location> existingByKey,
                                    Map<String, Location> existingLandmarksBySourceIdentity,
                                    String sourceCode, String nameVi,
                                    Map<String, Object> data, Location parent, Double latitude, Double longitude,
                                    Double defaultRadiusKm, String status, MutableReport report) {
        String naturalKey = key("LANDMARK", sourceCode);
        String sourceProvider = optionalText(data, "sourceProvider", null);
        String sourceObjectType = optionalText(data, "sourceObjectType", null);
        String sourceObjectId = optionalText(data, "sourceObjectId", null);
        String externalSourceIdentity = sourceIdentity(sourceProvider, sourceObjectType, sourceObjectId);
        Location location = existingByKey.get(naturalKey);
        if (location == null && externalSourceIdentity != null) {
            location = existingLandmarksBySourceIdentity.get(externalSourceIdentity);
        }
        boolean created = location == null;
        String previousSourceCode = created ? null : location.getSourceCode();
        if (created) {
            location = new Location();
            location.setLocationType("LANDMARK");
        }

        String nameEn = optionalText(data, "nameEn", null);
        String category = optionalText(data, "category", "OTHER").toUpperCase();
        Integer popularityScore = integer(data, "popularityScore", 0);
        if (popularityScore < 0) throw new IllegalArgumentException("popularityScore không được âm: " + sourceCode);
        String descriptionVi = optionalText(data, "descriptionVi", null);
        String descriptionEn = optionalText(data, "descriptionEn", null);
        LocalDateTime sourceUpdatedAt = localDateTime(data, "sourceUpdatedAt");
        String dataQualityStatus = optionalText(data, "dataQualityStatus", "MATCHED").toUpperCase();
        Boolean manualOverride = bool(data, "manualOverride", false);
        boolean preserveManualFields = !created && Boolean.TRUE.equals(location.getManualOverride()) && !manualOverride;
        boolean effectiveManualOverride = Boolean.TRUE.equals(location.getManualOverride()) || manualOverride;
        String fullPath = nameVi + ", " + provinceFor(parent).getNameVi();
        boolean changed = created
                || !Objects.equals(location.getCode(), sourceCode)
                || !Objects.equals(location.getSourceCode(), sourceCode)
                || (!preserveManualFields && (
                        !Objects.equals(location.getNameVi(), nameVi)
                        || !Objects.equals(location.getNameEn(), nameEn)
                        || !Objects.equals(location.getParent() == null ? null : location.getParent().getId(), parent.getId())
                        || !Objects.equals(location.getFullPath(), fullPath)
                        || !Objects.equals(location.getLatitude(), latitude)
                        || !Objects.equals(location.getLongitude(), longitude)
                        || !Objects.equals(location.getDefaultRadiusKm(), defaultRadiusKm)
                        || !Objects.equals(location.getPopularityScore(), popularityScore)
                        || !Objects.equals(location.getCategory(), category)
                        || !Objects.equals(location.getDescriptionVi(), descriptionVi)
                        || !Objects.equals(location.getDescriptionEn(), descriptionEn)
                        || !Objects.equals(location.getStatus(), status)))
                || !Objects.equals(location.getSourceProvider(), sourceProvider)
                || !Objects.equals(location.getSourceObjectType(), sourceObjectType)
                || !Objects.equals(location.getSourceObjectId(), sourceObjectId)
                || !Objects.equals(location.getSourceUpdatedAt(), sourceUpdatedAt)
                || !Objects.equals(location.getDataQualityStatus(), dataQualityStatus)
                || !Objects.equals(location.getManualOverride(), effectiveManualOverride);

        location.setCode(sourceCode);
        location.setSourceCode(sourceCode);
        if (!preserveManualFields) {
            location.setNameVi(nameVi);
            location.setNameEn(nameEn);
            location.setNormalizedName(VietnameseTextNormalizer.normalize(nameVi));
            location.setParent(parent);
            location.setFullPath(fullPath);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setDefaultRadiusKm(defaultRadiusKm);
            location.setPopularityScore(popularityScore);
            location.setCategory(category);
            location.setDescriptionVi(descriptionVi);
            location.setDescriptionEn(descriptionEn);
            location.setStatus(status);
            location.setSortOrder(Math.max(0, 100000 - popularityScore));
        }
        location.setSourceProvider(sourceProvider);
        location.setSourceObjectType(sourceObjectType);
        location.setSourceObjectId(sourceObjectId);
        location.setSourceUpdatedAt(sourceUpdatedAt);
        location.setLastSeenAt(LocalDateTime.now());
        location.setDataQualityStatus(dataQualityStatus);
        location.setManualOverride(effectiveManualOverride);
        Location saved = locationRepository.save(location);
        if (previousSourceCode != null && !previousSourceCode.equals(sourceCode)) {
            existingByKey.remove(key("LANDMARK", previousSourceCode));
        }
        existingByKey.put(naturalKey, saved);
        if (externalSourceIdentity != null) {
            existingLandmarksBySourceIdentity.put(externalSourceIdentity, saved);
        }
        if (created) report.added++;
        else if (changed) report.updated++;
        else report.skipped++;
        return saved;
    }

    private Location provinceFor(Location location) {
        Location cursor = location;
        for (int depth = 0; cursor != null && depth < 3; depth++) {
            if ("PROVINCE".equals(cursor.getLocationType())) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalArgumentException("Landmark phải thuộc một tỉnh hợp lệ.");
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
                if ("LANDMARK".equals(location.getLocationType())) {
                    if (!Boolean.TRUE.equals(location.getManualOverride())) {
                        location.setDataQualityStatus("MISSING_SOURCE");
                        locationRepository.save(location);
                    }
                    continue;
                }
                if (location.getParent() == null) staleParents.add(location); else staleWards.add(location);
            }
        }
        locationRepository.deleteAll(staleWards);
        locationRepository.flush();
        locationRepository.deleteAll(staleParents);
        return staleWards.size() + staleParents.size();
    }

    Resource resolveSourceResource() {
        if (locationResource == null || !locationResource.exists() || !locationResource.isReadable()) {
            String description = locationResource == null ? "<not configured>" : locationResource.getDescription();
            throw new IllegalStateException(
                    "Location import resource is unavailable: " + description
                            + ". Configure LOCATION_IMPORT_RESOURCE or package classpath:data/locations.json.");
        }
        return locationResource;
    }

    Resource resolveLandmarkResource() {
        if (landmarkResource == null || !landmarkResource.exists() || !landmarkResource.isReadable()) {
            String description = landmarkResource == null ? "<not configured>" : landmarkResource.getDescription();
            throw new IllegalStateException(
                    "Landmark import resource is unavailable: " + description
                            + ". Configure LANDMARK_IMPORT_RESOURCE or package classpath:data/landmarks.json.");
        }
        return landmarkResource;
    }

    Resource resolveCurrentProvinceResource() {
        if (currentProvinceResource == null || !currentProvinceResource.exists() || !currentProvinceResource.isReadable()) {
            String description = currentProvinceResource == null ? "<not configured>" : currentProvinceResource.getDescription();
            throw new IllegalStateException(
                    "Current province import resource is unavailable: " + description
                            + ". Configure CURRENT_PROVINCE_IMPORT_RESOURCE or package classpath:data/provinces-current-34.json.");
        }
        return currentProvinceResource;
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
        try { return Double.valueOf(value.toString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " không phải số hợp lệ."); }
    }

    private Integer integer(Map<String, Object> source, String field, int fallback) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) return fallback;
        try { return Integer.valueOf(value.toString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " không phải số nguyên hợp lệ."); }
    }

    private Boolean bool(Map<String, Object> source, String field, boolean fallback) {
        Object value = source.get(field);
        if (value == null || value.toString().isBlank()) return fallback;
        if (value instanceof Boolean booleanValue) return booleanValue;
        if ("true".equalsIgnoreCase(value.toString())) return true;
        if ("false".equalsIgnoreCase(value.toString())) return false;
        throw new IllegalArgumentException(field + " không phải boolean hợp lệ.");
    }

    private List<String> stringList(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(Object::toString).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private LocalDateTime localDateTime(Map<String, Object> source, String field) {
        String value = optionalText(source, field, null);
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " không phải thời gian ISO hợp lệ.");
        }
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
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

    private String sourceIdentity(String provider, String objectType, String objectId) {
        if (provider == null || provider.isBlank() || objectType == null || objectType.isBlank()
                || objectId == null || objectId.isBlank()) {
            return null;
        }
        return provider.trim().toUpperCase() + "|" + objectType.trim().toUpperCase() + "|" + objectId.trim();
    }

    public record ImportReport(int added, int updated, int skipped, int removed, int errors, int provinces, int wards, int landmarks) { }

    private static final class MutableReport {
        int added;
        int updated;
        int skipped;
        int removed;
        int errors;
        int provinces;
        int wards;
        int landmarks;
        ImportReport toImmutable() { return new ImportReport(added, updated, skipped, removed, errors, provinces, wards, landmarks); }
    }
}
