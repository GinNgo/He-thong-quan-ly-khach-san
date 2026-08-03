package com.hotel.dtos;

import com.hotel.security.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class StaffUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Size(max = 32, message = "Phone number must not exceed 32 characters")
    @Pattern(regexp = "^\\+?[0-9() .-]*$", message = "Phone number contains unsupported characters")
    private String phone;

    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
    private String password;

    @NotEmpty(message = "At least one staff role is required")
    @Size(max = 5, message = "No more than 5 staff roles may be assigned")
    private Set<@NotNull @Positive Long> roleIds;

    @NotNull(message = "Property is required")
    @Positive(message = "Property is invalid")
    private Long hotelId;

    @Size(max = 500, message = "Assignment reason must not exceed 500 characters")
    private String assignmentReason;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Set<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(Set<Long> roleIds) { this.roleIds = roleIds; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public String getAssignmentReason() { return assignmentReason; }
    public void setAssignmentReason(String assignmentReason) { this.assignmentReason = assignmentReason; }
}
