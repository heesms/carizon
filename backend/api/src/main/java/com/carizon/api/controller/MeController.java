package com.carizon.api.controller;

import com.carizon.api.dto.ApiResponse;
import com.carizon.core.domain.user.User;
import com.carizon.core.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal DefaultOAuth2User oAuth2User) {
        String email = (String) oAuth2User.getAttributes().get("email");
        User user = userService.findByEmail(email).orElse(null);
        List<String> roles = user != null
            ? user.getRoles()
            : oAuth2User.getAuthorities().stream().map(Object::toString).toList();
        return ApiResponse.of(Map.of(
            "email", email,
            "name", user != null ? user.getName() : null,
            "roles", roles
        ));
    }
}
