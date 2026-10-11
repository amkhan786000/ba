package com.rahbar.website;

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

    static final Map<String, BiConsumer<WebProject, String>> PROJECT = Map.of(
            "name", WebProject::setName, "category", WebProject::setCategory, "summary", WebProject::setSummary,
            "description", WebProject::setDescription, "sponsorshipInfo", WebProject::setSponsorshipInfo);
    static final Map<String, BiConsumer<WebNews, String>> NEWS = Map.of(
            "title", WebNews::setTitle, "summary", WebNews::setSummary, "content", WebNews::setContent,
            "location", WebNews::setLocation);
    static final Map<String, BiConsumer<WebStory, String>> STORY = Map.of(
            "title", WebStory::setTitle, "summary", WebStory::setSummary, "story", WebStory::setStory,
            "nowWhere", WebStory::setNowWhere);
    static final Map<String, BiConsumer<WebPage, String>> PAGE = Map.of(
            "title", WebPage::setTitle, "body", WebPage::setBody);

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
