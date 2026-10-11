package com.rahbar.reb;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebSectionRepository extends JpaRepository<RebSection, Long> {

    java.util.List<RebSection> findBySessionId(Long sessionId);
    java.util.List<RebSection> findBySessionIdAndClassId(Long sessionId, Long classId);
    boolean existsByClassId(Long classId);
    boolean existsBySessionIdAndClassIdAndNameAndIdNot(Long sessionId, Long classId, String name, Long id);
    boolean existsBySessionIdAndClassIdAndName(Long sessionId, Long classId, String name);
}
