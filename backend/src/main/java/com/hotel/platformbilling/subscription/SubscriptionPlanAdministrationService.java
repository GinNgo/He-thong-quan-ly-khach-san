package com.hotel.platformbilling.subscription;

import com.hotel.dtos.SubscriptionFeatureDTO;
import com.hotel.dtos.SubscriptionPlanDTO;
import com.hotel.entities.PlanFeature;
import com.hotel.entities.SubscriptionPlan;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.SubscriptionFeatureRepository;
import com.hotel.repositories.SubscriptionPlanRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SubscriptionPlanAdministrationService {
    private static final Pattern KEY = Pattern.compile("[A-Z0-9_]{2,50}");
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionFeatureRepository featureRepository;
    private final PropertyAccessService accessService;
    private final FinancialAuditService auditService;
    private final Clock clock;
    private final SubscriptionPlanAdminOperationRepository operationRepository;

    public SubscriptionPlanAdministrationService(SubscriptionPlanRepository planRepository,
            SubscriptionFeatureRepository featureRepository, PropertyAccessService accessService,
            FinancialAuditService auditService, SubscriptionPlanAdminOperationRepository operationRepository) {
        this(planRepository, featureRepository, accessService, auditService, operationRepository, Clock.systemUTC());
    }

    SubscriptionPlanAdministrationService(SubscriptionPlanRepository planRepository,
            SubscriptionFeatureRepository featureRepository, PropertyAccessService accessService,
            FinancialAuditService auditService, SubscriptionPlanAdminOperationRepository operationRepository, Clock clock) {
        this.planRepository = planRepository; this.featureRepository = featureRepository;
        this.accessService = accessService; this.auditService = auditService; this.clock = clock;
        this.operationRepository = operationRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanVersionView> list() {
        requireSystemAdmin();
        return planRepository.findAllByOrderByFamilyCodeAscVersionNumberDesc().stream().map(this::view).toList();
    }

    @Transactional
    public PlanVersionView createVersion(CreateVersionCommand command, String idempotencyKey, String correlationId) {
        requireSystemAdmin();
        Validated input = validate(command);
        String keyHash = hash(requireText(idempotencyKey, "Idempotency-Key", 200));
        String payloadHash = hash(input.toString());
        Optional<SubscriptionPlan> replay = planRepository.findByCreationKeyHash(keyHash);
        if (replay.isPresent()) {
            if (!payloadHash.equals(replay.get().getCreationPayloadHash()))
                throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
            return view(replay.get());
        }
        List<SubscriptionPlan> family = planRepository.findFamilyForUpdate(input.familyCode());
        int version = family.isEmpty() ? 1 : family.get(0).getVersionNumber() + 1;
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setFamilyCode(input.familyCode()); plan.setVersionNumber(version);
        plan.setCode(input.familyCode() + "_V" + version); plan.setNameVi(input.nameVi()); plan.setNameEn(input.nameEn());
        plan.setBillingType(input.billingType()); plan.setPrice(input.price()); plan.setIsLifetime(input.lifetime());
        plan.setDurationValue(input.durationValue()); plan.setDurationUnit(input.durationUnit());
        plan.setStatus("INACTIVE"); plan.setCreationKeyHash(keyHash); plan.setCreationPayloadHash(payloadHash);
        Set<PlanFeature> features = new LinkedHashSet<>();
        for (FeatureLimit item : input.features()) {
            PlanFeature feature = new PlanFeature(); feature.setPlan(plan);
            feature.setFeatureCode(item.code()); feature.setLimitValue(item.limit()); features.add(feature);
        }
        plan.setFeatures(features);
        try { plan = planRepository.saveAndFlush(plan); }
        catch (DataIntegrityViolationException exception) {
            SubscriptionPlan winner = planRepository.findByCreationKeyHash(keyHash)
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
            if (!payloadHash.equals(winner.getCreationPayloadHash()))
                throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
            return view(winner);
        }
        audit(plan, "PLAN_VERSION_CREATED", null, "INACTIVE", correlationId, keyHash, "Created immutable plan version");
        return view(plan);
    }

    @Transactional
    public PlanVersionView activate(Long planId, String idempotencyKey, String correlationId) {
        return changeStatus(planId, "ACTIVE", idempotencyKey, correlationId, "Activated governed plan version");
    }

    @Transactional
    public PlanVersionView deactivate(Long planId, String reason, String idempotencyKey, String correlationId) {
        String safeReason = requireText(reason, "reason", 1000);
        if (safeReason.length() < 10) throw new IllegalArgumentException("reason must contain at least 10 characters.");
        return changeStatus(planId, "INACTIVE", idempotencyKey, correlationId, safeReason);
    }

    private PlanVersionView changeStatus(Long planId, String target, String idempotencyKey, String correlationId, String reason) {
        requireSystemAdmin(); String operationKey = hash(requireText(idempotencyKey, "Idempotency-Key", 200));
        String requestHash = hash(target + ":" + planId + ":" + reason);
        Optional<SubscriptionPlanAdminOperation> replay = operationRepository.findByKeyHash(operationKey);
        if (replay.isPresent()) {
            if (!target.equals(replay.get().getAction()) || !Objects.equals(planId, replay.get().getPlanId())
                    || !requestHash.equals(replay.get().getRequestHash()))
                throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
            return planRepository.findById(planId).map(this::view)
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        }
        String familyCode = planRepository.findFamilyCodeById(planId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        List<SubscriptionPlan> family = planRepository.findFamilyForUpdate(familyCode);
        SubscriptionPlan targetPlan = family.stream().filter(item -> Objects.equals(item.getId(), planId)).findFirst()
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (target.equals(targetPlan.getStatus())) {
            operationRepository.saveAndFlush(SubscriptionPlanAdminOperation.record(operationKey,target,planId,target,requestHash,now));
            return view(targetPlan);
        }
        if ("ACTIVE".equals(target)) {
            if (targetPlan.getPrice() == null || targetPlan.getPrice().signum() <= 0)
                throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                        "Non-purchasable compatibility plans cannot be activated.");
            if (targetPlan.getActivatedAt() != null) {
                throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                        "A retired plan version cannot be reactivated; create a new version.");
            }
            for (SubscriptionPlan current : family) if (!Objects.equals(current.getId(), planId)
                    && "ACTIVE".equals(current.getStatus())) { current.setStatus("INACTIVE"); current.setDeactivatedAt(now);
                planRepository.saveAndFlush(current);
                audit(current, "PLAN_VERSION_INACTIVE", "ACTIVE", "INACTIVE", correlationId,
                        hash(idempotencyKey + ":AUTO:" + current.getId()), "Automatically retired by activation of version " + planId); }
            targetPlan.setStatus("ACTIVE"); targetPlan.setActivatedAt(now); targetPlan.setDeactivatedAt(null);
        } else { targetPlan.setStatus("INACTIVE"); targetPlan.setDeactivatedAt(now); }
        planRepository.saveAndFlush(targetPlan);
        operationRepository.saveAndFlush(SubscriptionPlanAdminOperation.record(operationKey, target, planId, target,requestHash, now));
        audit(targetPlan, "PLAN_VERSION_" + target, "ACTIVE".equals(target) ? "INACTIVE" : "ACTIVE",
                target, correlationId, hash(idempotencyKey + ":" + reason), reason);
        return view(targetPlan);
    }

    private Validated validate(CreateVersionCommand command) {
        if (command == null) throw new IllegalArgumentException("plan version request is required.");
        String family = normalizeKey(command.familyCode());
        String vi = requireText(command.nameVi(), "nameVi", 255);
        String en = command.nameEn() == null ? null : requireText(command.nameEn(), "nameEn", 255);
        String billing = normalizeEnum(command.billingType(), Set.of("MONTHLY","YEARLY","ONCE"), "billingType");
        if (command.price() == null || command.price().scale() > 0 || command.price().signum() <= 0)
            throw new IllegalArgumentException("price must be a positive whole VND amount.");
        String unit = normalizeEnum(command.durationUnit(), Set.of("DAY","MONTH","YEAR","LIFETIME"), "durationUnit");
        boolean lifetime = "LIFETIME".equals(unit);
        Integer duration = lifetime ? null : command.durationValue();
        if (!lifetime && (duration == null || duration < 1 || duration > 120))
            throw new IllegalArgumentException("duration is invalid.");
        if (!("MONTHLY".equals(billing) && "MONTH".equals(unit)
                || "YEARLY".equals(billing) && "YEAR".equals(unit)
                || "ONCE".equals(billing) && lifetime))
            throw new IllegalArgumentException("billingType and durationUnit are inconsistent.");
        Map<String,Integer> limits = new LinkedHashMap<>();
        if (command.features() == null || command.features().isEmpty()) throw new IllegalArgumentException("features are required.");
        for (FeatureLimit item : command.features()) {
            String code = normalizeKey(item.code()); if (item.limit() == null || item.limit() < -1)
                throw new IllegalArgumentException("feature limit is invalid.");
            if (limits.putIfAbsent(code, item.limit()) != null) throw new IllegalArgumentException("feature keys must be unique.");
        }
        Set<String> known = new HashSet<>(); featureRepository.findByCodeIn(limits.keySet()).forEach(item -> known.add(item.getCode()));
        if (!known.equals(limits.keySet())) throw new IllegalArgumentException("Unknown feature key.");
        List<FeatureLimit> features = limits.entrySet().stream().map(e -> new FeatureLimit(e.getKey(), e.getValue())).toList();
        return new Validated(family, vi, en, billing, command.price(), lifetime, duration, unit, features);
    }

    private PlanVersionView view(SubscriptionPlan plan) {
        List<FeatureLimit> features = plan.getFeatures() == null ? List.of() : plan.getFeatures().stream()
                .sorted(Comparator.comparing(PlanFeature::getFeatureCode))
                .map(item -> new FeatureLimit(item.getFeatureCode(), item.getLimitValue())).toList();
        return new PlanVersionView(plan.getId(), plan.getFamilyCode(), plan.getVersionNumber(), plan.getCode(),
                plan.getNameVi(), plan.getNameEn(), plan.getBillingType(), plan.getPrice(), "VND",
                Boolean.TRUE.equals(plan.getIsLifetime()), plan.getDurationValue(), plan.getDurationUnit(),
                plan.getStatus(), plan.getRecordVersion(), features, plan.getCreatedAt(), plan.getActivatedAt(), plan.getDeactivatedAt());
    }
    private void requireSystemAdmin() { if (!accessService.isSystemAdministrator()) throw new AccessDeniedException("System administrator permission is required."); }
    private String normalizeKey(String value) { String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid catalog key."); return key; }
    private String normalizeEnum(String value, Set<String> allowed, String field) { String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " is invalid."); return normalized; }
    private String requireText(String value, String field, int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required."); String text=value.trim(); if(text.length()>max) throw new IllegalArgumentException(field+" is too long."); return text; }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
    private void audit(SubscriptionPlan plan,String event,String before,String after,String correlation,String key,String reason){ User actor=accessService.currentUser(); auditService.append(new FinancialAuditService.AuditCommand("PLATFORM_BILLING",null,"SUBSCRIPTION_PLAN_VERSION",String.valueOf(plan.getId()),"USER",actor==null?null:actor.getId(),"ADMIN",before,after,reason,key,null,correlation,Map.of("familyCode",plan.getFamilyCode(),"version",plan.getVersionNumber(),"event",event))); }

    public record FeatureLimit(String code,Integer limit){}
    public record CreateVersionCommand(String familyCode,String nameVi,String nameEn,String billingType,BigDecimal price,Integer durationValue,String durationUnit,List<FeatureLimit> features){}
    public record PlanVersionView(Long id,String familyCode,Integer versionNumber,String versionCode,String nameVi,String nameEn,String billingType,BigDecimal price,String currency,boolean lifetime,Integer durationValue,String durationUnit,String status,Long recordVersion,List<FeatureLimit> features,LocalDateTime createdAt,LocalDateTime activatedAt,LocalDateTime deactivatedAt){}
    private record Validated(String familyCode,String nameVi,String nameEn,String billingType,BigDecimal price,boolean lifetime,Integer durationValue,String durationUnit,List<FeatureLimit> features){}
}
