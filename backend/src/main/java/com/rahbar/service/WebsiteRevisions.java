package com.rahbar.service;

import com.rahbar.entity.WebRevision;
import com.rahbar.repository.WebRevisionRepository;
import com.rahbar.entity.WebsiteLang;
import com.rahbar.service.WebsiteRevisions;
import com.rahbar.entity.WebsiteTranslatable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Keeps every saved version of editable text so an earlier one can be put back. Targets:
 * {@code site:<key>} page text, {@code tr:<TYPE>:<id>:<field>} a translation, {@code rec:<TYPE>:<id>:<field>} the
 * English text stored on a record.
 */
@Service
public class WebsiteRevisions {

    private final WebRevisionRepository revisions;

    public WebsiteRevisions(WebRevisionRepository revisions) {
        this.revisions = revisions;
    }

    /** Records the new value when it differs from the previous one (and the starting point, before the first edit). */
    @Transactional
    public void saved(String target, WebsiteLang lang, String oldValue, String newValue) {
        if (Objects.equals(blankToNull(oldValue), blankToNull(newValue))) return;
        if (revisions.findTop30ByTargetAndLangOrderByIdDesc(target, lang.code()).isEmpty() && blankToNull(oldValue) != null) {
            save(target, lang, oldValue);
        }
        save(target, lang, blankToNull(newValue));
    }

    public static String record(WebsiteTranslatable type, Long id, String field) {
        return "rec:" + type.name() + ":" + id + ":" + field;
    }

    public static String translation(WebsiteTranslatable type, Long id, String field) {
        return "tr:" + type.name() + ":" + id + ":" + field;
    }

    public static String site(String key) {
        return "site:" + key;
    }

    private void save(String target, WebsiteLang lang, String value) {
        WebRevision r = new WebRevision();
        r.setTarget(target);
        r.setLang(lang.code());
        r.setValue(value);
        revisions.save(r);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
