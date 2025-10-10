package com.carizon.api.controller;

import com.carizon.api.dto.ApiResponse;
import com.carizon.core.domain.alert.Alert;
import com.carizon.core.domain.user.User;
import com.carizon.core.service.AlertService;
import com.carizon.core.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;
    private final UserService userService;

    public AlertController(AlertService alertService, UserService userService) {
        this.alertService = alertService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<Alert>> list(@AuthenticationPrincipal DefaultOAuth2User user) {
        Long userId = resolveUser(user).getId();
        return ApiResponse.of(alertService.listAlerts(userId));
    }

    @PostMapping
    public ApiResponse<Alert> create(@AuthenticationPrincipal DefaultOAuth2User user,
                                     @RequestBody Map<String, String> payload) {
        User resolvedUser = resolveUser(user);
        String criteria = payload.getOrDefault("criteria", "{}");
        return ApiResponse.of(alertService.createAlert(resolvedUser.getId(), criteria));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal DefaultOAuth2User user,
                                     @PathVariable Long id) {
        User resolvedUser = resolveUser(user);
        alertService.deleteAlert(resolvedUser.getId(), id);
        return ApiResponse.of(null);
    }

    private User resolveUser(DefaultOAuth2User user) {
        String email = (String) user.getAttributes().get("email");
        return userService.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not registered"));
    }
}
