package com.carizon.core.repository;

import com.carizon.core.domain.car.PlatformCar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformCarRepository extends JpaRepository<PlatformCar, Long> {
    List<PlatformCar> findByCarId(String carId);
}
