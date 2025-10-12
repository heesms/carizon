package com.carizon.api.repository;

import com.carizon.api.entity.PlatformCodeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformCodeMappingRepository extends JpaRepository<PlatformCodeMapping, Long> {
    List<PlatformCodeMapping> findByPlatformName(String platformName);
    Optional<PlatformCodeMapping> findByPlatformNameAndLevelAndRawCode(
        String platformName, 
        PlatformCodeMapping.MappingLevel level, 
        String rawCode
    );
}
