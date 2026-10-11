package com.rahbar.repository;

import com.rahbar.entity.WebNews;
import com.rahbar.repository.WebNewsRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface WebNewsRepository extends JpaRepository<WebNews, Long>, JpaSpecificationExecutor<WebNews> {

    Optional<WebNews> findBySlugAndPublishedTrue(String slug);
}
