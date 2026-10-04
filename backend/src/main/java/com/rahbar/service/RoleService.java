package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/** Role management (Admin only). Roles 1-8 are built in: the application's access rules depend on their ids. */
@Service
public class RoleService {

    /** Ids the code relies on (Super Admin, App Admin, Coordinator, Convenor, Sponsor, Student, Management, Office Coordinator). */
    public static final Set<Integer> BUILT_IN_ROLES = Set.of(1, 2, 3, 4, 5, 6, 7, 8);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    /** Every role with how many users have it and whether it is built in. */
    public List<Map<String, Object>> listRoles() {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] r : userRepository.countPerRole()) counts.put((Integer) r[0], (Long) r[1]);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Role role : roleRepository.findAllByOrderByRoleIdAsc()) {
            Map<String, Object> row = toRow(role);
            row.put("userCount", counts.getOrDefault(role.getRoleId(), 0L));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> getRole(Integer roleId) {
        Map<String, Object> row = toRow(require(roleId));
        row.put("userCount", userRepository.countByRoleId(roleId));
        return row;
    }

    public Map<String, Object> createRole(String roleName, String description) {
        String name = validName(roleName);
        if (roleRepository.existsByRoleNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A role named '" + name + "' already exists.");
        }
        Role role = new Role();
        role.setRoleId(roleRepository.nextId());
        role.setRoleName(name);
        role.setDescription(blankToNull(description));
        return toRow(roleRepository.save(role));
    }

    public Map<String, Object> updateRole(Integer roleId, String roleName, String description) {
        Role role = require(roleId);
        String name = validName(roleName);
        if (roleRepository.existsByRoleNameIgnoreCaseAndRoleIdNot(name, roleId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A role named '" + name + "' already exists.");
        }
        role.setRoleName(name);
        role.setDescription(blankToNull(description));
        return toRow(roleRepository.save(role));
    }

    public void deleteRole(Integer roleId) {
        Role role = require(roleId);
        if (BUILT_IN_ROLES.contains(roleId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'" + role.getRoleName() + "' is a built-in role and can't be deleted.");
        }
        long users = userRepository.countByRoleId(roleId);
        if (users > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot delete role '" + role.getRoleName() + "': " + users + " user(s) still have it. Change their role first.");
        }
        try {
            roleRepository.delete(role);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete role '" + role.getRoleName() + "': it is still in use.");
        }
    }

    private Role require(Integer roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Role not found."));
    }

    private static String validName(String roleName) {
        String name = roleName == null ? "" : roleName.trim();
        if (name.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Role name is required.");
        if (name.length() > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "Role name can be at most 50 characters.");
        return name;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Same JSON keys as the Role entity (roleId, roleName, ...) so existing screens keep working. */
    private static Map<String, Object> toRow(Role role) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleId", role.getRoleId());
        row.put("roleName", role.getRoleName());
        row.put("description", role.getDescription());
        row.put("builtIn", BUILT_IN_ROLES.contains(role.getRoleId()));
        row.put("createdAt", role.getCreatedAt());
        row.put("updatedAt", role.getUpdatedAt());
        return row;
    }
}
