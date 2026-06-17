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

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedDatabase(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        return args -> {
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
        };
    }
}
