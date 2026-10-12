package com.rahbar.repository;

import com.rahbar.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findAllByOrderByDisplayOrderAscIdAsc();

    List<Faq> findByActiveTrueAndOnWebsiteTrueOrderByDisplayOrderAscIdAsc();
}
