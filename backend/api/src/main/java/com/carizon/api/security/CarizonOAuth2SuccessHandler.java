package com.carizon.api.security;

import com.carizon.core.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CarizonOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;

    public CarizonOAuth2SuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof DefaultOAuth2User oAuth2User) {
            Map<String, Object> attributes = oAuth2User.getAttributes();
            String email = (String) attributes.getOrDefault("email", "");
            String name = (String) attributes.getOrDefault("name", "");
            userService.registerOrUpdateOAuthUser(email, name);
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
