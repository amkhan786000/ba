package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Materials: stock = received - handed out, never below zero; only one's own students; items kept by the office. */
class MaterialServiceTest {

    private final MaterialItemRepository items = mock(MaterialItemRepository.class);
    private final MaterialReceiptRepository receipts = mock(MaterialReceiptRepository.class);
    private final MaterialDistributionRepository distributions = mock(MaterialDistributionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final CoachingAccess coaching = mock(CoachingAccess.class);
    private final MaterialService service = new MaterialService(items, receipts, distributions, users, mock(CoachingStudentRepository.class),
            mock(RebStudentRepository.class), scope, coaching);

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    @BeforeEach
    void setUp() {
        Phase3Support.signIn(1, 2, null, null, "MATERIALS:VIEW", "MATERIALS:EDIT");
        MaterialItem book = new MaterialItem();
        book.setId(1L);
        book.setName("Science book");
        when(items.findById(1L)).thenReturn(Optional.of(book));
        MaterialReceipt in = new MaterialReceipt();
        in.setItemId(1L);
        in.setQuantity(5);
        when(receipts.findAll()).thenReturn(List.of(in));
        MaterialDistribution out = new MaterialDistribution();
        out.setItemId(1L);
        out.setQuantity(2);
        when(distributions.findAll()).thenReturn(List.of(out));
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        User s1 = student(10L), s2 = student(11L);
        when(users.findByRoleId(6)).thenReturn(List.of(s1, s2));
    }

    private static User student(Long id) {
        User u = new User();
        u.setId(id);
        u.setUserId("ST" + id);
        u.setName("Student " + id);
        u.setRoleId(6);
        u.setStatus("active");
        return u;
    }

    private static Map<String, Object> give(int each, Long... ids) {
        List<Map<String, Object>> who = new ArrayList<>();
        for (Long id : ids) who.add(Map.of("kind", "SCHOLAR", "id", id));
        return new HashMap<>(Map.of("itemId", 1, "quantity", each, "recipients", who));
    }

    @Test
    void stockIsReceivedMinusHandedOut() {
        assertEquals(3, service.inStock(1L));
        MaterialItem book = items.findById(1L).orElseThrow();
        when(items.findAllByOrderByNameAsc()).thenReturn(List.of(book));
        Map<String, Object> row = service.items().get(0);
        assertEquals(5L, row.get("received"));
        assertEquals(3L, row.get("in_stock"));
    }

    @Test
    void handsOutToSeveralStudentsWhenTheStockCoversEveryone() {
        Map<String, Object> out = service.give(give(1, 10L, 11L));
        verify(distributions, times(2)).save(any(MaterialDistribution.class));
        assertTrue(String.valueOf(out.get("message")).contains("1 left in stock"));
    }

    @Test
    void notMoreThanTheStock() {
        ApiException e = assertThrows(ApiException.class, () -> service.give(give(2, 10L, 11L)));
        assertTrue(e.getMessage().contains("Only 3"));
        verify(distributions, never()).save(any());
    }

    @Test
    void onlyToStudentsTheUserMaySee() {
        assertThrows(ApiException.class, () -> service.give(give(1, 99L)));
        when(scope.current()).thenReturn(StudentScope.Scope.NONE);
        assertThrows(ApiException.class, () -> service.give(give(1, 10L)));
    }

    @Test
    void stockAlreadyHandedOutCantBeRemoved() {
        MaterialReceipt big = new MaterialReceipt();
        big.setItemId(1L);
        big.setQuantity(5);
        when(receipts.findById(7L)).thenReturn(Optional.of(big));
        assertThrows(ApiException.class, () -> service.deleteReceipt(7L)); // 3 left - 5 < 0
    }

    @Test
    void aChapterLeadDoesntChangeItemsOrStock() {
        Phase3Support.signIn(2, 9, com.rahbar.entity.Role.SCOPE_CHAPTER, 7L, "MATERIALS:VIEW", "MATERIALS:EDIT");
        assertThrows(ApiException.class, () -> service.saveItem(null, new HashMap<>(Map.of("name", "Pens"))));
        assertThrows(ApiException.class, () -> service.addReceipt(new HashMap<>(Map.of("itemId", 1, "quantity", 3))));
    }
}
