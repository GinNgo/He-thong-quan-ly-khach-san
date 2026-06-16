package com.hotel.dtos;

public class AuthResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private String username;
    private java.util.List<String> roles;
    private java.util.List<PermissionDTO> permissions;

    public AuthResponse(String accessToken, String username, java.util.List<String> roles, java.util.List<PermissionDTO> permissions) {
        this.accessToken = accessToken;
        this.username = username;
        this.roles = roles;
        this.permissions = permissions;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public java.util.List<String> getRoles() { return roles; }
    public void setRoles(java.util.List<String> roles) { this.roles = roles; }

    public java.util.List<PermissionDTO> getPermissions() { return permissions; }
    public void setPermissions(java.util.List<PermissionDTO> permissions) { this.permissions = permissions; }
}
