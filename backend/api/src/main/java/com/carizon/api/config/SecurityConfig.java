package com.carizon.api.config;

import com.carizon.api.security.CarizonOAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CarizonOAuth2SuccessHandler successHandler;
    private final String sessionCookieName;

    public SecurityConfig(CarizonOAuth2SuccessHandler successHandler,
                          @Value("${carizon.security.session-cookie-name:CARIZON_SESSION}") String sessionCookieName) {
        this.successHandler = successHandler;
        this.sessionCookieName = sessionCookieName;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(registry -> registry
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth.successHandler(successHandler))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .deleteCookies(sessionCookieName)
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));

        return http.build();
    }
}
