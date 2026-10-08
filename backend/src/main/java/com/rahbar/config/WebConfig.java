package com.rahbar.config;

import com.rahbar.web.ActivityLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ActivityLogInterceptor activityLogInterceptor;

    public WebConfig(ActivityLogInterceptor activityLogInterceptor) {
        this.activityLogInterceptor = activityLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityLogInterceptor).addPathPatterns("/api/**");
    }

    // Uploaded files are no longer served publicly from /uploads: they go through FileController
    // (/api/files/{name}), which requires sign-in and checks who may open each file.
}
