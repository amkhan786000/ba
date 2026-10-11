package com.rahbar.website;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface WebGalleryImageRepository extends JpaRepository<WebGalleryImage, Long>, JpaSpecificationExecutor<WebGalleryImage> {

    @Query("select distinct g.album from WebGalleryImage g where g.published = true and g.album is not null order by g.album")
    List<String> publishedAlbums();
}
