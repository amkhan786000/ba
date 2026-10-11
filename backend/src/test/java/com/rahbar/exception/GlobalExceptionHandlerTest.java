package com.rahbar.exception;

import com.rahbar.service.ErrorAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unexpected errors don't show internals; mistakes in the request are not reported as server errors. */
class GlobalExceptionHandlerTest {

    private final ErrorAlertService alerts = mock(ErrorAlertService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(alerts);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/students");

    @Test
    void anUnexpectedErrorShowsAReferenceNotTheTechnicalMessage() {
        when(alerts.record(any(), anyString())).thenReturn("K7Q2ZP");
        var r = handler.handleGeneric(new IllegalStateException("could not execute statement; SQL [insert into users ...]"), request);
        assertEquals(500, r.getStatusCode().value());
        String message = String.valueOf(r.getBody().get("error"));
        assertTrue(message.contains("K7Q2ZP"));
        assertFalse(message.contains("SQL"));
        verify(alerts).record(any(), eq("POST /api/admin/students"));
    }

    @Test
    void requestMistakesKeepTheirStatusAndAreNotReported() {
        var notFound = handler.handleGeneric(new NoResourceFoundException(HttpMethod.GET, "wp-login.php"), request);
        assertEquals(404, notFound.getStatusCode().value());
        var tooLarge = handler.handleGeneric(new MaxUploadSizeExceededException(25L * 1024 * 1024), request);
        assertEquals(413, tooLarge.getStatusCode().value());
        verifyNoInteractions(alerts);
    }

    @Test
    void aClosedConnectionIsNotReported() {
        assertNull(handler.handleGeneric(new RuntimeException(new java.io.IOException("Broken pipe")), request));
        verifyNoInteractions(alerts);
    }
}
