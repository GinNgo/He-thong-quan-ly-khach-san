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
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (appModuleRepository.count() == 0) {
            AppModule sysModule = new AppModule(null, "SYSTEM", "Hệ Thống");
            AppModule hotelModule = new AppModule(null, "HOTEL", "Khách Sạn");
            appModuleRepository.save(sysModule);
            appModuleRepository.save(hotelModule);

            AppFunction funcUser = new AppFunction(null, sysModule, FunctionCode.USER.name(), "Quản lý Người Dùng", "/admin/users", "pi pi-users", 1);
            AppFunction funcRole = new AppFunction(null, sysModule, FunctionCode.ROLE.name(), "Quản lý Phân Quyền", "/admin/roles", "pi pi-key", 2);
            AppFunction funcRoom = new AppFunction(null, hotelModule, FunctionCode.ROOM.name(), "Quản lý Phòng", "/admin/rooms", "pi pi-home", 3);
            AppFunction funcRoomType = new AppFunction(null, hotelModule, FunctionCode.ROOM_TYPE.name(), "Quản lý Loại Phòng", "/admin/room-types", "pi pi-list", 4);
            AppFunction funcReservation = new AppFunction(null, hotelModule, FunctionCode.RESERVATION.name(), "Quản lý Đặt Phòng", "/admin/reservations", "pi pi-calendar", 5);
            
            appFunctionRepository.save(funcUser);
            appFunctionRepository.save(funcRole);
            appFunctionRepository.save(funcRoom);
            appFunctionRepository.save(funcRoomType);
            appFunctionRepository.save(funcReservation);
        }

        if (roleRepository.count() == 0) {
            Role adminRole = new Role();
            adminRole.setCode("SUPER_ADMIN");
            adminRole.setName("ADMIN");
            roleRepository.save(adminRole);

            Role customerRole = new Role();
            customerRole.setCode("CUSTOMER");
            customerRole.setName("CUSTOMER");
            roleRepository.save(customerRole);

            Role receptionistRole = new Role();
            receptionistRole.setCode("RECEPTIONIST");
            receptionistRole.setName("RECEPTIONIST");
            roleRepository.save(receptionistRole);

            // Assign permissions to SUPER_ADMIN
            int allActions = ActionCode.VIEW | ActionCode.CREATE | ActionCode.UPDATE | ActionCode.DELETE | ActionCode.EXPORT | ActionCode.APPROVE;
            for (AppFunction func : appFunctionRepository.findAll()) {
                RolePermission rp = new RolePermission(null, adminRole, func, allActions);
                rolePermissionRepository.save(rp);
            }
        }

        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            admin.setFullName("System Admin");
            admin.setEmail("admin@hotel.com");
            admin.setRoles(Set.of(roleRepository.findByCode("SUPER_ADMIN").orElseThrow()));
            admin.setStatus("ACTIVE");
            userRepository.save(admin);
        }
    }
}
