package com.hotel.services.impl;

import com.hotel.entities.AppModule;
import com.hotel.entities.AppFunction;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.entities.User;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.entities.Hotel;
import com.hotel.entities.RoomType;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppModuleRepository appModuleRepository;
    private final AppFunctionRepository appFunctionRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try {
            jdbcTemplate.execute("ALTER TABLE app_module ALTER COLUMN name NVARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE app_function ALTER COLUMN name NVARCHAR(255)");
        } catch (Exception e) {
            System.out.println("Could not alter table columns: " + e.getMessage());
        }
        AppModule sysModule = appModuleRepository.findByCode("SYSTEM");
        if (sysModule == null) {
            sysModule = appModuleRepository.save(new AppModule(null, "SYSTEM", "Hệ Thống"));
        } else {
            sysModule.setName("Hệ Thống");
            sysModule = appModuleRepository.save(sysModule);
        }
        
        AppModule hotelModule = appModuleRepository.findByCode("HOTEL");
        if (hotelModule == null) {
            hotelModule = appModuleRepository.save(new AppModule(null, "HOTEL", "Khách Sạn"));
        } else {
            hotelModule.setName("Khách Sạn");
            hotelModule = appModuleRepository.save(hotelModule);
        }
        
        AppModule financeModule = appModuleRepository.findByCode("FINANCE");
        if (financeModule == null) {
            financeModule = appModuleRepository.save(new AppModule(null, "FINANCE", "Tài Chính"));
        } else {
            financeModule.setName("Tài Chính");
            financeModule = appModuleRepository.save(financeModule);
        }

        initFunction(sysModule, FunctionCode.USER.name(), "Quản lý Người Dùng", "/admin/users", "pi pi-users", 1);
        initFunction(sysModule, FunctionCode.ROLE.name(), "Quản lý Phân Quyền", "/admin/roles", "pi pi-key", 2);
        initFunction(hotelModule, FunctionCode.ROOM.name(), "Quản lý Phòng", "/admin/rooms", "pi pi-home", 3);
        initFunction(hotelModule, FunctionCode.ROOM_TYPE.name(), "Quản lý Loại Phòng", "/admin/room-types", "pi pi-list", 4);
        initFunction(hotelModule, FunctionCode.RESERVATION.name(), "Quản lý Đặt Phòng", "/admin/reservations", "pi pi-calendar", 5);
        initFunction(sysModule, FunctionCode.REPORT.name(), "Báo Cáo Thống Kê", "/admin/dashboard", "pi pi-chart-bar", 6);
        initFunction(financeModule, FunctionCode.INVOICE.name(), "Quản lý Hóa Đơn", "/admin/invoices", "pi pi-file-o", 7);
        initFunction(hotelModule, FunctionCode.HOTEL.name(), "Dịch vụ Khách Sạn", "/admin/services", "pi pi-box", 8);
        initFunction(sysModule, FunctionCode.AI_CHAT.name(), "AI Chatbot", "/ai", "pi pi-android", 9);
        initFunction(financeModule, FunctionCode.FINANCE.name(), "Thanh Toán", "/admin/payments", "pi pi-money-bill", 10);
        initFunction(sysModule, FunctionCode.SYSTEM.name(), "Cấu hình Trang", "/admin/modules", "pi pi-cog", 11);

        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElse(null);
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setCode("SUPER_ADMIN");
            adminRole.setName("ADMIN");
            adminRole = roleRepository.save(adminRole);
        }

        if (roleRepository.findByCode("CUSTOMER").isEmpty()) {
            Role customerRole = new Role();
            customerRole.setCode("CUSTOMER");
            customerRole.setName("CUSTOMER");
            roleRepository.save(customerRole);
        }

        if (roleRepository.findByCode("RECEPTIONIST").isEmpty()) {
            Role receptionistRole = new Role();
            receptionistRole.setCode("RECEPTIONIST");
            receptionistRole.setName("RECEPTIONIST");
            roleRepository.save(receptionistRole);
        }

        if (roleRepository.findByCode("HOTEL_MANAGER").isEmpty()) {
            Role managerRole = new Role();
            managerRole.setCode("HOTEL_MANAGER");
            managerRole.setName("HOTEL MANAGER");
            roleRepository.save(managerRole);
        }

        // Seed default Hotel if none exists
        Hotel defaultHotel = hotelRepository.findAll().stream().findFirst().orElse(null);
        if (defaultHotel == null) {
            defaultHotel = new Hotel();
            defaultHotel.setName("Đà Lạt Grand Hotel");
            defaultHotel.setAddress("123 Trần Phú");
            defaultHotel.setCity("Đà Lạt");
            defaultHotel.setCountry("Việt Nam");
            defaultHotel.setStarRating(5);
            defaultHotel.setStatus("ACTIVE");
            defaultHotel = hotelRepository.save(defaultHotel);
        }

        // Map existing RoomTypes to default hotel
        for (RoomType rt : roomTypeRepository.findAll()) {
            if (rt.getHotel() == null) {
                rt.setHotel(defaultHotel);
                roomTypeRepository.save(rt);
            }
        }

        // Assign permissions to SUPER_ADMIN
        int allActions = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE | ActionCode.EXPORT | ActionCode.APPROVE;
        for (AppFunction func : appFunctionRepository.findAll()) {
            RolePermission existing = rolePermissionRepository.findByRoleIdAndFunctionId(adminRole.getId(), func.getId());
            if (existing == null) {
                RolePermission rp = new RolePermission(null, adminRole, func, allActions);
                rolePermissionRepository.save(rp);
            } else {
                existing.setActionMask(allActions);
                rolePermissionRepository.save(existing);
            }
        }

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
            admin.setRoles(new java.util.HashSet<>(Set.of(adminRole)));
            userRepository.save(admin);
        }
    }

    private void initFunction(AppModule module, String code, String name, String url, String icon, int order) {
        AppFunction func = appFunctionRepository.findByCode(code);
        if (func == null) {
            appFunctionRepository.save(new AppFunction(null, module, code, name, url, icon, order));
        } else {
            func.setName(name);
            func.setUrl(url);
            func.setIcon(icon);
            func.setSortOrder(order);
            appFunctionRepository.save(func);
        }
    }
}
