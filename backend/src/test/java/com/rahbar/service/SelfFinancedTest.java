package com.rahbar.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** BA Details > Self financed: Yes only when chosen; empty or anything else is No. */
class SelfFinancedTest {

    @Test
    void yesOnlyWhenChosen() {
        assertTrue(AdminService.yes(true));
        assertTrue(AdminService.yes("Yes"));
        assertTrue(AdminService.yes("Y"));
        assertFalse(AdminService.yes(false));
        assertFalse(AdminService.yes(null));
        assertFalse(AdminService.yes(""));
        assertFalse(AdminService.yes("no"));
    }
}
