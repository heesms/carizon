package com.carizon.api.controller;

import com.carizon.api.dto.ApiResponse;
import com.carizon.core.domain.favorite.Favorite;
import com.carizon.core.domain.user.User;
import com.carizon.core.service.FavoriteService;
import com.carizon.core.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;

    public FavoriteController(FavoriteService favoriteService, UserService userService) {
        this.favoriteService = favoriteService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<Favorite>> list(@AuthenticationPrincipal DefaultOAuth2User user) {
        Long userId = resolveUserId(user).getId();
        return ApiResponse.of(favoriteService.listFavorites(userId));
    }

    @PostMapping
    public ApiResponse<Favorite> add(@AuthenticationPrincipal DefaultOAuth2User user,
                                     @RequestBody Map<String, Long> payload) {
        User resolvedUser = resolveUserId(user);
        Long carId = payload.get("carId");
        return ApiResponse.of(favoriteService.addFavorite(resolvedUser.getId(), carId));
    }

    @DeleteMapping("/{carId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal DefaultOAuth2User user,
                                     @PathVariable Long carId) {
        User resolvedUser = resolveUserId(user);
        favoriteService.removeFavorite(resolvedUser.getId(), carId);
        return ApiResponse.of(null);
    }

    private User resolveUserId(DefaultOAuth2User user) {
        String email = (String) user.getAttributes().get("email");
        return userService.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not registered"));
    }
}
