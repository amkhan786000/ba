package com.rahbar.repository;

import com.rahbar.entity.WebRevision;
import com.rahbar.repository.WebRevisionRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface WebRevisionRepository extends JpaRepository<WebRevision, Long>, JpaSpecificationExecutor<WebRevision> {

    List<WebRevision> findTop30ByTargetAndLangOrderByIdDesc(String target, String lang);
}
