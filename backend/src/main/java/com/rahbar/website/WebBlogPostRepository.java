package com.rahbar.website;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface WebBlogPostRepository extends JpaRepository<WebBlogPost, Long>, JpaSpecificationExecutor<WebBlogPost> {

    Optional<WebBlogPost> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);

    List<WebBlogPost> findByCreatedByOrderByIdDesc(Long createdBy);

    List<WebBlogPost> findTop3ByPublishedTrueAndTopicAndIdNotOrderByPublishedAtDesc(String topic, Long id);

    @Query("select distinct p.topic from WebBlogPost p where p.published = true order by p.topic")
    List<String> publishedTopics();
}
