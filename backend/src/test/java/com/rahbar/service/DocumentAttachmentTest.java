package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.OrgDocumentCategoryRepository;
import com.rahbar.repository.OrgDocumentRepository;
import com.rahbar.repository.OrgDocumentRevisionRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Admin > Documents: a PDF or Word file can be attached, with or without text; only real PDF / Word files; a new
 * file keeps the old one with the earlier version; only the document's readers can open it.
 */
class DocumentAttachmentTest {

    private final OrgDocumentRepository documents = mock(OrgDocumentRepository.class);
    private final OrgDocumentCategoryRepository categories = mock(OrgDocumentCategoryRepository.class);
    private final OrgDocumentRevisionRepository revisions = mock(OrgDocumentRevisionRepository.class);
    private final OrgDocumentService service = new OrgDocumentService(documents, categories, revisions, mock(UserRepository.class));
    private final FileStorageService files = new FileStorageService();

    @TempDir
    Path uploads;

    private static void signIn(int roleId, String... permissions) {
        User u = new User();
        u.setId(1L);
        u.setRoleId(roleId);
        var p = new RahbarUserPrincipal(u, Set.of(permissions), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @BeforeEach
    void setUp() {
        signIn(2, "DOCUMENTS:EDIT");
        ReflectionTestUtils.setField(files, "uploadDir", uploads.toString());
        ReflectionTestUtils.setField(service, "files", files);
        when(categories.existsById(3L)).thenReturn(true);
        when(documents.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void signOut() { SecurityContextHolder.clearContext(); }

    private static Map<String, Object> body(String content) {
        Map<String, Object> b = new HashMap<>();
        b.put("categoryId", 3);
        b.put("title", "By-laws");
        b.put("content", content);
        return b;
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void aFileCanBeTheWholeDocument() {
        OrgDocument d = service.save(null, body(""), pdf("By-laws 2024.pdf"));
        assertEquals("", d.getContent());
        assertEquals("By-laws 2024.pdf", d.getAttachmentName());
        assertTrue(d.getAttachmentPath().startsWith(OrgDocumentService.FILE_PREFIX));
        assertTrue(uploads.resolve(d.getAttachmentPath()).toFile().isFile());
    }

    @Test
    void textOrAFileIsNeeded() {
        assertThrows(ApiException.class, () -> service.save(null, body("  "), null));
    }

    @Test
    void onlyRealPdfOrWordFiles() {
        byte[] docx = {'P', 'K', 3, 4, 0, 0};
        byte[] doc = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        assertDoesNotThrow(() -> OrgDocumentService.checkFile(new MockMultipartFile("file", "a.docx", null, docx)));
        assertDoesNotThrow(() -> OrgDocumentService.checkFile(new MockMultipartFile("file", "a.DOC", null, doc)));
        assertDoesNotThrow(() -> OrgDocumentService.checkFile(pdf("a.pdf")));
        assertThrows(ApiException.class, () -> OrgDocumentService.checkFile(new MockMultipartFile("file", "a.pdf", "application/pdf", docx))); // renamed
        assertThrows(ApiException.class, () -> OrgDocumentService.checkFile(new MockMultipartFile("file", "a.exe", null, docx)));
        assertThrows(ApiException.class, () -> OrgDocumentService.checkFile(new MockMultipartFile("file", "a.html", "text/html", "<html>".getBytes())));
    }

    @Test
    void aNewFileKeepsTheOldOneWithTheEarlierVersion() {
        OrgDocument d = new OrgDocument();
        d.setId(7L);
        d.setCategoryId(3L);
        d.setTitle("By-laws");
        d.setContent("");
        d.setAttachmentPath("orgdoc_1_old.pdf");
        d.setAttachmentName("old.pdf");
        when(documents.findById(7L)).thenReturn(Optional.of(d));
        service.save(7L, body(""), pdf("new.pdf"));
        ArgumentCaptor<OrgDocumentRevision> r = ArgumentCaptor.forClass(OrgDocumentRevision.class);
        verify(revisions).save(r.capture());
        assertEquals("orgdoc_1_old.pdf", r.getValue().getAttachmentPath());
        assertEquals("old.pdf", r.getValue().getAttachmentName());
        assertEquals("new.pdf", d.getAttachmentName());
    }

    @Test
    void removingTheFileNeedsText() {
        OrgDocument d = new OrgDocument();
        d.setId(7L);
        d.setCategoryId(3L);
        d.setTitle("By-laws");
        d.setContent("");
        d.setAttachmentPath("orgdoc_1_old.pdf");
        when(documents.findById(7L)).thenReturn(Optional.of(d));
        Map<String, Object> b = body("");
        b.put("removeFile", true);
        assertThrows(ApiException.class, () -> service.save(7L, b, null));
        b.put("content", "Article 1");
        service.save(7L, b, null);
        assertNull(d.getAttachmentPath());
    }

    @Test
    void onlyTheDocumentsReadersCanOpenTheFile() {
        OrgDocument stored = service.save(null, body(""), pdf("policy.pdf"));
        stored.setId(9L);
        stored.setVisibility("ROLES");
        stored.setVisibleRoleIds("2");
        when(documents.findById(9L)).thenReturn(Optional.of(stored));
        OrgDocumentService.Attachment a = service.file(9L, null);
        assertEquals("policy.pdf", a.name());
        assertEquals("application/pdf", a.contentType());

        signIn(5, "DASHBOARD:VIEW"); // a sponsor: not one of the readers
        assertThrows(ApiException.class, () -> service.file(9L, null));
    }

    @Test
    void theUploadedNameIsKeptWithoutFolders() {
        assertEquals("By-laws.pdf", OrgDocumentService.attachmentName("C:\\Users\\me\\By-laws.pdf"));
        assertEquals("document", OrgDocumentService.attachmentName(null));
    }
}
