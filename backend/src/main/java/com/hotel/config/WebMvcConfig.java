package com.hotel.config;

import com.hotel.security.PermissionInterceptor;
import com.hotel.security.TenantFilterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;
    private final TenantFilterInterceptor tenantFilterInterceptor;

    public WebMvcConfig(PermissionInterceptor permissionInterceptor, TenantFilterInterceptor tenantFilterInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
        this.tenantFilterInterceptor = tenantFilterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
        registry.addInterceptor(tenantFilterInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
