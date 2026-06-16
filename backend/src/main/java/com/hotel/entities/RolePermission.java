package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_role_permission")
public class RolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "function_id", nullable = false)
    private AppFunction function;

    @Column(name = "action_mask", nullable = false)
    private Integer actionMask;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public AppFunction getFunction() { return function; }
    public void setFunction(AppFunction function) { this.function = function; }
    public Integer getActionMask() { return actionMask; }
    public void setActionMask(Integer actionMask) { this.actionMask = actionMask; }
}
