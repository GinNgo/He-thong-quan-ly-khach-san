package com.hotel.dtos;

import java.time.LocalDateTime;
import java.util.List;

public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String status;
    private Integer points;
    private LocalDateTime createdAt;
    private List<RoleSummary> roles;
    private HotelSummary hotel;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RoleSummary> getRoles() { return roles; }
    public void setRoles(List<RoleSummary> roles) { this.roles = roles; }

    public HotelSummary getHotel() { return hotel; }
    public void setHotel(HotelSummary hotel) { this.hotel = hotel; }

    public static class RoleSummary {
        private Long id;
        private String code;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class HotelSummary {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // SaaS Context Fields
    private String plan;
    private String subscriptionStatus;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isLifetime;
    private java.util.Map<String, Integer> limits;
    private java.util.Map<String, Integer> currentUsage;
    private List<HotelSummary> assignedProperties;

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public Boolean getIsLifetime() { return isLifetime; }
    public void setIsLifetime(Boolean isLifetime) { this.isLifetime = isLifetime; }

    public java.util.Map<String, Integer> getLimits() { return limits; }
    public void setLimits(java.util.Map<String, Integer> limits) { this.limits = limits; }

    public java.util.Map<String, Integer> getCurrentUsage() { return currentUsage; }
    public void setCurrentUsage(java.util.Map<String, Integer> currentUsage) { this.currentUsage = currentUsage; }

    public List<HotelSummary> getAssignedProperties() { return assignedProperties; }
    public void setAssignedProperties(List<HotelSummary> assignedProperties) { this.assignedProperties = assignedProperties; }
}
