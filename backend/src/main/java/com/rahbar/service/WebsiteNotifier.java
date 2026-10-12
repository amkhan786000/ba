package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.service.WebsiteNotifier;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Section;
import com.rahbar.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * In-app notifications about the website (new message, a post or story waiting for review, a directory sign-up) to
 * everyone whose role may view that section, plus the Super Admin. Chapter / RCC-scoped users only hear about their
 * own chapter / center.
 */
@Component
public class WebsiteNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebsiteNotifier.class);

    private final RoleRepository roles;
    private final UserRepository users;
    private final NotificationService notifications;

    public WebsiteNotifier(RoleRepository roles, UserRepository users, NotificationService notifications) {
        this.roles = roles;
        this.users = users;
        this.notifications = notifications;
    }

    public void notify(Section section, Long chapterId, Long centerId, String title, String message, String link) {
        try {
            Map<Integer, Role> byId = new HashMap<>();
            String key = section.key(Section.Level.VIEW);
            for (Role r : roles.findAll()) {
                if (Integer.valueOf(Role.SUPER_ADMIN).equals(r.getRoleId()) || r.permissionSet().contains(key)) byId.put(r.getRoleId(), r);
            }
            if (byId.isEmpty()) return;
            List<User> people = users.findByRoleIdIn(new ArrayList<>(byId.keySet()));
            Map<Long, java.util.Set<Integer>> rolesOf = UserRoles.byUser(people, users);
            for (User u : people) {
                if ("Inactive".equalsIgnoreCase(u.getStatus())) continue;
                // any of their roles (main or additional) with the permission whose scope covers this record
                boolean reaches = rolesOf.getOrDefault(u.getId(), java.util.Set.of()).stream().map(byId::get).filter(java.util.Objects::nonNull).anyMatch(r -> {
                    String scope = r.getScope();
                    if (Role.SCOPE_CHAPTER.equals(scope)) return chapterId != null && chapterId.equals(u.getChapterId());
                    if (Role.SCOPE_RCC.equals(scope)) return centerId != null && centerId.equals(u.getRccCenterId());
                    return true;
                });
                if (reaches) notifications.notify(u.getId(), title, message, "WEBSITE", link, false);
            }
        } catch (RuntimeException e) {
            log.warn("Could not send website notification '{}': {}", title, e.getMessage());
        }
    }
}
