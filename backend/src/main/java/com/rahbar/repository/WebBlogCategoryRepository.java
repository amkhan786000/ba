package com.rahbar.repository;

import com.rahbar.entity.WebBlogCategory;
import com.rahbar.repository.WebBlogCategoryRepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebBlogCategoryRepository extends JpaRepository<WebBlogCategory, Long> {

    List<WebBlogCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<WebBlogCategory> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<WebBlogCategory> findBySlug(String slug);
}
