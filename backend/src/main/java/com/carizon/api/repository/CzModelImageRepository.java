package com.carizon.api.repository;

import com.carizon.api.entity.CzModelImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CzModelImageRepository extends JpaRepository<CzModelImage, Long> {

    /**
     * Get representative image for a model code
     * Priority: is_main=1 first, then sort_order ASC, then random among is_main
     */
    @Query("""
        SELECT img FROM CzModelImage img 
        WHERE img.modelCode = :modelCode 
        ORDER BY img.isMain DESC, img.sortOrder ASC, img.id ASC
        """)
    List<CzModelImage> findByModelCodeOrderByMainAndSort(@Param("modelCode") String modelCode);

    /**
     * Get all images for a model code
     */
    List<CzModelImage> findByModelCodeOrderByIsMainDescSortOrderAscIdAsc(String modelCode);
}
