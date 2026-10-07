package com.rahbar.web;

import com.rahbar.security.SponsorPrivacy;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;
import java.util.Map;

/** Applies SponsorPrivacy to every JSON response (maps and lists) before it is written. */
@RestControllerAdvice
public class SponsorPrivacyAdvice implements ResponseBodyAdvice<Object> {

    private final SponsorPrivacy sponsorPrivacy;

    public SponsorPrivacyAdvice(SponsorPrivacy sponsorPrivacy) {
        this.sponsorPrivacy = sponsorPrivacy;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if ((body instanceof Map<?, ?> || body instanceof Collection<?>) && sponsorPrivacy.hidesDetails()) {
            return sponsorPrivacy.mask(body);
        }
        return body;
    }
}
