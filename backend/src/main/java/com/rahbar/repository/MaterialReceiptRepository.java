package com.rahbar.repository;

import com.rahbar.entity.MaterialReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialReceiptRepository extends JpaRepository<MaterialReceipt, Long> {
    List<MaterialReceipt> findByItemIdOrderByReceivedOnDesc(Long itemId);
}
