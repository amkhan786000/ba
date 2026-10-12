package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.entity.UserAdditionalRole;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserAdditionalRoleRepository;
import com.rahbar.security.Access;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The roles a person has besides their main one (Admin > Users). A person is one user record; giving them another
 * role (e.g. a Super Admin who also sponsors) adds it here instead of making a second account.
 */
@Service
public class AdditionalRoleService {

    private final UserAdditionalRoleRepository additional;
    private final RoleRepository roles;

    public AdditionalRoleService(UserAdditionalRoleRepository additional, RoleRepository roles) {
        this.additional = additional;
        this.roles = roles;
    }

    public List<Integer> of(Long userId) {
        return additional.findByUserId(userId).stream().map(UserAdditionalRole::getRoleId).sorted().toList();
    }

    /** Replaces the user's additional roles; their main role is never repeated here. Only a Super Admin grants Super Admin. */
    @Transactional
    public void set(User user, Collection<?> roleIds) {
        Set<Integer> wanted = new TreeSet<>();
        for (Object o : roleIds == null ? List.of() : roleIds) {
            if (o == null || String.valueOf(o).isBlank()) continue;
            Integer id;
            try {
                id = Integer.valueOf(String.valueOf(o).trim());
            } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown role.");
            }
            if (!roles.existsById(id)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown role.");
            if (!id.equals(user.getRoleId())) wanted.add(id);
        }
        List<UserAdditionalRole> current = additional.findByUserId(user.getId());
        Set<Integer> had = new TreeSet<>();
        current.forEach(r -> had.add(r.getRoleId()));
        if ((wanted.contains(Role.SUPER_ADMIN) != had.contains(Role.SUPER_ADMIN)) && !Access.isSuperAdmin()) {
            throw Access.forbidden("Only a Super Admin can manage Super Admin accounts.");
        }
        for (UserAdditionalRole r : current) if (!wanted.contains(r.getRoleId())) additional.delete(r);
        for (Integer id : wanted) {
            if (had.contains(id)) continue;
            UserAdditionalRole r = new UserAdditionalRole();
            r.setUserId(user.getId());
            r.setRoleId(id);
            additional.save(r);
        }
    }

    /** The main role became one of the additional ones: drop it from them. */
    @Transactional
    public void dropMain(User user) {
        additional.findByUserId(user.getId()).stream().filter(r -> r.getRoleId().equals(user.getRoleId())).forEach(additional::delete);
    }
}
