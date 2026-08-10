package com.hotel.entities;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_function")
public class AppFunction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private AppModule module;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    private String url;
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "supported_action_mask", nullable = false)
    private Integer supportedActionMask = 127;

    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType = "PROPERTY";

    @Column(nullable = false)
    private Boolean active = true;

    @Version
    @Column(nullable = false)
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppModule getModule() { return module; }
    public void setModule(AppModule module) { this.module = module; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getSupportedActionMask() { return supportedActionMask; }
    public void setSupportedActionMask(Integer supportedActionMask) { this.supportedActionMask = supportedActionMask; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
