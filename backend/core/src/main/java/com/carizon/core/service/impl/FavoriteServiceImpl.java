package com.carizon.core.service.impl;

import com.carizon.core.domain.favorite.Favorite;
import com.carizon.core.repository.FavoriteRepository;
import com.carizon.core.service.FavoriteService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public FavoriteServiceImpl(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @Override
    public List<Favorite> listFavorites(Long userId) {
        return favoriteRepository.findByUserId(userId);
    }

    @Override
    public Favorite addFavorite(Long userId, Long carId) {
        return favoriteRepository.save(new Favorite(userId, carId));
    }

    @Override
    public void removeFavorite(Long userId, Long carId) {
        favoriteRepository.deleteByUserIdAndCarId(userId, carId);
    }
}
