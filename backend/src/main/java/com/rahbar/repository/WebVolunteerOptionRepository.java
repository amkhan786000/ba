package com.rahbar.repository;

import com.rahbar.entity.WebVolunteerOption;
import com.rahbar.repository.WebVolunteerOptionRepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebVolunteerOptionRepository extends JpaRepository<WebVolunteerOption, Long> {

    List<WebVolunteerOption> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<WebVolunteerOption> findByOptionKey(String optionKey);

    boolean existsByKindAndIdNot(WebVolunteerOption.Kind kind, Long id);

    boolean existsByKind(WebVolunteerOption.Kind kind);
}
