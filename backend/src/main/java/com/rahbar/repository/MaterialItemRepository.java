package com.rahbar.repository;

import com.rahbar.entity.MaterialItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialItemRepository extends JpaRepository<MaterialItem, Long> {
    List<MaterialItem> findAllByOrderByNameAsc();
}
