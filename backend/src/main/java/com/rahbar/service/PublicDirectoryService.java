package com.rahbar.service;

import com.rahbar.dto.PublicCenter;
import com.rahbar.dto.PublicChapter;
import com.rahbar.dto.PublicStats;
import com.rahbar.entity.Chapter;
import com.rahbar.entity.RccCenter;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Chapters and RCC centers for public websites (e.g. biharanjuman.com). Only the name, the
 * description / location and the lead's name are shared: never phone numbers or emails. Students are
 * only ever shared as a count.
 */
@Service
@Transactional(readOnly = true)
public class PublicDirectoryService {

    private final ChapterRepository chapters;
    private final RccCenterRepository centers;
    private final UserRepository users;

    public PublicDirectoryService(ChapterRepository chapters, RccCenterRepository centers, UserRepository users) {
        this.chapters = chapters;
        this.centers = centers;
        this.users = users;
    }

    /** Active chapters, by name. */
    public List<PublicChapter> chapters() {
        return chapters.findAllByOrderByChapterNameAsc().stream()
                .filter(c -> !Boolean.FALSE.equals(c.getActive()))
                .map(PublicChapter::of)
                .toList();
    }

    /** All RCC centers, by name. */
    public List<PublicCenter> centers() {
        return centers.findAll().stream()
                .sorted(Comparator.comparing(c -> c.getCenterName() == null ? "" : c.getCenterName(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(PublicCenter::of)
                .toList();
    }

    /** Every student account, the same figure as "total students" in the reports. Merged duplicate accounts are not counted. */
    public PublicStats stats() {
        long count = users.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> u.getMergedIntoId() == null)
                .count();
        return new PublicStats(count);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
