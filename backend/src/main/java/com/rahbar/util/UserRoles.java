package com.rahbar.util;

import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;

import java.util.*;

/**
 * Does a user have a role? Their main role (users.role_id) or one of their additional roles (user_additional_roles).
 * For the signed-in user, Access.current().hasRole(...) answers without a query.
 */
public final class UserRoles {

    private UserRoles() {
    }

    public static boolean has(User user, int roleId, UserRepository users) {
        return hasAny(user, List.of(roleId), users);
    }

    public static boolean hasAny(User user, Collection<Integer> roleIds, UserRepository users) {
        if (user == null) return false;
        if (user.getRoleId() != null && roleIds.contains(user.getRoleId())) return true;
        return user.getId() != null && users.hasAdditionalRole(user.getId(), roleIds);
    }

    /** users.id -> all their roles (main first, then additional ones), with one query for the additional ones. */
    public static Map<Long, Set<Integer>> byUser(Collection<User> people, UserRepository users) {
        Map<Long, Set<Integer>> out = new HashMap<>();
        for (User u : people) {
            Set<Integer> r = new LinkedHashSet<>();
            if (u.getRoleId() != null) r.add(u.getRoleId());
            out.put(u.getId(), r);
        }
        List<Object[]> extra = users.allAdditionalRoles();
        for (Object[] x : extra == null ? List.<Object[]>of() : extra) {
            Set<Integer> r = out.get((Long) x[0]);
            if (r != null) r.add((Integer) x[1]);
        }
        return out;
    }
}
