package com.rahbar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final com.rahbar.service.ErrorAlertService alerts;

    public GlobalExceptionHandler(com.rahbar.service.ErrorAlertService alerts) {
        this.alerts = alerts;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
    }

    /** A permission check (@PreAuthorize) refused the request. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "You do not have permission to do this."));
    }

    /** A unique value clashed (e.g. an email or phone number another user already has): say so plainly, no SQL. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(org.springframework.dao.DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Duplicate entry '([^']*)'").matcher(detail);
        String message = m.find()
                ? (m.group(1).isEmpty() ? "A required value is empty." : "\"" + m.group(1) + "\" is already used by another user or record.")
                : "The change conflicts with existing data and was not saved.";
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).warn("Data conflict: {}", detail);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", message));
    }

    /**
     * Anything unexpected: the user sees a short reference instead of the technical message (which could show SQL
     * or other internals); the full error goes to the log and to the alert email (ErrorAlertService).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> client = clientError(ex);
        if (client != null) return client;
        if (clientGone(ex)) return null; // the browser closed the connection (e.g. a cancelled download): nothing to answer
        String ref = alerts.record(ex, request.getMethod() + " " + request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong on our side. Please try again; if it keeps happening, tell the office (reference " + ref + ")."));
    }

    /**
     * Mistakes in the request itself (unknown address, wrong method, unreadable JSON, missing or wrong parameter,
     * file too large): answered with their own status and a plain message, and not reported as server errors.
     */
    static ResponseEntity<Map<String, Object>> clientError(Exception ex) {
        if (ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "The file is too large."));
        }
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || ex instanceof org.springframework.web.bind.MissingServletRequestParameterException
                || ex instanceof org.springframework.web.multipart.support.MissingServletRequestPartException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "The request is incomplete or not in the expected format."));
        }
        if (ex instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            String message = er.getStatusCode().value() == 404 ? "Not found." : er.getStatusCode().value() == 405 ? "This action isn't allowed here."
                    : "The request could not be handled.";
            return ResponseEntity.status(er.getStatusCode()).body(Map.of("error", message));
        }
        return null;
    }

    /** The client hung up while we were answering (closed tab, cancelled download). */
    static boolean clientGone(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName();
            String msg = t.getMessage() == null ? "" : t.getMessage();
            if (name.equals("ClientAbortException") || name.equals("AsyncRequestNotUsableException") || msg.contains("Broken pipe")
                    || msg.contains("Connection reset by peer")) return true;
        }
        return false;
    }
}
