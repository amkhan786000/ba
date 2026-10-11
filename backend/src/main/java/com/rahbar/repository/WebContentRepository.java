package com.rahbar.repository;

import com.rahbar.entity.WebContent;
import com.rahbar.repository.WebContentRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface WebContentRepository extends JpaRepository<WebContent, Long>, JpaSpecificationExecutor<WebContent> {

    Optional<WebContent> findByKeyAndLang(String key, String lang);

    List<WebContent> findByLangIn(java.util.Collection<String> langs);
}
