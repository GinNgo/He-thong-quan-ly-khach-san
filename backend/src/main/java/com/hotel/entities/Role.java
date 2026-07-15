package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_role")
public class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g., SUPER_ADMIN, HOTEL_ADMIN, RECEPTIONIST, CUSTOMER

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "system_role", nullable = false)
    private Boolean systemRole = false;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private java.util.Set<RolePermission> rolePermissions;

    // Getters and Setters omitted for brevity

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getSystemRole() { return systemRole; }
    public void setSystemRole(Boolean systemRole) { this.systemRole = systemRole; }

    public java.util.Set<RolePermission> getRolePermissions() {
        return rolePermissions;
    }

    public void setRolePermissions(java.util.Set<RolePermission> rolePermissions) {
        this.rolePermissions = rolePermissions;
    }
}
