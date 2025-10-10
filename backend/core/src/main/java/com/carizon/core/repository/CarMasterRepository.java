package com.carizon.core.repository;

import com.carizon.core.domain.car.CarMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarMasterRepository extends JpaRepository<CarMaster, Long> {
}
