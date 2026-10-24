package com.rahbar.repository;

import com.rahbar.entity.VolunteerHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VolunteerHoursRepository extends JpaRepository<VolunteerHours, Long> {
    List<VolunteerHours> findAllByOrderByWorkedOnDescIdDesc();

    List<VolunteerHours> findByUserIdOrderByWorkedOnDesc(Long userId);

    List<VolunteerHours> findByVolunteerIdOrderByWorkedOnDesc(Long volunteerId);
}
