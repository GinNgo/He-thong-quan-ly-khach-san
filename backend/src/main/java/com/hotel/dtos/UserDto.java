package com.hotel.dtos;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

public class UserDto {
    private Long id;
    private Long version;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private String status;
    private Instant emailVerifiedAt;
    private String pendingEmail;
    private Integer points;
    private LocalDateTime createdAt;
    private List<RoleSummary> roles;
    private HotelSummary hotel;
    private List<StaffAssignmentSummary> staffAssignments;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }

    public boolean isEmailVerified() { return emailVerifiedAt != null; }

    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RoleSummary> getRoles() { return roles; }
    public void setRoles(List<RoleSummary> roles) { this.roles = roles; }

    public HotelSummary getHotel() { return hotel; }
    public void setHotel(HotelSummary hotel) { this.hotel = hotel; }

    public List<StaffAssignmentSummary> getStaffAssignments() { return staffAssignments; }
    public void setStaffAssignments(List<StaffAssignmentSummary> staffAssignments) { this.staffAssignments = staffAssignments; }

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

    public static class StaffAssignmentSummary {
        private Long id;
        private Long hotelId;
        private String hotelName;
        private String status;
        private String statusReason;
        private LocalDateTime startDate;
        private LocalDateTime endDate;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getHotelId() { return hotelId; }
        public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
        public String getHotelName() { return hotelName; }
        public void setHotelName(String hotelName) { this.hotelName = hotelName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusReason() { return statusReason; }
        public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
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
    private String partnerRegistrationStatus;
    private Long unreadMessageCount;
    private Long pendingBookingCount;

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
    public String getPartnerRegistrationStatus() { return partnerRegistrationStatus; }
    public void setPartnerRegistrationStatus(String partnerRegistrationStatus) { this.partnerRegistrationStatus = partnerRegistrationStatus; }
    public Long getUnreadMessageCount() { return unreadMessageCount; }
    public void setUnreadMessageCount(Long unreadMessageCount) { this.unreadMessageCount = unreadMessageCount; }
    public Long getPendingBookingCount() { return pendingBookingCount; }
    public void setPendingBookingCount(Long pendingBookingCount) { this.pendingBookingCount = pendingBookingCount; }
}
