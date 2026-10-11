package com.rahbar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahbar.entity.Chapter;
import com.rahbar.entity.RccCenter;
import com.rahbar.entity.User;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.rahbar.dto.PublicCenter;

/** The public lists share names, descriptions / locations and lead names only; students only as a count. */
class PublicDirectoryServiceTest {

    private static Chapter chapter(long id, String name, boolean active) {
        Chapter c = new Chapter();
        c.setChapterId(id);
        c.setChapterName(name);
        c.setDescription("  Chapter of " + name + " ");
        c.setLeadName("Lead " + name);
        c.setLeadPhone("+966500000000");
        c.setLeadEmail("lead@" + name.toLowerCase() + ".example");
        c.setActive(active);
        return c;
    }

    private static RccCenter center(long id, String name) {
        RccCenter c = new RccCenter();
        c.setRccCenterId(id);
        c.setCenterName(name);
        c.setLocation(name + ", Jharkhand");
        c.setInchargeName("Incharge " + name);
        c.setContactNumber("9999999999");
        return c;
    }

    private final UserRepository users = mock(UserRepository.class);

    private PublicDirectoryService service(List<Chapter> chapters, List<RccCenter> centers) {
        ChapterRepository cr = mock(ChapterRepository.class);
        RccCenterRepository rr = mock(RccCenterRepository.class);
        when(cr.findAllByOrderByChapterNameAsc()).thenReturn(chapters);
        when(rr.findAll()).thenReturn(centers);
        return new PublicDirectoryService(cr, rr, users);
    }

    private static User user(long id, Long mergedInto) {
        User u = new User();
        u.setId(id);
        u.setUserId("STU-" + id);
        u.setMergedIntoId(mergedInto);
        return u;
    }

    @Test
    void onlyActiveChaptersWithoutContactDetails() throws Exception {
        var svc = service(List.of(chapter(1, "Darbhanga", true), chapter(2, "Old", false)), List.of());
        var list = svc.chapters();
        assertEquals(1, list.size());
        var c = list.get(0);
        assertEquals(1L, c.id());
        assertEquals("Darbhanga", c.name());
        assertEquals("Chapter of Darbhanga", c.description());
        assertEquals("Lead Darbhanga", c.leadName());
        String json = new ObjectMapper().writeValueAsString(list);
        assertFalse(json.contains("+966"), json);
        assertFalse(json.contains("@"), json);
    }

    @Test
    void centersSortedByNameWithoutPhoneNumbers() throws Exception {
        var svc = service(List.of(), List.of(center(2, "Jamshedpur"), center(1, "hazaribagh")));
        var list = svc.centers();
        assertEquals(List.of("hazaribagh", "Jamshedpur"), list.stream().map(PublicCenter::name).toList());
        assertEquals("Incharge hazaribagh", list.get(0).inchargeName());
        assertEquals("hazaribagh, Jharkhand", list.get(0).location());
        assertFalse(new ObjectMapper().writeValueAsString(list).contains("9999999999"));
    }

    @Test
    void countsStudentsWithoutMergedDuplicates() {
        // 4 was merged into 1
        when(users.findByRoleId(6)).thenReturn(List.of(user(1, null), user(2, null), user(3, null), user(4, 1L)));
        assertEquals(3, service(List.of(), List.of()).stats().students());
    }
}
