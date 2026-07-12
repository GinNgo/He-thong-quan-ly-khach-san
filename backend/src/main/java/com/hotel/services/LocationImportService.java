package com.hotel.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Location;
import com.hotel.repositories.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LocationImportService {

    private static final Logger log = LoggerFactory.getLogger(LocationImportService.class);

    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.location-import.enabled:false}")
    private boolean importEnabled;

    @Value("${app.location-import.file-path:}")
    private String jsonFilePath;

    public LocationImportService(LocationRepository locationRepository, ObjectMapper objectMapper) {
        this.locationRepository = locationRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void importDataOnStartup() {
        if (!importEnabled) {
            log.info("Location import is disabled.");
            return;
        }

        if (jsonFilePath == null || jsonFilePath.isBlank()) {
            log.warn("Location JSON file path is empty. Skipping import.");
            return;
        }

        File file = new File(jsonFilePath);
        if (!file.exists()) {
            log.error("Location JSON file not found at: {}", jsonFilePath);
            return;
        }

        try {
            log.info("Starting Location Import from: {}", jsonFilePath);
            List<Map<String, Object>> provincesData = objectMapper.readValue(file, new TypeReference<>() {});

            int totalProvinces = 0;
            int totalWards = 0;
            int newRecords = 0;
            int updatedRecords = 0;
            int skipped = 0;

            for (Map<String, Object> provinceData : provincesData) {
                String pCode = String.valueOf(provinceData.get("code"));
                String pName = (String) provinceData.get("name");

                Location province = locationRepository.findByCode(pCode).orElse(null);
                if (province == null) {
                    province = new Location();
                    province.setCode(pCode);
                    province.setSourceCode(pCode);
                    province.setLocationType("PROVINCE");
                    newRecords++;
                } else {
                    updatedRecords++;
                }
                
                province.setNameVi(pName);
                province.setNormalizedName(normalize(pName));
                province.setFullPath(pName);
                province = locationRepository.save(province);
                totalProvinces++;

                List<Map<String, Object>> districtsData = (List<Map<String, Object>>) provinceData.get("districts");
                if (districtsData != null) {
                    for (Map<String, Object> districtData : districtsData) {
                        String legacyDistrictName = (String) districtData.get("name");
                        List<Map<String, Object>> wardsData = (List<Map<String, Object>>) districtData.get("wards");
                        if (wardsData != null) {
                            for (Map<String, Object> wardData : wardsData) {
                                String wCode = String.valueOf(wardData.get("code"));
                                String wName = (String) wardData.get("name");

                                Location ward = locationRepository.findByCode(wCode).orElse(null);
                                if (ward == null) {
                                    ward = new Location();
                                    ward.setCode(wCode);
                                    ward.setSourceCode(wCode);
                                    ward.setLocationType("WARD");
                                    newRecords++;
                                } else {
                                    updatedRecords++;
                                }
                                
                                ward.setNameVi(wName);
                                ward.setNormalizedName(normalize(wName));
                                ward.setParent(province);
                                ward.setLegacyParentName(legacyDistrictName);
                                ward.setFullPath(wName + ", " + pName);
                                locationRepository.save(ward);
                                totalWards++;
                            }
                        }
                    }
                }
            }

            log.info("--- LOCATION IMPORT REPORT ---");
            log.info("Total Provinces processed: {}", totalProvinces);
            log.info("Total Wards processed: {}", totalWards);
            log.info("New records: {}", newRecords);
            log.info("Updated records: {}", updatedRecords);
            log.info("Skipped: {}", skipped);
            log.info("------------------------------");

        } catch (Exception e) {
            log.error("Error during location import", e);
        }
    }

    private String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }
}
