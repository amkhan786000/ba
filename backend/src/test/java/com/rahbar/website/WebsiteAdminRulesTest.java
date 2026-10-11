package com.rahbar.website;

import com.rahbar.entity.Role;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Section;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Who may write, publish and see what in Admin > Website. */
class WebsiteAdminRulesTest {

    private final WebBlogPostRepository posts = mock(WebBlogPostRepository.class);
    private final WebProjectRepository projects = mock(WebProjectRepository.class);
    private final WebsiteNotifier notifier = mock(WebsiteNotifier.class);
    private final WebBlogCategoryRepository categories = mock(WebBlogCategoryRepository.class);
    private final WebsiteAdminPostsController.Blog blog =
            new WebsiteAdminPostsController.Blog(posts, projects, mock(UserRepository.class), notifier, categories);

    private final WebStoryRepository stories = mock(WebStoryRepository.class);
    private final WebsiteAdminPostsController.Stories storyAdmin = new WebsiteAdminPostsController.Stories(stories, projects,
            mock(ChapterRepository.class), mock(RccCenterRepository.class), mock(WebTranslationRepository.class),
            mock(UserRepository.class), notifier);

    @AfterEach
    void signOut() {
        WebsiteTestUsers.signOut();
    }

    private static WebBlogPost post(String title, boolean published, boolean review) {
        WebBlogPost p = new WebBlogPost();
        p.setTitle(title);
        p.setSlug(WebsiteSupport.slugify(title));
        p.setTopic("Community");
        p.setContent("Text");
        p.setPublished(published);
        p.setReviewRequested(review);
        return p;
    }

    @Test
    void writersCannotPublishButPublishersAreAskedToReview() {
        WebsiteTestUsers.signIn(7, "WEBSITE_BLOG:VIEW");
        when(posts.save(any())).thenAnswer(i -> i.getArgument(0));
        WebBlogPost saved = blog.create(post("My trip home", true, true));
        assertFalse(saved.isPublished());
        assertTrue(saved.isReviewRequested());
        verify(notifier).notify(eq(Section.WEBSITE_BLOG_PUBLISH), isNull(), isNull(), contains("My trip home"), anyString(), anyString());

        WebsiteTestUsers.signIn(1, "WEBSITE_BLOG:VIEW", "WEBSITE_BLOG_PUBLISH:EDIT");
        saved = blog.create(post("Annual meeting", true, true));
        assertTrue(saved.isPublished());
        assertFalse(saved.isReviewRequested());
    }

    @Test
    void writersSeeOnlyTheirOwnPostsAndCannotChangePublishedOnes() {
        WebsiteTestUsers.signIn(7, "WEBSITE_BLOG:VIEW");
        when(posts.findByCreatedByOrderByIdDesc(7L)).thenReturn(List.of());
        blog.list();
        verify(posts).findByCreatedByOrderByIdDesc(7L);
        verify(posts, never()).findAll(any(Sort.class));

        WebBlogPost someoneElses = post("Other", false, false);
        someoneElses.setCreatedBy(8L);
        when(posts.findById(5L)).thenReturn(Optional.of(someoneElses));
        assertThrows(ApiException.class, () -> blog.get(5L));

        WebBlogPost mine = post("Mine", true, false);
        mine.setCreatedBy(7L);
        when(posts.findById(6L)).thenReturn(Optional.of(mine));
        assertThrows(ApiException.class, () -> blog.update(6L, post("Mine changed", false, false)));
    }

    @Test
    void withoutThePermissionNothingIsShown() {
        WebsiteTestUsers.signIn(9, "DASHBOARD:VIEW");
        assertThrows(ApiException.class, blog::list);
        assertThrows(ApiException.class, storyAdmin::list);
    }

    private static WebStory story(Long chapterId) {
        WebStory s = new WebStory();
        s.setTitle("From Grade 10 to diploma engineer");
        s.setSlug("grade-10-to-diploma");
        s.setPersonName("Sana Parveen Khatoon");
        s.setStory("She topped her centre.");
        s.setConsentGiven(true);
        s.setChapterId(chapterId);
        return s;
    }

    @Test
    void chapterLeadsStoriesAreTiedToTheirChapterAndNeedConsent() {
        WebsiteTestUsers.signIn(20, Role.SCOPE_CHAPTER, 3L, null, "WEBSITE_STORIES:EDIT");
        when(stories.save(any())).thenAnswer(i -> i.getArgument(0));
        WebStory saved = storyAdmin.create(story(99L));
        assertEquals(3L, saved.getChapterId());
        assertFalse(saved.isPublished());

        WebStory noConsent = story(3L);
        noConsent.setConsentGiven(false);
        assertThrows(ApiException.class, () -> storyAdmin.create(noConsent));

        when(stories.findAll(any(Sort.class))).thenReturn(List.of(story(3L), story(4L)));
        @SuppressWarnings("unchecked")
        List<WebStory> visible = (List<WebStory>) storyAdmin.list().get("data");
        assertEquals(1, visible.size());
        assertEquals(3L, visible.get(0).getChapterId());
    }

    @Test
    void rccCoordinatorsStoriesGoToTheirCenterAndTheCoachingProject() {
        WebsiteTestUsers.signIn(21, Role.SCOPE_RCC, null, 7L, "WEBSITE_STORIES:EDIT");
        WebProject rcc = new WebProject();
        rcc.setId(1L);
        when(projects.findBySlugAndActiveTrue(WebsiteAdminPostsController.COACHING_PROJECT)).thenReturn(Optional.of(rcc));
        when(stories.save(any())).thenAnswer(i -> i.getArgument(0));
        WebStory saved = storyAdmin.create(story(null));
        assertEquals(7L, saved.getRccCenterId());
        assertEquals(1L, saved.getProjectId());
    }

    @Test
    void revisionTargetsBelongToTheirSection() {
        assertEquals(Section.WEBSITE_PAGES, WebsiteAdminContentController.sectionOf("site:home.hero.title"));
        assertEquals(Section.WEBSITE_NEWS, WebsiteAdminContentController.sectionOf("rec:NEWS:4:content"));
        assertEquals(Section.WEBSITE_STORIES, WebsiteAdminContentController.sectionOf("tr:STORY:2:story"));
        assertThrows(ApiException.class, () -> WebsiteAdminContentController.sectionOf("users:1"));
    }

    @Test
    void revisionsKeepTheStartingPointAndSkipUnchangedText() {
        WebRevisionRepository repo = mock(WebRevisionRepository.class);
        WebsiteRevisions revisions = new WebsiteRevisions(repo);
        revisions.saved("site:a", WebsiteLang.EN, "same", "same");
        verify(repo, never()).save(any());
        when(repo.findTop30ByTargetAndLangOrderByIdDesc("site:a", "en")).thenReturn(List.of());
        revisions.saved("site:a", WebsiteLang.EN, "old", "new");
        verify(repo, times(2)).save(any()); // the old text first, then the new one
        assertEquals(Map.of(), Map.of());
    }
}
