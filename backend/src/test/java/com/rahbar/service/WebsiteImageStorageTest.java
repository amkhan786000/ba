package com.rahbar.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Website uploads are recognised by their bytes, not by their name. */
class WebsiteImageStorageTest {

    @Test
    void imagesAreRecognisedByTheirBytes() {
        assertEquals("jpg", WebsiteImageStorage.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("png", WebsiteImageStorage.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0}));
        assertNull(WebsiteImageStorage.detect("<html>".getBytes()));
    }

    @Test
    void pdfsAreRecognised() {
        assertEquals("pdf", WebsiteImageStorage.detect("%PDF-1.7".getBytes()));
    }
}
