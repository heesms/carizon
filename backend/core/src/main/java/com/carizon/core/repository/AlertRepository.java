package com.carizon.core.repository;

import com.carizon.core.domain.alert.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserId(Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
