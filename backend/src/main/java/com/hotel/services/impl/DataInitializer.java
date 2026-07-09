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
        }

        if (defaultHotel.getId() == null || hasEncodingNoise(defaultHotel.getName()) || hasEncodingNoise(defaultHotel.getAddress())
                || hasEncodingNoise(defaultHotel.getCity()) || hasEncodingNoise(defaultHotel.getCountry())) {
            defaultHotel.setName("Đà Lạt Grand Hotel");
            defaultHotel.setAddress("123 Trần Phú");
            defaultHotel.setCity("Đà Lạt");
            defaultHotel.setCountry("Việt Nam");
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
    }

    private void seedReceptionistPermissions(Role role) {
        ensurePermission(role, FunctionCode.REPORT, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.CUSTOMER, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.ROOM_TYPE, ActionCode.VIEW);
        ensurePermission(role, FunctionCode.ROOM, ActionCode.VIEW | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.RESERVATION, ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE);
        ensurePermission(role, FunctionCode.INVOICE, ActionCode.VIEW | ActionCode.CREATE);
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
}
