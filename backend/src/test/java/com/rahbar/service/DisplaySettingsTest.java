package com.rahbar.service;

import com.rahbar.entity.AppSetting;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Application Settings: the date format is DD/MM/YYYY unless an admin picks one of the offered formats. */
class DisplaySettingsTest {

    private final AppSettingRepository repo = mock(AppSettingRepository.class);
    private final DisplaySettings settings = new DisplaySettings(repo);

    @Test
    void ddMmYyyyUntilChangedAndOnlyKnownFormats() {
        when(repo.findById("display.date_format")).thenReturn(Optional.empty());
        assertEquals("DD/MM/YYYY", settings.dateFormat());

        settings.save(Map.of("dateFormat", "YYYY-MM-DD"));
        verify(repo).save(argThat((AppSetting s) -> "YYYY-MM-DD".equals(s.getValue())));
        assertThrows(ApiException.class, () -> settings.save(Map.of("dateFormat", "MM-DD-YY")));

        AppSetting stored = new AppSetting();
        stored.setValue("garbage");
        when(repo.findById("display.date_format")).thenReturn(Optional.of(stored));
        assertEquals("DD/MM/YYYY", settings.dateFormat()); // an unknown stored value falls back
        stored.setValue("MM/DD/YYYY");
        assertEquals("MM/DD/YYYY", settings.dateFormat());
        verify(repo, times(1)).save(any());
    }
}
