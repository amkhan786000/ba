package com.rahbar.controller;

import com.rahbar.entity.Course;
import com.rahbar.entity.RccCenter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.CourseRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.service.PublicService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The website's scholarship form: open only during an Application Period; names only; a link to Rahbar's tracker. */
class WebsiteScholarshipTest {

    private final PublicService publicService = mock(PublicService.class);
    private final RccCenterRepository centers = mock(RccCenterRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final WebsiteScholarshipController controller = new WebsiteScholarshipController(publicService, centers, courses);

    private static HttpServletRequest visitor(String ip) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getHeader("X-Forwarded-For")).thenReturn(ip);
        return r;
    }

    @Test
    void optionsSayWhetherTheFormIsOpenAndListOnlyNames() {
        ReflectionTestUtils.setField(controller, "rahbarUrls", "https://rahbar.biharanjuman.com/,http://194.195.87.118");
        RccCenter a = new RccCenter(), b = new RccCenter(), blank = new RccCenter();
        a.setCenterName("RCC Gaya");
        b.setCenterName(" rcc gaya ");
        blank.setCenterName(" ");
        Course c = new Course();
        c.setCourseName("Diploma in Civil Engineering");
        when(centers.findAll()).thenReturn(List.of(a, b, blank));
        when(courses.findAll()).thenReturn(List.of(c));
        when(publicService.isApplicationPeriodOpen()).thenReturn(false);

        Map<String, Object> o = controller.options();
        assertEquals(false, o.get("open"));
        assertEquals(List.of("RCC Gaya"), o.get("rccCenters"));
        assertEquals(List.of("Diploma in Civil Engineering"), o.get("courses"));
        assertEquals("https://rahbar.biharanjuman.com/track-application", o.get("trackUrl"));
    }

    @Test
    void applyReturnsTheApplicationNumberAndPassesClosedPeriodsOn() {
        when(publicService.apply(any())).thenReturn(42L);
        Map<String, Object> ok = controller.apply(Map.of("name", "Asif", "website", ""), visitor("1.1.1.1"));
        assertEquals(42L, ok.get("applicationId"));
        verify(publicService).apply(argThat(m -> !m.containsKey("website")));

        when(publicService.apply(any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Applications are currently closed."));
        ApiException closed = assertThrows(ApiException.class, () -> controller.apply(Map.of("name", "B"), visitor("2.2.2.2")));
        assertEquals(HttpStatus.FORBIDDEN, closed.getStatus());
    }

    @Test
    void botsFillingTheHiddenFieldAreIgnoredAndRepeatsAreLimited() {
        Map<String, Object> bot = controller.apply(Map.of("name", "x", "website", "spam.example"), visitor("3.3.3.3"));
        assertNull(bot.get("applicationId"));
        verify(publicService, never()).apply(any());

        when(publicService.apply(any())).thenReturn(1L);
        for (int i = 0; i < 3; i++) controller.apply(Map.of("name", "A"), visitor("4.4.4.4"));
        ApiException limited = assertThrows(ApiException.class, () -> controller.apply(Map.of("name", "A"), visitor("4.4.4.4")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatus());
    }
}
