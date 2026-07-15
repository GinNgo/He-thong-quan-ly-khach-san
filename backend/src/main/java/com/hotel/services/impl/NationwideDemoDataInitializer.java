package com.hotel.services.impl;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import com.hotel.util.VietnameseTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Profile({"development", "demo", "test"})
@ConditionalOnProperty(prefix = "app.demo-data", name = {"enabled", "nationwide-property-seed"}, havingValue = "true")
@RequiredArgsConstructor
public class NationwideDemoDataInitializer {
    private static final List<String> PROPERTY_TYPES = List.of(
            "HOTEL", "MOTEL", "HOMESTAY", "HOSTEL", "APARTMENT", "VILLA", "RESORT", "GUEST_HOUSE");
    private static final List<String> BRANDS = List.of(
            "LuxeStay Riverside", "Aurora Central", "Green Hill Homestay", "Ocean Pearl",
            "Mekong Garden Inn", "Sunleaf Residence", "Harbor Light", "Cloud Garden");
    private static final List<String> STREETS = List.of(
            "Hoa Biển", "Bình Minh", "Mây Trắng", "Vườn Xanh", "Ánh Dương", "Sông Việt", "Đồi Gió", "Sen Hồng");
    private static final List<String> IMAGE_URLS = List.of(
            "/assets/properties/hotel-city-01.webp", "/assets/properties/hotel-city-02.webp",
            "/assets/properties/motel-01.webp", "/assets/properties/homestay-01.webp",
            "/assets/properties/hostel-01.webp", "/assets/properties/apartment-01.webp",
            "/assets/properties/villa-01.webp", "/assets/properties/resort-01.webp",
            "/assets/properties/guest-house-01.webp", "/assets/properties/hotel-beach-01.webp",
            "/assets/properties/hotel-room-01.webp", "/assets/properties/hotel-room-02.webp");
    private static final List<String> OWNER_CATEGORIES = List.of(
            "FREE", "NO_PLAN", "STANDARD", "BUSINESS", "LIFETIME", "EXPIRED");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final HotelRepository hotelRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomTypeImageRepository roomTypeImageRepository;
    private final RoomRepository roomRepository;
    private final HotelServiceRepository hotelServiceRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final SubscriptionOrderRepository subscriptionOrderRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final SoftwareContractRepository softwareContractRepository;
    private final DemoSeedProgressRepository progressRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo-data.coverage-mode:STANDARD}")
    private String coverageMode;
    @Value("${app.demo-data.properties-per-province:5}")
    private int propertiesPerProvince;
    @Value("${app.demo-data.properties-per-ward:1}")
    private int propertiesPerWard;
    @Value("${app.demo-data.max-total-properties:5000}")
    private int maxTotalProperties;
    @Value("${app.demo-data.batch-size:50}")
    private int batchSize;
    @Value("${DEMO_ACCOUNT_PASSWORD:}")
    private String demoAccountPassword;

    @EventListener(ApplicationReadyEvent.class)
    @Order(300)
    public void seed() {
        if (demoAccountPassword == null || demoAccountPassword.isBlank()) {
            throw new IllegalStateException("DEMO_ACCOUNT_PASSWORD phải được cấu hình khi bật nationwide demo seed.");
        }
        String mode = "FULL_COVERAGE".equalsIgnoreCase(coverageMode) ? "FULL_COVERAGE" : "STANDARD";
        transactionTemplate.executeWithoutResult(status -> seedPlansAndRole());
        List<SeedTarget> targets = loadTargets(mode);
        int success = 0;
        int failed = 0;
        for (int i = 0; i < targets.size(); i++) {
            SeedTarget target = targets.get(i);
            try {
                final int sequence = i + 1;
                transactionTemplate.executeWithoutResult(status -> seedProperty(target, sequence, mode));
                success++;
            } catch (RuntimeException exception) {
                failed++;
                saveFailure(target, mode, exception);
                log.error("Demo seed thất bại tại {}: {}", target.seedKey(), exception.getMessage());
            }
            if ((i + 1) % Math.max(batchSize, 1) == 0 || i + 1 == targets.size()) {
                log.info("Demo seed progress: {}/{} (success={}, failed={})", i + 1, targets.size(), success, failed);
            }
        }
        logReport(mode, targets.size(), success, failed);
    }

    private List<SeedTarget> loadTargets(String mode) {
        List<SeedTarget> result = new ArrayList<>();
        if ("FULL_COVERAGE".equals(mode)) {
            List<SeedLocation> wards = jdbcTemplate.query("""
                    SELECT p.id,p.source_code,p.name_vi,w.id,w.source_code,w.name_vi
                    FROM locations w JOIN locations p ON p.id=w.parent_id
                    WHERE w.location_type='WARD' AND w.status='ACTIVE' AND p.location_type='PROVINCE'
                    ORDER BY p.source_code,w.source_code
                    """, (rs, row) -> new SeedLocation(rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getLong(4), rs.getString(5), rs.getString(6)));
            outer: for (SeedLocation ward : wards) {
                for (int i = 1; i <= Math.max(propertiesPerWard, 1); i++) {
                    if (result.size() >= maxTotalProperties) break outer;
                    result.add(target(ward, i));
                }
            }
        } else {
            List<SeedLocation> rows = jdbcTemplate.query("""
                    SELECT p.id,p.source_code,p.name_vi,w.id,w.source_code,w.name_vi,
                           ROW_NUMBER() OVER(PARTITION BY p.id ORDER BY w.source_code) ward_order
                    FROM locations p JOIN locations w ON w.parent_id=p.id AND w.location_type='WARD' AND w.status='ACTIVE'
                    WHERE p.location_type='PROVINCE' AND p.status='ACTIVE'
                    ORDER BY p.source_code,ward_order
                    """, (rs, row) -> new SeedLocation(rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getLong(4), rs.getString(5), rs.getString(6)));
            Map<Long, Integer> provinceCounts = new HashMap<>();
            for (SeedLocation row : rows) {
                int count = provinceCounts.getOrDefault(row.provinceId(), 0);
                if (count >= Math.max(3, Math.min(propertiesPerProvince, 5)) || result.size() >= maxTotalProperties) continue;
                provinceCounts.put(row.provinceId(), count + 1);
                result.add(target(row, count + 1));
            }
        }
        return result;
    }

    private SeedTarget target(SeedLocation location, int ordinal) {
        String provinceCode = safeCode(location.provinceCode());
        String wardCode = safeCode(location.wardCode());
        String seedKey = "DEMO-PROVINCE-" + provinceCode + "-WARD-" + wardCode + "-PROPERTY-" + String.format("%02d", ordinal);
        return new SeedTarget(seedKey, location, ordinal);
    }

    private void seedPlansAndRole() {
        Role ownerRole = roleRepository.findByCode("PROPERTY_OWNER").orElseGet(Role::new);
        ownerRole.setCode("PROPERTY_OWNER");
        ownerRole.setName("Chủ cơ sở");
        ownerRole.setDescription("Quản lý các cơ sở được gán và giới hạn theo gói dịch vụ.");
        roleRepository.save(ownerRole);

        seedPlan("FREE", "Gói Miễn phí", "Free Plan", "MONTHLY", BigDecimal.ZERO, false,
                Map.of("MAX_PROPERTIES", 1, "MAX_ROOM_TYPES", 3, "MAX_ROOMS", 10, "MAX_IMAGES", 15, "MAX_STAFF", 0));
        seedPlan("STANDARD", "Gói Tiêu chuẩn", "Standard Plan", "YEARLY", new BigDecimal("6000000"), false,
                Map.of("MAX_PROPERTIES", 3, "MAX_ROOM_TYPES", 20, "MAX_ROOMS", 100, "MAX_IMAGES", 300, "MAX_STAFF", 10));
        seedPlan("BUSINESS", "Gói Doanh nghiệp", "Business Plan", "YEARLY", new BigDecimal("15000000"), false,
                Map.of("MAX_PROPERTIES", 10, "MAX_ROOM_TYPES", 100, "MAX_ROOMS", 1000, "MAX_IMAGES", 3000, "MAX_STAFF", 100));
        seedPlan("LIFETIME", "Gói Vĩnh viễn", "Lifetime Plan", "ONCE", new BigDecimal("50000000"), true,
                Map.of("MAX_PROPERTIES", -1, "MAX_ROOM_TYPES", -1, "MAX_ROOMS", -1, "MAX_IMAGES", -1, "MAX_STAFF", -1));
    }

    private void seedPlan(String code, String nameVi, String nameEn, String billingType, BigDecimal price,
                          boolean lifetime, Map<String, Integer> limits) {
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(code).orElseGet(SubscriptionPlan::new);
        plan.setCode(code);
        plan.setNameVi(nameVi);
        plan.setNameEn(nameEn);
        plan.setBillingType(billingType);
        plan.setPrice(price);
        plan.setIsLifetime(lifetime);
        plan.setStatus("ACTIVE");
        plan = subscriptionPlanRepository.save(plan);
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            PlanFeature feature = planFeatureRepository.findByPlanIdAndFeatureCode(plan.getId(), entry.getKey()).orElseGet(PlanFeature::new);
            feature.setPlan(plan);
            feature.setFeatureCode(entry.getKey());
            feature.setLimitValue(entry.getValue());
            planFeatureRepository.save(feature);
        }
    }

    private void seedProperty(SeedTarget target, int sequence, String mode) {
        markStarted(target, mode);
        Hotel hotel = hotelRepository.findBySeedKey(target.seedKey()).orElseGet(Hotel::new);
        int style = Math.floorMod(target.seedKey().hashCode(), PROPERTY_TYPES.size());
        String brand = BRANDS.get(style);
        String nameVi = brand + " " + target.location().wardName();
        String code = "D-" + safeCode(target.location().provinceCode()) + "-" + safeCode(target.location().wardCode()) + "-" + target.ordinal();
        String address = (20 + sequence % 180) + " Đường " + STREETS.get(style) + ", "
                + target.location().wardName() + ", " + target.location().provinceName();
        hotel.setSeedKey(target.seedKey());
        hotel.setCode(code);
        hotel.setSlug(VietnameseTextNormalizer.normalize(nameVi).replace(' ', '-') + "-" + sequence);
        hotel.setName(nameVi);
        hotel.setNameVi(nameVi);
        hotel.setNameEn(brand + " Demo Property");
        hotel.setDescriptionVi("Cơ sở lưu trú demo phục vụ phát triển, kiểm thử và trình diễn; không phải hồ sơ doanh nghiệp thật.");
        hotel.setDescriptionEn("Demo accommodation for development and testing; not a real business listing.");
        hotel.setDescription(hotel.getDescriptionVi());
        hotel.setAddressLine(address);
        hotel.setCity(target.location().provinceName());
        hotel.setCountry("Việt Nam");
        hotel.setProvinceId(target.location().provinceId());
        hotel.setWardId(target.location().wardId());
        hotel.setLatitude(8.5 + (sequence % 145) / 10.0);
        hotel.setLongitude(102.0 + (sequence % 70) / 10.0);
        hotel.setPropertyType(PROPERTY_TYPES.get(style));
        hotel.setStarRating(2 + sequence % 4);
        hotel.setMainImage(IMAGE_URLS.get(style % IMAGE_URLS.size()));
        hotel.setMinPrice(450000D + style * 50000D);
        hotel.setMaxPrice(1800000D + style * 100000D);
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setCheckinTime("14:00");
        hotel.setCheckoutTime("12:00");
        hotel.setPhone(String.format("000-%03d-%04d", sequence % 1000, sequence));
        hotel.setEmail(String.format("property.%04d@example.com", sequence));
        hotel.setWebsite("https://example.com/demo/property-" + sequence);
        hotel.setAverageRating(null);
        hotel.setReviewCount(0);
        hotel.setExternalProvider(null);
        hotel.setExternalId(null);
        hotel.setIsDemo(true);
        hotel.setDataSource("DEMO");
        hotel = hotelRepository.saveAndFlush(hotel);

        seedPropertyImages(hotel, style);
        seedRoomInventory(hotel, style);
        seedService(hotel, sequence);
        seedOwner(hotel, sequence);
        markCompleted(target, mode);
    }

    private void seedPropertyImages(Hotel hotel, int style) {
        Map<Integer, PropertyImage> existing = new HashMap<>();
        propertyImageRepository.findByHotelId(hotel.getId()).stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsDemo()))
                .forEach(image -> existing.put(image.getSortOrder(), image));
        for (int i = 0; i < 4; i++) {
            String url = IMAGE_URLS.get((style + i) % IMAGE_URLS.size());
            PropertyImage image = existing.getOrDefault(i, new PropertyImage());
            image.setHotel(hotel);
            image.setImageUrl(url);
            image.setIsPrimary(i == 0);
            image.setSortOrder(i);
            image.setAltTextVi("Ảnh demo " + hotel.getNameVi());
            image.setAltTextEn("Demo image of " + hotel.getNameEn());
            image.setIsDemo(true);
            propertyImageRepository.save(image);
        }
    }

    private void seedRoomInventory(Hotel hotel, int style) {
        List<RoomTemplate> templates = new ArrayList<>(List.of(
                new RoomTemplate("SINGLE", "Phòng đơn", "Single Room", "SINGLE", 1, 1, 1, 2, "20", "550000", List.of("101", "102")),
                new RoomTemplate("DOUBLE", "Phòng đôi", "Double Room", "DOUBLE", 1, 2, 1, 3, "28", "850000", List.of("201", "202", "203")),
                new RoomTemplate("FAMILY", "Phòng gia đình", "Family Room", "MULTIPLE", 2, 4, 2, 6, "42", "1450000", List.of("301"))));
        if (style % 2 == 0) templates.add(new RoomTemplate("TWIN", "Phòng hai giường", "Twin Room", "TWIN", 2, 2, 1, 3, "30", "950000", List.of("401", "402")));
        if (style % 3 == 0) templates.add(new RoomTemplate("SUITE", "Phòng Suite", "Suite", "KING", 1, 2, 2, 4, "48", "1850000", List.of("501")));
        for (RoomTemplate template : templates) {
            RoomType roomType = roomTypeRepository.findByCodeAndHotelId(template.code(), hotel.getId()).orElseGet(RoomType::new);
            roomType.setHotel(hotel);
            roomType.setCode(template.code());
            roomType.setNameVi(template.nameVi());
            roomType.setNameEn(template.nameEn());
            roomType.setDescriptionVi(template.nameVi() + " demo với thông tin sức chứa rõ ràng.");
            roomType.setDescriptionEn("Demo " + template.nameEn().toLowerCase(Locale.ROOT) + " with declared capacity.");
            roomType.setArea(new BigDecimal(template.area()));
            roomType.setBedType(template.bedType());
            roomType.setBedCount(template.bedCount());
            roomType.setMaxAdults(template.maxAdults());
            roomType.setMaxChildren(template.maxChildren());
            roomType.setMaxGuests(template.maxGuests());
            roomType.setMaxGuest(template.maxGuests());
            roomType.setBasePrice(demoPrice(template.code(), hotel.getId()));
            roomType.setStatus("ACTIVE");
            roomType.setIsDemo(true);
            roomType = roomTypeRepository.saveAndFlush(roomType);
            seedRoomTypeImages(roomType, style);
            for (String roomNumber : template.roomNumbers()) {
                Room room = roomRepository.findByHotelIdAndRoomNumber(hotel.getId(), roomNumber).orElseGet(Room::new);
                boolean newRoom = room.getId() == null;
                room.setHotel(hotel);
                room.setRoomType(roomType);
                room.setRoomNumber(roomNumber);
                room.setFloor(Integer.parseInt(roomNumber.substring(0, 1)));
                if (newRoom) {
                    room.setStatus("AVAILABLE");
                    room.setHousekeepingStatus("CLEAN");
                    room.setMaintenanceStatus("NONE");
                }
                room.setMaxGuests(template.maxGuests());
                room.setDescriptionVi("Phòng vật lý demo số " + roomNumber + ".");
                room.setDescriptionEn("Demo physical room " + roomNumber + ".");
                room.setIsDemo(true);
                roomRepository.save(room);
            }
        }
    }

    private void seedRoomTypeImages(RoomType roomType, int style) {
        Map<Integer, RoomTypeImage> existing = new HashMap<>();
        roomTypeImageRepository.findByRoomTypeIdOrderBySortOrderAsc(roomType.getId())
                .stream().filter(image -> Boolean.TRUE.equals(image.getIsDemo()))
                .forEach(image -> existing.put(image.getSortOrder(), image));
        List<String> urls = roomTypeImages(roomType.getCode());
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            RoomTypeImage image = existing.getOrDefault(i, new RoomTypeImage());
            image.setRoomType(roomType);
            image.setImageUrl(url);
            image.setIsPrimary(i == 0);
            image.setSortOrder(i);
            image.setAltTextVi("Ảnh demo " + roomType.getNameVi());
            image.setAltTextEn("Demo " + roomType.getNameEn());
            image.setIsDemo(true);
            roomTypeImageRepository.save(image);
        }
    }

    private BigDecimal demoPrice(String code, Long hotelId) {
        long variant = Math.floorMod(hotelId, 7);
        return switch (code) {
            case "SINGLE" -> BigDecimal.valueOf(350000 + variant % 5 * 50000);
            case "DOUBLE" -> BigDecimal.valueOf(500000 + variant % 6 * 75000);
            case "TWIN" -> BigDecimal.valueOf(550000 + variant % 6 * 80000);
            case "FAMILY" -> BigDecimal.valueOf(850000 + variant % 6 * 125000);
            case "SUITE" -> BigDecimal.valueOf(1200000 + variant * 250000);
            default -> BigDecimal.valueOf(450000 + variant * 70000);
        };
    }

    private List<String> roomTypeImages(String code) {
        return switch (code) {
            case "SINGLE" -> List.of("/assets/room-types/single-room-01.webp", "/assets/room-types/double-room-02.webp");
            case "DOUBLE" -> List.of("/assets/room-types/double-room-01.webp", "/assets/room-types/double-room-02.webp");
            case "TWIN" -> List.of("/assets/room-types/twin-room-01.webp", "/assets/room-types/double-room-02.webp");
            case "FAMILY" -> List.of("/assets/room-types/family-room-01.webp", "/assets/room-types/twin-room-01.webp");
            default -> List.of("/assets/room-types/suite-room-01.webp", "/assets/room-types/suite-room-02.webp");
        };
    }

    private void seedService(Hotel hotel, int sequence) {
        String code = hotel.getCode() + "-BREAKFAST";
        HotelService service = hotelServiceRepository.findByCode(code).orElseGet(HotelService::new);
        service.setCode(code);
        service.setHotel(hotel);
        service.setSystemService(false);
        service.setNameVi("Ăn sáng demo tại cơ sở");
        service.setNameEn("Demo property breakfast");
        service.setDescriptionVi("Dịch vụ demo; đơn giá được lưu snapshot khi khách sử dụng.");
        service.setDescriptionEn("Demo service with price snapshot on usage.");
        service.setPrice(new BigDecimal(100000 + (sequence % 5) * 10000));
        service.setStatus("ACTIVE");
        hotelServiceRepository.save(service);
    }

    private void seedOwner(Hotel hotel, int sequence) {
        String category = OWNER_CATEGORIES.get((sequence - 1) % OWNER_CATEGORIES.size());
        String email = String.format("owner.%s%04d@example.com", category.toLowerCase(Locale.ROOT).replace('_', '.'), sequence);
        User owner = userRepository.findByEmail(email).orElseGet(User::new);
        boolean isNew = owner.getId() == null;
        owner.setUsername(email);
        owner.setEmail(email);
        owner.setFullName("Chủ cơ sở demo " + String.format("%04d", sequence));
        owner.setPhone(String.format("000-900-%04d", sequence));
        owner.setStatus("ACTIVE");
        owner.setHotel(hotel);
        if (isNew) owner.setPasswordHash(passwordEncoder.encode(demoAccountPassword));
        Role role = roleRepository.findByCode("PROPERTY_OWNER").orElseThrow();
        Set<Role> roles = owner.getRoles() == null ? new HashSet<>() : new HashSet<>(owner.getRoles());
        roles.add(role);
        owner.setRoles(roles);
        owner = userRepository.save(owner);

        UserProperty mapping = userPropertyRepository.findByUserIdAndHotelIdAndRelationshipType(owner.getId(), hotel.getId(), "OWNER")
                .orElseGet(UserProperty::new);
        mapping.setUser(owner);
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setIsPrimaryOwner(true);
        mapping.setStatus("ACTIVE");
        if (mapping.getStartDate() == null) mapping.setStartDate(LocalDateTime.now());
        userPropertyRepository.save(mapping);

        if (!"NO_PLAN".equals(category)) seedSubscription(owner, hotel, category, sequence);
    }

    private void seedSubscription(User owner, Hotel hotel, String category, int sequence) {
        String planCode = switch (category) {
            case "FREE" -> "FREE";
            case "BUSINESS" -> "BUSINESS";
            case "LIFETIME" -> "LIFETIME";
            default -> "STANDARD";
        };
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode).orElseThrow();
        AccountSubscription subscription = accountSubscriptionRepository
                .findFirstByUserIdAndPlanCodeOrderByStartAtDesc(owner.getId(), planCode).orElseGet(AccountSubscription::new);
        subscription.setUser(owner);
        subscription.setPlan(plan);
        subscription.setStartAt(LocalDateTime.now().minusMonths("EXPIRED".equals(category) ? 18 : 1));
        subscription.setIsLifetime("LIFETIME".equals(category));
        subscription.setEndAt("LIFETIME".equals(category) || "FREE".equals(category) ? null
                : "EXPIRED".equals(category) ? LocalDateTime.now().minusMonths(6) : LocalDateTime.now().plusYears(1));
        subscription.setStatus("EXPIRED".equals(category) ? "EXPIRED" : "ACTIVE");
        accountSubscriptionRepository.save(subscription);

        if (Set.of("STANDARD", "BUSINESS", "LIFETIME", "EXPIRED").contains(category)) {
            String orderCode = String.format("DEMO-SUB-%04d", sequence);
            SubscriptionOrder order = subscriptionOrderRepository.findByOrderCode(orderCode).orElseGet(SubscriptionOrder::new);
            order.setOrderCode(orderCode);
            order.setUser(owner);
            order.setPlan(plan);
            order.setBillingType(plan.getBillingType());
            order.setDurationValue(plan.getIsLifetime() ? null : 1);
            order.setSubtotal(plan.getPrice());
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setTaxAmount(BigDecimal.ZERO);
            order.setTotalAmount(plan.getPrice());
            order.setCurrency("VND");
            order.setStatus("EXPIRED".equals(category) ? "EXPIRED" : "ACTIVATED");
            order = subscriptionOrderRepository.save(order);

            SubscriptionPayment payment = subscriptionPaymentRepository.findFirstByOrderId(order.getId()).orElseGet(SubscriptionPayment::new);
            payment.setOrder(order);
            payment.setPaymentMethod("BANK_TRANSFER");
            payment.setAmount(plan.getPrice());
            payment.setTransactionCode("DEMO-TXN-" + sequence);
            payment.setPaymentStatus("SUCCESS");
            payment.setPaidAt(subscription.getStartAt());
            payment.setVerifiedBy("DEMO_SEED");
            payment.setVerifiedAt(subscription.getStartAt());
            subscriptionPaymentRepository.save(payment);

            String contractNo = String.format("DEMO-CONTRACT-%04d", sequence);
            SoftwareContract contract = softwareContractRepository.findByContractNo(contractNo).orElseGet(SoftwareContract::new);
            contract.setContractNo(contractNo);
            contract.setUser(owner);
            contract.setProperty(hotel);
            contract.setPlan(plan);
            contract.setOrder(order);
            contract.setContractType(plan.getIsLifetime() ? "LIFETIME_PURCHASE" : "YEARLY_RENTAL");
            contract.setStartDate(subscription.getStartAt());
            contract.setEndDate(subscription.getEndAt());
            contract.setIsLifetime(subscription.getIsLifetime());
            contract.setContractValue(plan.getPrice());
            contract.setCurrency("VND");
            contract.setStatus("EXPIRED".equals(category) ? "EXPIRED" : "ACTIVE");
            contract.setSignedAt(subscription.getStartAt());
            contract.setApprovedBy("DEMO_SEED");
            softwareContractRepository.save(contract);
        }
    }

    private void markStarted(SeedTarget target, String mode) {
        DemoSeedProgress progress = progressRepository.findById(target.seedKey()).orElseGet(DemoSeedProgress::new);
        progress.setSeedKey(target.seedKey());
        progress.setCoverageMode(mode);
        progress.setLocation(jdbcTemplate.queryForObject("SELECT id FROM locations WHERE id=?", (rs, row) -> {
            Location location = new Location();
            location.setId(rs.getLong(1));
            return location;
        }, target.location().wardId()));
        progress.setStatus("RUNNING");
        progress.setAttemptCount((progress.getAttemptCount() == null ? 0 : progress.getAttemptCount()) + 1);
        progress.setStartedAt(LocalDateTime.now());
        progress.setErrorMessage(null);
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
    }

    private void markCompleted(SeedTarget target, String mode) {
        DemoSeedProgress progress = progressRepository.findById(target.seedKey()).orElseThrow();
        progress.setCoverageMode(mode);
        progress.setStatus("COMPLETED");
        progress.setCompletedAt(LocalDateTime.now());
        progress.setUpdatedAt(LocalDateTime.now());
        progressRepository.save(progress);
    }

    private void saveFailure(SeedTarget target, String mode, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(status -> {
            DemoSeedProgress progress = progressRepository.findById(target.seedKey()).orElseGet(DemoSeedProgress::new);
            progress.setSeedKey(target.seedKey());
            progress.setCoverageMode(mode);
            progress.setStatus("FAILED");
            progress.setAttemptCount((progress.getAttemptCount() == null ? 0 : progress.getAttemptCount()) + 1);
            progress.setErrorMessage(String.valueOf(exception.getMessage()));
            progress.setUpdatedAt(LocalDateTime.now());
            progressRepository.save(progress);
        });
    }

    private void logReport(String mode, int requested, int success, int failed) {
        long hotels = hotelRepository.countByIsDemoTrue();
        long owners = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT user_id) FROM user_properties WHERE relationship_type='OWNER' AND status='ACTIVE'", Long.class);
        long roomTypes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_types WHERE is_demo=1", Long.class);
        long rooms = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms WHERE is_demo=1", Long.class);
        long images = jdbcTemplate.queryForObject("SELECT (SELECT COUNT(*) FROM property_images WHERE is_demo=1)+(SELECT COUNT(*) FROM room_type_images WHERE is_demo=1)", Long.class);
        log.info("Demo seed report mode={}, requested={}, success={}, failed={}, hotels={}, owners={}, roomTypes={}, rooms={}, images={}",
                mode, requested, success, failed, hotels, owners, roomTypes, rooms, images);
    }

    private String safeCode(String value) {
        return value == null ? "NA" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private record SeedLocation(Long provinceId, String provinceCode, String provinceName,
                                Long wardId, String wardCode, String wardName) { }
    private record SeedTarget(String seedKey, SeedLocation location, int ordinal) { }
    private record RoomTemplate(String code, String nameVi, String nameEn, String bedType, int bedCount,
                                int maxAdults, int maxChildren, int maxGuests, String area, String price,
                                List<String> roomNumbers) { }
}
