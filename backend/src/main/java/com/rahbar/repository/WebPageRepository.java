package com.rahbar.repository;

import com.rahbar.entity.WebPage;
import com.rahbar.repository.WebPageRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface WebPageRepository extends JpaRepository<WebPage, Long>, JpaSpecificationExecutor<WebPage> {

    List<WebPage> findByPublishedTrueAndShowInMenuTrueOrderByMenuOrderAscTitleAsc();

    Optional<WebPage> findBySlugAndPublishedTrue(String slug);
}
