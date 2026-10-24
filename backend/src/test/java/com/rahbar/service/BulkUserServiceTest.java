package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Manage Users > Bulk add users: name + chapter duplicates are left out and listed; phones made up when empty. */
class BulkUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ChapterRepository chapters = mock(ChapterRepository.class);
    private final BulkUserService service = new BulkUserService(users, roles, chapters, mock(PasswordEncoder.class));
    private final List<User> saved = new ArrayList<>();

    private static Role role(int id, String name, String scope) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName(name);
        r.setScope(scope);
        return r;
    }

    private static Chapter chapter(long id, String name) {
        Chapter c = new Chapter();
        c.setChapterId(id);
        c.setChapterName(name);
        return c;
    }

    @BeforeEach
    void setUp() {
        Phase3Support.signIn(1, 1, null, null, "USERS:EDIT");
        when(roles.findAll()).thenReturn(List.of(role(5, "Sponsor", "ALL"), role(6, "Student", "ALL"), role(9, "Chapter Lead", "CHAPTER")));
        Chapter patna = chapter(1, "Patna"), jeddah = chapter(2, "Jeddah");
        when(chapters.findAll()).thenReturn(List.of(patna, jeddah));
        when(chapters.findByChapterNameIgnoreCase("patna")).thenReturn(Optional.of(patna));
        when(chapters.findByChapterNameIgnoreCase("Patna")).thenReturn(Optional.of(patna));
        when(chapters.findByChapterNameIgnoreCase("Jeddah")).thenReturn(Optional.of(jeddah));
        User existing = new User();
        existing.setId(1L);
        existing.setUserId("1001");
        existing.setName("Mohammad Imran");
        existing.setRoleId(5);
        existing.setChapterId(1L);
        existing.setPhone("9800000001");
        when(users.findAll()).thenReturn(List.of(existing));
        when(users.findAllUserIds()).thenReturn(List.of("1001"));
        when(users.save(any())).thenAnswer(i -> { saved.add(i.getArgument(0)); return i.getArgument(0); });
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private BulkUserService.Result upload(String csv) throws Exception {
        return service.upload(new MockMultipartFile("file", "u.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void namesAreComparedTogetherWithTheChapter() throws Exception {
        BulkUserService.Result r = upload("""
                Name,Chapter Name,Phone Number,Role
                Md. Imran,Patna,,Sponsor
                Md Imran,Jeddah,,Sponsor
                Sara Khan,Jeddah,9811111111,Sponsor
                sara  khan,Jeddah,,Sponsor
                """);
        // same name + same chapter as an existing user, and a repeat inside the file: not added, listed
        assertEquals(List.of("Md. Imran", "sara  khan"), r.duplicates().stream().map(d -> d.get("name")).toList());
        assertTrue(String.valueOf(r.duplicates().get(0).get("looks_like")).contains("Mohammad Imran (1001, Sponsor, Patna)"));
        assertEquals("row 4 of this file", r.duplicates().get(1).get("looks_like"));
        // same name in another chapter is someone else: added
        assertEquals(List.of("Md Imran", "Sara Khan"), r.added().stream().map(a -> a.get("name")).toList());
        assertEquals(2, saved.size());
    }

    @Test
    void anEmptyPhoneGetsAMadeUpNumberNoRealMobileHas() throws Exception {
        BulkUserService.Result r = upload("Name,Chapter Name,Phone Number,Role\nAbdul Rahman,Jeddah,,Sponsor\n");
        String phone = (String) r.added().get(0).get("phone");
        assertTrue(phone.matches("5\\d{9}"), phone);
        assertEquals(true, r.added().get(0).get("phone_generated"));
        assertEquals("1002@rahbar.com", r.added().get(0).get("sign_in"));
        assertTrue(saved.get(0).getMustChangePassword());
    }

    @Test
    void rowsThatCantBeAddedSayWhy() throws Exception {
        BulkUserService.Result r = upload("""
                Name,Chapter Name,Phone Number,Role
                A One,Nowhere,,Sponsor
                B Two,,,Chapter Lead
                C Three,Patna,,Student
                D Four,Patna,12345,Sponsor
                E Five,Patna,9800000001,Sponsor
                F Six,Patna,,Pilot
                """);
        assertTrue(r.added().isEmpty());
        List<String> reasons = ((List<Map<String, Object>>) r.report().toMap().get("problems")).stream().map(p -> String.valueOf(p.get("reason"))).toList();
        assertTrue(reasons.get(0).contains("doesn't exist"));
        assertTrue(reasons.get(1).contains("needs a chapter"));
        assertTrue(reasons.get(2).contains("Student Directory"));
        assertTrue(reasons.get(3).contains("10-digit"));
        assertTrue(reasons.get(4).contains("already belongs"));
        assertTrue(reasons.get(5).contains("Unknown role"));
    }

    @Test
    void canonicalNames() {
        assertEquals("md imran", BulkUserService.canonical(" Mohd.  Imran "));
        assertEquals("md imran", BulkUserService.canonical("Muhammad Imran"));
        assertEquals("md imran", BulkUserService.canonical("MD IMRAN"));
        assertEquals("9876543210", BulkUserService.mobile("+91 98765 43210"));
    }

    @Test
    void careOfIsTakenFromTheName() throws Exception {
        assertArrayEquals(new String[]{"Abdul Rahman", "Imran Khan"}, BulkUserService.splitCareOf("Abdul Rahman c/o Imran Khan"));
        assertArrayEquals(new String[]{"Abdul Rahman", "Imran Khan"}, BulkUserService.splitCareOf("Abdul Rahman C/O. Imran Khan"));
        assertArrayEquals(new String[]{"Abdul Rahman", "Imran Khan"}, BulkUserService.splitCareOf("Abdul Rahman (c/o Imran Khan)"));
        assertArrayEquals(new String[]{"Abdul Rahman", "Imran Khan"}, BulkUserService.splitCareOf("Abdul Rahman care of Imran Khan"));
        assertArrayEquals(new String[]{"Coconut Trader", null}, BulkUserService.splitCareOf("Coconut Trader"));

        BulkUserService.Result r = upload("Name,Chapter Name,Phone Number,Role\nAbdul Rahman c/o Imran Khan,Jeddah,9822222222,Sponsor\n");
        assertEquals("Abdul Rahman", saved.get(0).getName());
        assertEquals("Imran Khan", saved.get(0).getCareOf());
        assertEquals("Imran Khan", r.added().get(0).get("care_of"));
    }
}
