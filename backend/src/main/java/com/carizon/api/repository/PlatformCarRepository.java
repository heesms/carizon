package com.carizon.api.repository;

import com.carizon.api.entity.PlatformCar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformCarRepository extends JpaRepository<PlatformCar, Long> {
    List<PlatformCar> findByCarId(Long carId);
    Optional<PlatformCar> findByPlatformNameAndPlatformCarKey(String platformName, String platformCarKey);
}
