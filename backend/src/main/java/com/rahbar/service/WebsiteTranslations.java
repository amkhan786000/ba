package com.rahbar.service;

import com.rahbar.entity.WebBlogCategory;
import com.rahbar.entity.WebNews;
import com.rahbar.entity.WebPage;
import com.rahbar.entity.WebProject;
import com.rahbar.entity.WebStory;
import com.rahbar.entity.WebTranslation;
import com.rahbar.repository.WebTranslationRepository;
import com.rahbar.entity.WebVolunteerOption;
import com.rahbar.entity.WebsiteLang;
import com.rahbar.entity.WebsiteTranslatable;
import com.rahbar.service.WebsiteTranslations;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lays Urdu / Hindi text over records for the website, falling back to English. Only used on records loaded in a
 * read-only transaction, so the translated values are never written back.
 */
@Service
public class WebsiteTranslations {

    public static final Map<String, BiConsumer<WebProject, String>> PROJECT = Map.of(
            "name", WebProject::setName, "category", WebProject::setCategory, "summary", WebProject::setSummary,
            "description", WebProject::setDescription, "sponsorshipInfo", WebProject::setSponsorshipInfo);
    public static final Map<String, BiConsumer<WebNews, String>> NEWS = Map.of(
            "title", WebNews::setTitle, "summary", WebNews::setSummary, "content", WebNews::setContent,
            "location", WebNews::setLocation);
    public static final Map<String, BiConsumer<WebStory, String>> STORY = Map.of(
            "title", WebStory::setTitle, "summary", WebStory::setSummary, "story", WebStory::setStory,
            "nowWhere", WebStory::setNowWhere);
    public static final Map<String, BiConsumer<WebPage, String>> PAGE = Map.of(
            "title", WebPage::setTitle, "body", WebPage::setBody);
    public static final Map<String, BiConsumer<WebBlogCategory, String>> CATEGORY = Map.of(
            "name", WebBlogCategory::setName, "description", WebBlogCategory::setDescription);
    public static final Map<String, BiConsumer<WebVolunteerOption, String>> VOLUNTEER_OPTION = Map.of(
            "title", WebVolunteerOption::setTitle, "description", WebVolunteerOption::setDescription,
            "choicesLabel", WebVolunteerOption::setChoicesLabel, "choices", WebVolunteerOption::setChoices,
            "extraLabel", WebVolunteerOption::setExtraLabel, "extraChoices", WebVolunteerOption::setExtraChoices);

    private final WebTranslationRepository repo;

    public WebsiteTranslations(WebTranslationRepository repo) {
        this.repo = repo;
    }

    public <T> List<T> apply(WebsiteTranslatable type, WebsiteLang lang, List<T> records, Function<T, Long> id,
                             Map<String, BiConsumer<T, String>> setters) {
        if (lang == WebsiteLang.EN || records.isEmpty()) return records;
        Set<Long> ids = records.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, String>> byRecord = new HashMap<>();
        for (WebTranslation t : repo.findByEntityTypeAndEntityIdInAndLang(type.name(), ids, lang.code())) {
            byRecord.computeIfAbsent(t.getEntityId(), k -> new HashMap<>()).put(t.getField(), t.getValue());
        }
        for (T r : records) {
            Map<String, String> values = byRecord.get(id.apply(r));
            if (values == null) continue;
            setters.forEach((field, setter) -> {
                String v = values.get(field);
                if (v != null && !v.isBlank()) setter.accept(r, v);
            });
        }
        return records;
    }

    public <T> T apply(WebsiteTranslatable type, WebsiteLang lang, T record, Function<T, Long> id,
                       Map<String, BiConsumer<T, String>> setters) {
        apply(type, lang, List.of(record), id, setters);
        return record;
    }
}
