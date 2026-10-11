package com.rahbar.website;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface WebStoryRepository extends JpaRepository<WebStory, Long>, JpaSpecificationExecutor<WebStory> {

    Optional<WebStory> findBySlugAndPublishedTrue(String slug);

    @Query("select distinct s.year from WebStory s where s.published = true and s.year is not null order by s.year desc")
    List<Integer> publishedYears();
}
