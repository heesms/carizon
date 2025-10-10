package com.carizon.core.service;

import com.carizon.core.domain.favorite.Favorite;

import java.util.List;

public interface FavoriteService {
    List<Favorite> listFavorites(Long userId);
    Favorite addFavorite(Long userId, Long carId);
    void removeFavorite(Long userId, Long carId);
}
