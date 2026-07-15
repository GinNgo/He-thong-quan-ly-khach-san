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
        log.info("LOCATION_IMPORT added={} updated={} skipped={} removed={} errors={} provinces={} wards={}",
                report.added(), report.updated(), report.skipped(), report.removed(), report.errors(), report.provinces(), report.wards());
    }

    @Transactional
    public ImportReport importData(boolean removeObsolete) {
        Path source = resolveSourcePath();
        MutableReport report = new MutableReport();
        Set<String> canonicalKeys = new HashSet<>();
        Map<String, Location> existingByKey = new HashMap<>();
        for (Location location : locationRepository.findAll()) {
            existingByKey.put(key(location.getLocationType(), location.getSourceCode()), location);
        }

        try (InputStream input = openUtf8BomSafe(source)) {
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

            if (removeObsolete) {
                report.removed = removeObsoleteRows(canonicalKeys);
            }
            locationRepository.flush();
            return report.toImmutable();
        } catch (Exception exception) {
            log.error("Location import failed for {}", source, exception);
            throw new IllegalStateException("Không thể import địa giới từ file UTF-8: " + source, exception);
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

    private Path resolveSourcePath() {
        List<Path> candidates = List.of(
                Path.of(jsonFilePath),
                Path.of("docs", "34_tinh_huyen_xa.json"),
                Path.of("..", "docs", "34_tinh_huyen_xa.json")
        );
        return candidates.stream().map(Path::toAbsolutePath).map(Path::normalize).filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy docs/34_tinh_huyen_xa.json"));
    }

    private InputStream openUtf8BomSafe(Path path) throws IOException {
        PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(Files.newInputStream(path)), 3);
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

    public record ImportReport(int added, int updated, int skipped, int removed, int errors, int provinces, int wards) { }

    private static final class MutableReport {
        int added;
        int updated;
        int skipped;
        int removed;
        int errors;
        int provinces;
        int wards;
        ImportReport toImmutable() { return new ImportReport(added, updated, skipped, removed, errors, provinces, wards); }
    }
}
