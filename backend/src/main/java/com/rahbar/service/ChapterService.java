package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Chapters: the list behind the Chapter dropdown on users. Users point at a chapter through users.chapter_id. */
@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;

    public ChapterService(ChapterRepository chapterRepository, UserRepository userRepository) {
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
    }

    public List<Chapter> list() {
        return chapterRepository.findAllByOrderByChapterNameAsc();
    }

    /** Adds a chapter, or edits one (chapterId set). Users point at the chapter by id, so a rename needs nothing else. */
    @Transactional
    public Chapter save(Chapter chapter) {
        String name = chapter.getChapterName() == null ? "" : chapter.getChapterName().trim();
        if (name.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Chapter name is required.");
        chapterRepository.findByChapterNameIgnoreCase(name)
                .filter(other -> !other.getChapterId().equals(chapter.getChapterId()))
                .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "A chapter named '" + name + "' already exists."); });

        Chapter target = chapter.getChapterId() == null ? new Chapter()
                : chapterRepository.findById(chapter.getChapterId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chapter not found."));
        String email = trimToNull(chapter.getLeadEmail());
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid email address for the chapter lead.");
        }
        target.setChapterName(name);
        target.setDescription(trimToNull(chapter.getDescription()));
        target.setLeadName(trimToNull(chapter.getLeadName()));
        target.setLeadPhone(trimToNull(chapter.getLeadPhone()));
        target.setLeadEmail(email);
        target.setActive(!Boolean.FALSE.equals(chapter.getActive())); // active unless explicitly switched off
        return chapterRepository.save(target);
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Refuses while users are still in the chapter, so no user is left pointing at a deleted chapter. */
    public void delete(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chapter not found."));
        long users = userRepository.countByChapterId(chapter.getChapterId());
        if (users > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This chapter has " + users
                    + " user(s). Move them to another chapter before deleting it.");
        }
        chapterRepository.delete(chapter);
    }
}
