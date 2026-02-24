package com.carizon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 설정
 * 프론트엔드 정적 리소스 서빙 설정
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 핸들러 설정
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600); // 1시간 캐시
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA 라우팅 지원: 모든 경로를 index.html로 리다이렉트
        registry.addViewController("/")
                .setViewName("forward:/index.html");
        
        // 관리자 페이지 경로들도 index.html로
        registry.addViewController("/admin/**")
                .setViewName("forward:/index.html");
    }
}
