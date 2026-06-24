package com.hotel.config;

import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedDatabase(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                                          AppModuleRepository appModuleRepository, AppFunctionRepository appFunctionRepository, RolePermissionRepository rolePermissionRepository,
                                          org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE app_module ALTER COLUMN name NVARCHAR(255)");
                jdbcTemplate.execute("ALTER TABLE app_function ALTER COLUMN name NVARCHAR(255)");
            } catch (Exception e) {
                System.out.println("Could not alter table columns: " + e.getMessage());
            }
            // Ensure SUPER_ADMIN role exists
            Role superAdminRole = roleRepository.findByCode("SUPER_ADMIN").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setCode("SUPER_ADMIN");
                newRole.setName("Super Administrator");
                return roleRepository.save(newRole);
            });

            // Ensure ADMIN role exists
            Role adminRole = roleRepository.findByCode("ADMIN").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setCode("ADMIN");
                newRole.setName("Administrator");
                return roleRepository.save(newRole);
            });

            // Ensure admin user has SUPER_ADMIN
            Optional<User> adminOpt = userRepository.findByUsername("admin");
            if (adminOpt.isPresent()) {
                User admin = adminOpt.get();
                if (admin.getRoles() == null || admin.getRoles().isEmpty()) {
                    Set<Role> roles = new HashSet<>();
                    roles.add(superAdminRole);
                    admin.setRoles(roles);
                    userRepository.save(admin);
                    System.out.println("Restored SUPER_ADMIN role for user 'admin'");
                }
            } else {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin"));
                admin.setEmail("admin@hotel.com");
                admin.setFullName("System Admin");
                admin.setStatus("ACTIVE");
                admin.setCreatedAt(LocalDateTime.now());
                Set<Role> roles = new HashSet<>();
                roles.add(superAdminRole);
                admin.setRoles(roles);
                userRepository.save(admin);
                System.out.println("Created 'admin' user with SUPER_ADMIN role");
            }
            
            // support user
            Optional<User> supportOpt = userRepository.findByUsername("support");
            if (supportOpt.isEmpty()) {
                User support = new User();
                support.setUsername("support");
                support.setPasswordHash(passwordEncoder.encode("support"));
                support.setEmail("support@hotel.com");
                support.setFullName("Support Staff");
                support.setStatus("ACTIVE");
                support.setCreatedAt(LocalDateTime.now());
                Set<Role> roles = new HashSet<>();
                roles.add(adminRole);
                support.setRoles(roles);
                userRepository.save(support);
                System.out.println("Created 'support' user with ADMIN role");
            }

            // Seed Modules and Functions
            if (appModuleRepository.count() == 0) {
                AppModule sysModule = new AppModule();
                sysModule.setCode("SYS");
                sysModule.setName("Hệ thống");
                sysModule = appModuleRepository.save(sysModule);

                createFunction(appFunctionRepository, sysModule, FunctionCode.SYSTEM.name(), "Cấu hình Hệ thống", "/admin/system", "pi pi-cog", 1);
                createFunction(appFunctionRepository, sysModule, FunctionCode.ROLE.name(), "Quản lý Vai trò", "/admin/roles", "pi pi-id-card", 2);
                createFunction(appFunctionRepository, sysModule, FunctionCode.USER.name(), "Quản lý Người dùng", "/admin/users", "pi pi-users", 3);

                AppModule bookingModule = new AppModule();
                bookingModule.setCode("BOOKING");
                bookingModule.setName("Lễ tân & Đặt phòng");
                bookingModule = appModuleRepository.save(bookingModule);

                createFunction(appFunctionRepository, bookingModule, FunctionCode.RESERVATION.name(), "Quản lý Đặt phòng", "/admin/reservations", "pi pi-calendar", 1);
                createFunction(appFunctionRepository, bookingModule, FunctionCode.CHECKIN.name(), "Nhận phòng", "/admin/checkin", "pi pi-sign-in", 2);
                createFunction(appFunctionRepository, bookingModule, FunctionCode.CHECKOUT.name(), "Trả phòng", "/admin/checkout", "pi pi-sign-out", 3);

                AppModule roomModule = new AppModule();
                roomModule.setCode("ROOM_MGT");
                roomModule.setName("Quản lý Phòng");
                roomModule = appModuleRepository.save(roomModule);

                createFunction(appFunctionRepository, roomModule, FunctionCode.ROOM.name(), "Danh sách Phòng", "/admin/rooms", "pi pi-box", 1);
                createFunction(appFunctionRepository, roomModule, FunctionCode.ROOM_TYPE.name(), "Loại Phòng", "/admin/room-types", "pi pi-tags", 2);

                AppModule financeModule = new AppModule();
                financeModule.setCode("FINANCE");
                financeModule.setName("Tài chính & Báo cáo");
                financeModule = appModuleRepository.save(financeModule);

                createFunction(appFunctionRepository, financeModule, FunctionCode.INVOICE.name(), "Hóa đơn", "/admin/invoices", "pi pi-file-o", 1);
                createFunction(appFunctionRepository, financeModule, FunctionCode.REPORT.name(), "Báo cáo Thống kê", "/admin/dashboard", "pi pi-chart-bar", 2);
            } else {
                // Fix broken names if any
                List<AppModule> modules = appModuleRepository.findAll();
                for (AppModule m : modules) {
                    if (m.getCode().equals("SYS")) m.setName("Hệ thống");
                    if (m.getCode().equals("BOOKING")) m.setName("Lễ tân & Đặt phòng");
                    if (m.getCode().equals("ROOM_MGT")) m.setName("Quản lý Phòng");
                    if (m.getCode().equals("FINANCE")) m.setName("Tài chính & Báo cáo");
                    appModuleRepository.save(m);
                }
                
                List<AppFunction> functions = appFunctionRepository.findAll();
                for (AppFunction f : functions) {
                    if (f.getCode().equals(FunctionCode.SYSTEM.name())) f.setName("Cấu hình Hệ thống");
                    if (f.getCode().equals(FunctionCode.ROLE.name())) f.setName("Quản lý Phân quyền");
                    if (f.getCode().equals(FunctionCode.USER.name())) f.setName("Quản lý Người dùng");
                    if (f.getCode().equals(FunctionCode.RESERVATION.name())) f.setName("Quản lý Đặt phòng");
                    if (f.getCode().equals(FunctionCode.CHECKIN.name())) f.setName("Nhận phòng");
                    if (f.getCode().equals(FunctionCode.CHECKOUT.name())) f.setName("Trả phòng");
                    if (f.getCode().equals(FunctionCode.ROOM.name())) f.setName("Danh sách Phòng");
                    if (f.getCode().equals(FunctionCode.ROOM_TYPE.name())) f.setName("Loại Phòng");
                    if (f.getCode().equals(FunctionCode.INVOICE.name())) f.setName("Quản lý Hóa đơn");
                    if (f.getCode().equals(FunctionCode.REPORT.name())) f.setName("Báo cáo Thống kê");
                    appFunctionRepository.save(f);
                }
            }

            // Seed Role Permissions for SUPER_ADMIN
            if (rolePermissionRepository.count() == 0) {
                List<AppFunction> allFunctions = appFunctionRepository.findAll();
                int allActions = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE | ActionCode.EXPORT | ActionCode.APPROVE;
                for (AppFunction func : allFunctions) {
                    RolePermission rp = new RolePermission();
                    rp.setRole(superAdminRole);
                    rp.setFunction(func);
                    rp.setActionMask(allActions);
                    rolePermissionRepository.save(rp);
                }
            }
        };
    }

    private void createFunction(AppFunctionRepository repo, AppModule module, String code, String name, String url, String icon, int order) {
        AppFunction func = new AppFunction();
        func.setModule(module);
        func.setCode(code);
        func.setName(name);
        func.setUrl(url);
        func.setIcon(icon);
        func.setSortOrder(order);
        repo.save(func);
    }
}
