package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.OperationalPolicyDTO;
import com.hotel.dtos.OperationalPolicyRequest;
import com.hotel.dtos.PublicOperationalPolicyDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.OperationalPolicyVersion;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.OperationalPolicyRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalPolicyService {

    private final OperationalPolicyRepository repository;
    private final HotelRepository hotelRepository;
    private final PropertyAccessService propertyAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<OperationalPolicyDTO> list(Long hotelId) {
        propertyAccessService.requireAssignedHotel(hotelId);
        return repository.findByHotelIdOrderByVersionNumberDesc(hotelId).stream().map(this::toDto).toList();
    }

    @Transactional
    public OperationalPolicyDTO createDraft(Long hotelId, OperationalPolicyRequest request) {
        propertyAccessService.requireAssignedHotel(hotelId);
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        validateRequest(request);
        long nextVersion = repository.findFirstByHotelIdOrderByVersionNumberDesc(hotelId)
                .map(OperationalPolicyVersion::getVersionNumber).orElse(0L) + 1L;
        OperationalPolicyVersion policy = new OperationalPolicyVersion();
        policy.setHotel(hotel);
        policy.setVersionNumber(nextVersion);
        policy.setStatus("DRAFT");
        apply(policy, request);
        return toDto(repository.save(policy));
    }

    @Transactional
    public OperationalPolicyDTO updateDraft(Long hotelId, Long policyId, OperationalPolicyRequest request) {
        propertyAccessService.requireAssignedHotel(hotelId);
        validateRequest(request);
        OperationalPolicyVersion policy = requireOwnedForUpdate(hotelId, policyId);
        if (!"DRAFT".equals(policy.getStatus())) {
            throw new IllegalStateException("Chính sách đã công bố là bất biến; hãy tạo phiên bản mới.");
        }
        apply(policy, request);
        return toDto(repository.save(policy));
    }

    @Transactional
    public OperationalPolicyDTO publish(Long hotelId, Long policyId) {
        propertyAccessService.requireAssignedHotel(hotelId);
        OperationalPolicyVersion candidate = requireOwnedForUpdate(hotelId, policyId);
        if (!"DRAFT".equals(candidate.getStatus())) {
            throw new IllegalStateException("Chỉ phiên bản nháp mới có thể được công bố.");
        }
        List<OperationalPolicyVersion> published = repository.findPublishedForUpdate(hotelId);
        for (OperationalPolicyVersion current : published) {
            if (!current.getEffectiveFrom().isBefore(candidate.getEffectiveFrom())) {
                throw new IllegalStateException("Đã có phiên bản được công bố cùng hoặc sau ngày hiệu lực này.");
            }
            if (current.getEffectiveUntil() == null || current.getEffectiveUntil().isAfter(candidate.getEffectiveFrom())) {
                current.setEffectiveUntil(candidate.getEffectiveFrom());
            }
        }
        candidate.setStatus("PUBLISHED");
        repository.saveAll(published);
        return toDto(repository.save(candidate));
    }

    @Transactional(readOnly = true)
    public PublicOperationalPolicyDTO currentPublic(Long hotelId, String locale, LocalDate stayDate) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .filter(item -> "APPROVED".equalsIgnoreCase(item.getApprovalStatus()))
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getOperationStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        LocalDateTime effectiveAt = stayDate == null ? LocalDateTime.now() : atStart(stayDate);
        return findEffective(hotel.getId(), effectiveAt).map(item -> toPublic(item, locale))
                .orElseThrow(() -> new ResourceNotFoundException("Cơ sở chưa công bố chính sách lưu trú."));
    }

    @Transactional(readOnly = true)
    public Optional<PolicySnapshot> capture(Long hotelId, LocalDate stayDate) {
        return findEffective(hotelId, atStart(stayDate)).map(policy -> new PolicySnapshot(
                policy.getId(), policy.getVersionNumber(), policy.getEffectiveFrom(), serialize(policy)));
    }

    private Optional<OperationalPolicyVersion> findEffective(Long hotelId, LocalDateTime at) {
        return repository.findByHotelIdAndStatusOrderByEffectiveFromDesc(hotelId, "PUBLISHED").stream()
                .filter(item -> !item.getEffectiveFrom().isAfter(at))
                .filter(item -> item.getEffectiveUntil() == null || item.getEffectiveUntil().isAfter(at))
                .findFirst();
    }

    private OperationalPolicyVersion requireOwnedForUpdate(Long hotelId, Long policyId) {
        OperationalPolicyVersion policy = repository.findByIdForUpdate(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiên bản chính sách."));
        if (!hotelId.equals(policy.getHotel().getId())) {
            throw new ResourceNotFoundException("Không tìm thấy phiên bản chính sách.");
        }
        return policy;
    }

    private void validateRequest(OperationalPolicyRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu nội dung chính sách.");
        if (request.effectiveFrom() == null) throw new IllegalArgumentException("Thiếu ngày hiệu lực.");
        List<String> required = List.of(request.checkInVi(), request.checkOutVi(), request.cancellationVi(),
                request.childPolicyVi(), request.petPolicyVi(), request.smokingPolicyVi(), request.houseRulesVi());
        if (required.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Nội dung chính sách tiếng Việt không được để trống.");
        }
    }

    private void apply(OperationalPolicyVersion policy, OperationalPolicyRequest request) {
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveUntil(null);
        policy.setCheckInVi(clean(request.checkInVi()));
        policy.setCheckInEn(cleanNullable(request.checkInEn()));
        policy.setCheckOutVi(clean(request.checkOutVi()));
        policy.setCheckOutEn(cleanNullable(request.checkOutEn()));
        policy.setCancellationVi(clean(request.cancellationVi()));
        policy.setCancellationEn(cleanNullable(request.cancellationEn()));
        policy.setChildPolicyVi(clean(request.childPolicyVi()));
        policy.setChildPolicyEn(cleanNullable(request.childPolicyEn()));
        policy.setPetPolicyVi(clean(request.petPolicyVi()));
        policy.setPetPolicyEn(cleanNullable(request.petPolicyEn()));
        policy.setSmokingPolicyVi(clean(request.smokingPolicyVi()));
        policy.setSmokingPolicyEn(cleanNullable(request.smokingPolicyEn()));
        policy.setHouseRulesVi(clean(request.houseRulesVi()));
        policy.setHouseRulesEn(cleanNullable(request.houseRulesEn()));
    }

    private PublicOperationalPolicyDTO toPublic(OperationalPolicyVersion item, String requestedLocale) {
        String locale = requestedLocale != null && requestedLocale.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
        boolean english = "en".equals(locale);
        return new PublicOperationalPolicyDTO(item.getVersionNumber(), item.getEffectiveFrom(), locale,
                localized(item.getCheckInVi(), item.getCheckInEn(), english),
                localized(item.getCheckOutVi(), item.getCheckOutEn(), english),
                localized(item.getCancellationVi(), item.getCancellationEn(), english),
                localized(item.getChildPolicyVi(), item.getChildPolicyEn(), english),
                localized(item.getPetPolicyVi(), item.getPetPolicyEn(), english),
                localized(item.getSmokingPolicyVi(), item.getSmokingPolicyEn(), english),
                localized(item.getHouseRulesVi(), item.getHouseRulesEn(), english));
    }

    private OperationalPolicyDTO toDto(OperationalPolicyVersion item) {
        return new OperationalPolicyDTO(item.getId(), item.getHotel().getId(), item.getVersionNumber(), item.getStatus(),
                item.getEffectiveFrom(), item.getEffectiveUntil(), item.getCheckInVi(), item.getCheckInEn(),
                item.getCheckOutVi(), item.getCheckOutEn(), item.getCancellationVi(), item.getCancellationEn(),
                item.getChildPolicyVi(), item.getChildPolicyEn(), item.getPetPolicyVi(), item.getPetPolicyEn(),
                item.getSmokingPolicyVi(), item.getSmokingPolicyEn(), item.getHouseRulesVi(), item.getHouseRulesEn(),
                item.getRowVersion());
    }

    private String serialize(OperationalPolicyVersion policy) {
        try {
            return objectMapper.writeValueAsString(toDto(policy));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể tạo bản chụp chính sách.", exception);
        }
    }

    private LocalDateTime atStart(LocalDate date) {
        return (date == null ? LocalDate.now() : date).atTime(LocalTime.MIN);
    }

    private String localized(String vi, String en, boolean english) {
        return english && en != null && !en.isBlank() ? en : vi;
    }

    private String clean(String value) { return value.trim(); }
    private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record PolicySnapshot(Long policyId, Long version, LocalDateTime effectiveFrom, String json) {
    }
}
