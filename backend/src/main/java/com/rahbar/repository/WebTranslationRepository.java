package com.rahbar.repository;

import com.rahbar.entity.WebTranslation;
import com.rahbar.repository.WebTranslationRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface WebTranslationRepository extends JpaRepository<WebTranslation, Long>, JpaSpecificationExecutor<WebTranslation> {

    List<WebTranslation> findByEntityTypeAndEntityIdInAndLang(String entityType, java.util.Collection<Long> ids, String lang);

    List<WebTranslation> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Optional<WebTranslation> findByEntityTypeAndEntityIdAndLangAndField(String entityType, Long entityId, String lang, String field);

    @Modifying
    @Query("delete from WebTranslation t where t.entityType = :type and t.entityId = :id")
    void deleteAllFor(@Param("type") String type, @Param("id") Long id);
}
