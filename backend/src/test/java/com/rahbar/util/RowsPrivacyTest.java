package com.rahbar.util;

import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generic rows never carry the password hash or a person's PAN. */
class RowsPrivacyTest {

    @Test
    void passwordHashAndPanAreLeftOut() {
        User u = new User();
        u.setId(5L);
        u.setName("Zara");
        u.setPasswordHash("$2a$10$secret");
        u.setPan("ABCDE1234F");
        Map<String, Object> row = Rows.of(u);
        assertEquals("Zara", row.get("name"));
        assertFalse(row.containsKey("password_hash"));
        assertFalse(row.containsKey("pan"));
        assertFalse(row.containsValue("ABCDE1234F"));
    }
}
