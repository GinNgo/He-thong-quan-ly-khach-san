package com.hotel.services.impl;

import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppModuleRepository appModuleRepository;
    private final AppFunctionRepository appFunctionRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final com.hotel.repositories.HotelServiceRepository hotelServiceRepository;
    private final com.hotel.repositories.ReservationRepository reservationRepository;
    private final com.hotel.repositories.InvoiceRepository invoiceRepository;
    private final com.hotel.repositories.SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        repairSchema();

        AppModule systemModule = initModule("SYSTEM", "Hệ thống");
        AppModule hotelModule = initModule("HOTEL", "Khách sạn");
        AppModule financeModule = initModule("FINANCE", "Tài chính");

        initFunction(systemModule, FunctionCode.REPORT.name(), "Bảng điều khiển", "/admin/dashboard", "pi pi-chart-bar", 1);
        initFunction(systemModule, FunctionCode.SYSTEM.name(), "Khai báo trang", "/admin/modules", "pi pi-sitemap", 2);
        initFunction(systemModule, FunctionCode.ROLE.name(), "Vai trò", "/admin/roles", "pi pi-key", 3);
        initFunction(systemModule, FunctionCode.ROLE_PERMISSION.name(), "Phân quyền", "/admin/role-permissions", "pi pi-shield", 4);
        initFunction(systemModule, FunctionCode.USER.name(), "Người dùng", "/admin/users", "pi pi-users", 5);
        initFunction(systemModule, FunctionCode.AI_CHAT.name(), "AI Chatbot", "/ai", "pi pi-android", 6);

        initFunction(hotelModule, FunctionCode.CUSTOMER.name(), "Khách hàng", "/admin/customers", "pi pi-id-card", 1);
        initFunction(hotelModule, FunctionCode.ROOM_TYPE.name(), "Loại phòng", "/admin/room-types", "pi pi-list", 2);
        initFunction(hotelModule, FunctionCode.ROOM.name(), "Phòng", "/admin/rooms", "pi pi-home", 3);
        initFunction(hotelModule, FunctionCode.RESERVATION.name(), "Đặt phòng", "/admin/reservations", "pi pi-calendar", 4);
        initFunction(hotelModule, FunctionCode.HOTEL.name(), "Dịch vụ khách sạn", "/admin/services", "pi pi-box", 5);
        initFunction(hotelModule, FunctionCode.CHAT.name(), "Chat trực tuyến", "/admin/chat", "pi pi-comments", 6);

        initFunction(financeModule, FunctionCode.INVOICE.name(), "Hóa đơn", "/admin/invoices", "pi pi-file-o", 1);
        initFunction(financeModule, FunctionCode.FINANCE.name(), "Thanh toán", "/admin/payments", "pi pi-money-bill", 2);

        Role superAdminRole = initRole("SUPER_ADMIN", "Quản trị hệ thống", "Toàn quyền hệ thống.");
        Role adminRole = initRole("ADMIN", "Quản trị viên", "Toàn quyền hệ thống.");
        Role hotelAdminRole = initRole("HOTEL_ADMIN", "Quản lý khách sạn", "Quản lý vận hành khách sạn.");
        Role hotelManagerRole = initRole("HOTEL_MANAGER", "Quản lý chi nhánh", "Quản lý vận hành theo chi nhánh.");
        Role receptionistRole = initRole("RECEPTIONIST", "Lễ tân", "Tiếp nhận khách và xử lý đặt phòng.");
        Role accountantRole = initRole("ACCOUNTANT", "Kế toán", "Theo dõi hóa đơn và thanh toán.");
        initRole("CUSTOMER", "Khách hàng", "Tài khoản khách đặt phòng.");

        Hotel defaultHotel = ensureDefaultHotel();
        mapExistingRoomTypesToDefaultHotel(defaultHotel);
        ensureDefaultInventory(defaultHotel);

        int allActions = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE | ActionCode.EXPORT | ActionCode.APPROVE;
        syncAllPermissions(superAdminRole, allActions);
        syncAllPermissions(adminRole, allActions);
        seedDefaultRolePermissions(hotelAdminRole);
        seedDefaultRolePermissions(hotelManagerRole);
        seedReceptionistPermissions(receptionistRole);
        seedAccountantPermissions(accountantRole);

        ensureAdminUser(superAdminRole);
        
        Role customerRole = roleRepository.findByCode("CUSTOMER").orElse(null);
        ensureMockData(defaultHotel, hotelManagerRole, receptionistRole, customerRole);
        
        seedSubscriptionPlans();
    }

    private void repairSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE app_module ALTER COLUMN name NVARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE app_function ALTER COLUMN name NVARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE app_role ALTER COLUMN name NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("IF COL_LENGTH('app_role', 'description') IS NULL ALTER TABLE app_role ADD description NVARCHAR(500)");
            jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN name NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN address NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN city NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN country NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE hotels ALTER COLUMN description NVARCHAR(MAX)");
            jdbcTemplate.execute("ALTER TABLE room_types ALTER COLUMN name_vi NVARCHAR(255) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE room_types ALTER COLUMN description_vi NVARCHAR(MAX)");
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN full_name NVARCHAR(255)");
        } catch (Exception e) {
            System.out.println("Could not alter table columns: " + e.getMessage());
        }
    }

    private AppModule initModule(String code, String name) {
        AppModule module = appModuleRepository.findByCode(code);
        if (module == null) {
            module = new AppModule();
            module.setCode(code);
        }
        module.setName(name);
        return appModuleRepository.save(module);
    }

    private void initFunction(AppModule module, String code, String name, String url, String icon, int order) {
        AppFunction function = appFunctionRepository.findByCode(code);
        if (function == null) {
            function = new AppFunction();
            function.setCode(code);
        }
        function.setModule(module);
        function.setName(name);
        function.setUrl(url);
        function.setIcon(icon);
        function.setSortOrder(order);
        appFunctionRepository.save(function);
    }

    private Role initRole(String code, String name, String description) {
        Role role = roleRepository.findByCode(code).orElseGet(Role::new);
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    private Hotel ensureDefaultHotel() {
        Hotel defaultHotel = hotelRepository.findAll().stream().findFirst().orElse(null);
        if (defaultHotel == null) {
            defaultHotel = new Hotel();
            defaultHotel.setName("Grand Palace Hotel");
            defaultHotel.setDescription("A luxurious hotel with all modern amenities.");
            defaultHotel.setAddressLine("123 Test Street");
            defaultHotel.setProvinceId(1L);
            defaultHotel.setWardId(1L);
            defaultHotel.setStarRating(5);
            defaultHotel.setStatus("ACTIVE");
            defaultHotel = hotelRepository.save(defaultHotel);
        }

        return defaultHotel;
    }

    private void mapExistingRoomTypesToDefaultHotel(Hotel defaultHotel) {
        for (RoomType roomType : roomTypeRepository.findAll()) {
            if (roomType.getHotel() == null) {
                roomType.setHotel(defaultHotel);
                roomTypeRepository.save(roomType);
            }
        }
    }

    private void ensureDefaultInventory(Hotel hotel) {
        List<RoomType> hotelRoomTypes = roomTypeRepository.findByHotelId(hotel.getId());
        if (hotelRoomTypes.isEmpty()) {
            hotelRoomTypes = new ArrayList<>();
            hotelRoomTypes.add(createRoomType(hotel, "STANDARD", "Phòng tiêu chuẩn", "Standard Room", 2, "850000",
                    "Lựa chọn gọn gàng cho chuyến đi ngắn ngày."));
            hotelRoomTypes.add(createRoomType(hotel, "DELUXE", "Phòng Deluxe", "Deluxe Room", 3, "1250000",
                    "Phòng rộng hơn, phù hợp cặp đôi hoặc gia đình nhỏ."));
            hotelRoomTypes.add(createRoomType(hotel, "SUITE", "Phòng Suite", "Suite Room", 4, "2200000",
                    "Không gian riêng tư với khu tiếp khách và tầm nhìn đẹp."));
        }

        for (RoomType roomType : hotelRoomTypes) {
            if (roomRepository.findByRoomTypeId(roomType.getId()).isEmpty()) {
                for (int i = 1; i <= 3; i++) {
                    Room room = new Room();
                    room.setRoomNumber(String.format("%d-%s-%02d", hotel.getId(), roomType.getCode(), i));
                    room.setRoomType(roomType);
                    room.setFloor(i);
                    room.setStatus("AVAILABLE");
                    room.setDescriptionVi(roomType.getDescriptionVi());
                    room.setDescriptionEn(roomType.getDescriptionEn());
                    roomRepository.save(room);
                }
            }
        }
    }

    private RoomType createRoomType(Hotel hotel, String code, String nameVi, String nameEn, Integer maxGuest,
                                    String basePrice, String description) {
        RoomType roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode(code);
        roomType.setNameVi(nameVi);
        roomType.setNameEn(nameEn);
        roomType.setMaxGuest(maxGuest);
        roomType.setBasePrice(new BigDecimal(basePrice));
        roomType.setDescriptionVi(description);
        roomType.setDescriptionEn(description);
        return roomTypeRepository.save(roomType);
    }

    private void syncAllPermissions(Role role, int actionMask) {
        for (AppFunction function : appFunctionRepository.findAll()) {
            setPermission(role, function, actionMask);
        }
    }

    private void seedDefaultRolePermissions(Role role) {
        int manage = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE;
        int manageAndExport = manage | ActionCode.EXPORT;
        ensurePermission(role, FunctionCode.REPORT, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.CUSTOMER, manage);
        ensurePermission(role, FunctionCode.ROOM_TYPE, manage);
        ensurePermission(role, FunctionCode.ROOM, manage);
        ensurePermission(role, FunctionCode.RESERVATION, manage | ActionCode.APPROVE);
        ensurePermission(role, FunctionCode.HOTEL, manage);
        ensurePermission(role, FunctionCode.INVOICE, manageAndExport);
        ensurePermission(role, FunctionCode.FINANCE, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.CHAT, manage);
    }

    private void seedReceptionistPermissions(Role role) {
        ensurePermission(role, FunctionCode.REPORT, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.CUSTOMER, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.ROOM_TYPE, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.ROOM, ActionCode.VIEW | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.RESERVATION, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.INVOICE, ActionCode.VIEW | ActionCode.CREATE);
        ensurePermission(role, FunctionCode.CHAT, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
    }

    private void seedAccountantPermissions(Role role) {
        int invoiceMask = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE | ActionCode.EXPORT;
        ensurePermission(role, FunctionCode.REPORT, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.INVOICE, invoiceMask);
        ensurePermission(role, FunctionCode.FINANCE, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.EXPORT);
    }

    private void ensurePermission(Role role, FunctionCode functionCode, int actionMask) {
        AppFunction function = appFunctionRepository.findByCode(functionCode.name());
        if (function == null) {
            return;
        }
        RolePermission existing = rolePermissionRepository.findByRoleIdAndFunctionId(role.getId(), function.getId());
        if (existing == null) {
            setPermission(role, function, actionMask);
        }
    }

    private void setPermission(Role role, AppFunction function, int actionMask) {
        RolePermission existing = rolePermissionRepository.findByRoleIdAndFunctionId(role.getId(), function.getId());
        if (existing == null) {
            existing = new RolePermission();
            existing.setRole(role);
            existing.setFunction(function);
        }
        existing.setActionMask(actionMask);
        rolePermissionRepository.save(existing);
    }

    private void ensureAdminUser(Role superAdminRole) {
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            admin.setFullName("System Admin");
            admin.setEmail("admin@hotel.com");
            admin.setStatus("ACTIVE");
        }

        if (admin.getRoles() == null || admin.getRoles().stream().noneMatch(r -> r.getCode().equals("SUPER_ADMIN"))) {
            admin.setRoles(new java.util.HashSet<>(Set.of(superAdminRole)));
        }
        userRepository.save(admin);
    }

    private boolean hasEncodingNoise(String value) {
        return value != null && (value.indexOf('\u00C3') >= 0 || value.indexOf('\u00C2') >= 0
                || value.indexOf('\u00C4') >= 0 || value.contains("?"));
    }

    private void ensureMockData(Hotel hotel, Role managerRole, Role receptionistRole, Role customerRole) {
        ensureMockUser("manager1", "manager1", "Manager One", "manager1@hotel.com", "ACTIVE", managerRole, hotel);
        ensureMockUser("receptionist1", "receptionist1", "Receptionist One", "receptionist1@hotel.com", "ACTIVE", receptionistRole, hotel);
        
        User customer1 = ensureMockUser("customer1", "customer1", "Nguyen Van A", "nva@hotel.com", "ACTIVE", customerRole, null);
        User customer2 = ensureMockUser("customer2", "customer2", "Le Thi B", "ltb@hotel.com", "ACTIVE", customerRole, null);
        
        com.hotel.entities.HotelService breakfast = ensureMockService("SVC_BREAKFAST", "Ăn sáng Buffet", "Buffet sáng tiêu chuẩn quốc tế", new BigDecimal("150000"));
        com.hotel.entities.HotelService laundry = ensureMockService("SVC_LAUNDRY", "Dịch vụ giặt ủi", "Giặt sấy lấy ngay trong ngày", new BigDecimal("50000"));
        com.hotel.entities.HotelService airportTransfer = ensureMockService("SVC_TRANSFER", "Đưa đón sân bay", "Xe 4 chỗ đưa đón sân bay 2 chiều", new BigDecimal("350000"));

        ensureMockReservations(hotel, customer1, customer2);
    }

    private User ensureMockUser(String username, String password, String fullName, String email, String status, Role role, Hotel hotel) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setFullName(fullName);
            user.setEmail(email);
            user.setStatus(status);
            if (role != null) {
                user.setRoles(new java.util.HashSet<>(Set.of(role)));
            }
            if (hotel != null) {
                user.setHotel(hotel);
            }
            userRepository.save(user);
        }
        return user;
    }

    private com.hotel.entities.HotelService ensureMockService(String code, String name, String description, BigDecimal price) {
        return hotelServiceRepository.findAll().stream()
                .filter(s -> s.getCode().equals(code))
                .findFirst()
                .orElseGet(() -> {
                    com.hotel.entities.HotelService service = new com.hotel.entities.HotelService();
                    service.setCode(code);
                    service.setNameVi(name);
                    service.setNameEn(name);
                    service.setDescriptionVi(description);
                    service.setDescriptionEn(description);
                    service.setPrice(price);
                    service.setStatus("ACTIVE");
                    return hotelServiceRepository.save(service);
                });
    }

    private void ensureMockReservations(Hotel hotel, User customer1, User customer2) {
        List<com.hotel.entities.Reservation> existingReservations = reservationRepository.findAll();
        if (existingReservations.isEmpty()) {
            List<Room> rooms = roomRepository.findAll();
            if (rooms.size() >= 2) {
                // Reservation 1
                com.hotel.entities.Reservation res1 = new com.hotel.entities.Reservation();
                res1.setUser(customer1);
                res1.setHotel(hotel);
                res1.setRoom(rooms.get(0));
                res1.setCheckInDate(java.time.LocalDate.now().minusDays(1));
                res1.setCheckOutDate(java.time.LocalDate.now().plusDays(2));
                res1.setStatus("CHECKED_IN");
                res1.setTotalAmount(new BigDecimal("2500000"));
                res1.setGuests(2);
                res1 = reservationRepository.save(res1);

                com.hotel.entities.Invoice inv1 = new com.hotel.entities.Invoice();
                inv1.setInvoiceCode("INV-MOCK-001");
                inv1.setReservation(res1);
                inv1.setIssueDate(java.time.LocalDate.now());
                inv1.setTotalAmount(res1.getTotalAmount());
                inv1.setStatus("PENDING");
                invoiceRepository.save(inv1);

                // Reservation 2
                com.hotel.entities.Reservation res2 = new com.hotel.entities.Reservation();
                res2.setUser(customer2);
                res2.setHotel(hotel);
                res2.setRoom(rooms.get(1));
                res2.setCheckInDate(java.time.LocalDate.now().minusDays(5));
                res2.setCheckOutDate(java.time.LocalDate.now().minusDays(3));
                res2.setStatus("COMPLETED");
                res2.setTotalAmount(new BigDecimal("1850000"));
                res2.setGuests(1);
                res2 = reservationRepository.save(res2);

                com.hotel.entities.Invoice inv2 = new com.hotel.entities.Invoice();
                inv2.setInvoiceCode("INV-MOCK-002");
                inv2.setReservation(res2);
                inv2.setIssueDate(java.time.LocalDate.now().minusDays(3));
                inv2.setTotalAmount(res2.getTotalAmount());
                inv2.setStatus("PAID");
                invoiceRepository.save(inv2);
            }
        }
    }

    private void seedSubscriptionPlans() {
        if (subscriptionPlanRepository.count() == 0) {
            com.hotel.entities.SubscriptionPlan freePlan = new com.hotel.entities.SubscriptionPlan();
            freePlan.setCode("FREE");
            freePlan.setNameVi("Gói Miễn phí");
            freePlan.setNameEn("Free Plan");
            freePlan.setBillingType("MONTHLY");
            freePlan.setPrice(BigDecimal.ZERO);
            freePlan.setIsLifetime(true);
            freePlan.setStatus("ACTIVE");
            subscriptionPlanRepository.save(freePlan);

            com.hotel.entities.SubscriptionPlan standardPlan = new com.hotel.entities.SubscriptionPlan();
            standardPlan.setCode("STANDARD");
            standardPlan.setNameVi("Gói Tiêu chuẩn");
            standardPlan.setNameEn("Standard Plan");
            standardPlan.setBillingType("MONTHLY");
            standardPlan.setPrice(new BigDecimal("500000"));
            standardPlan.setIsLifetime(false);
            standardPlan.setStatus("ACTIVE");
            subscriptionPlanRepository.save(standardPlan);
            
            com.hotel.entities.SubscriptionPlan premiumPlan = new com.hotel.entities.SubscriptionPlan();
            premiumPlan.setCode("PREMIUM");
            premiumPlan.setNameVi("Gói Cao cấp");
            premiumPlan.setNameEn("Premium Plan");
            premiumPlan.setBillingType("YEARLY");
            premiumPlan.setPrice(new BigDecimal("5000000"));
            premiumPlan.setIsLifetime(false);
            premiumPlan.setStatus("ACTIVE");
            subscriptionPlanRepository.save(premiumPlan);
        }
    }
}
