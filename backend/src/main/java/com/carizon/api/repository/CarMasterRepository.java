package com.carizon.api.repository;

import com.carizon.api.entity.CarMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CarMasterRepository extends JpaRepository<CarMaster, Long>, JpaSpecificationExecutor<CarMaster> {
}
