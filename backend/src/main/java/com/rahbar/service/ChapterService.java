package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Chapters: the list behind the Chapter dropdown on users. A user's chapter is stored by name in users.region. */
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

    /** Adds a chapter, or edits one (chapterId set). Renaming also moves the chapter's users to the new name. */
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
        String oldName = target.getChapterName();
        target.setChapterName(name);
        String description = chapter.getDescription();
        target.setDescription(description == null || description.isBlank() ? null : description.trim());
        Chapter saved = chapterRepository.save(target);
        if (oldName != null && !oldName.equals(name)) userRepository.renameRegion(oldName, name);
        return saved;
    }

    /** Refuses while users are still in the chapter, so no user is left pointing at a deleted chapter. */
    public void delete(Long id) {
        Chapter chapter = chapterRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Chapter not found."));
        long users = userRepository.countByRegion(chapter.getChapterName());
        if (users > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This chapter has " + users
                    + " user(s). Move them to another chapter before deleting it.");
        }
        chapterRepository.delete(chapter);
    }
}
