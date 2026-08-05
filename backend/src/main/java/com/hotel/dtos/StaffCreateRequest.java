package com.hotel.dtos;

import com.hotel.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class StaffCreateRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 100, message = "Username must contain between 4 and 100 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}._+@-]+$", message = "Username contains unsupported characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 320, message = "Email must not exceed 320 characters")
    private String email;

    @NotBlank(message = "Initial password is required")
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Size(max = 32, message = "Phone number must not exceed 32 characters")
    @Pattern(regexp = "^\\+?[0-9() .-]*$", message = "Phone number contains unsupported characters")
    private String phone;

    @NotEmpty(message = "At least one staff role is required")
    @Size(max = 5, message = "No more than 5 staff roles may be assigned")
    private Set<@NotNull @Positive Long> roleIds;

    @NotNull(message = "Property is required")
    @Positive(message = "Property is invalid")
    private Long hotelId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Set<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(Set<Long> roleIds) { this.roleIds = roleIds; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
}
