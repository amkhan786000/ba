package com.rahbar.controller;

import com.rahbar.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Roles and their permissions (Admin > Roles). Each endpoint names the permission it needs. */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Also feeds the role dropdown of the user screens. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLES:VIEW') or hasAuthority('USERS:VIEW') or hasAuthority('MESSAGES:EDIT')")
    public List<Map<String, Object>> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/sections")
    @PreAuthorize("hasAuthority('ROLES:VIEW')")
    public List<Map<String, Object>> sections() {
        return roleService.sections();
    }

    /** Body: { "permissions": ["USERS:VIEW", "USERS:EDIT", ...], "scope": "ALL" | "CHAPTER" | "RCC" }. */
    @PutMapping("/{roleId}/access")
    @PreAuthorize("hasAuthority('ROLES:EDIT')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateAccess(@PathVariable Integer roleId, @RequestBody Map<String, Object> body) {
        Object permissions = body.get("permissions");
        return roleService.updateAccess(roleId,
                permissions instanceof java.util.Collection<?> c ? (java.util.Collection<String>) c : List.of(),
                body.get("scope") == null ? null : String.valueOf(body.get("scope")));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLES:VIEW')")
    public Map<String, Object> getRole(@PathVariable Integer roleId) {
        return roleService.getRole(roleId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLES:EDIT')")
    public Map<String, Object> createRole(@RequestBody Map<String, String> body) {
        return roleService.createRole(body.get("roleName"), body.get("description"));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLES:EDIT')")
    public Map<String, Object> updateRole(@PathVariable Integer roleId, @RequestBody Map<String, String> body) {
        return roleService.updateRole(roleId, body.get("roleName"), body.get("description"));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLES:EDIT')")
    public Map<String, String> deleteRole(@PathVariable Integer roleId) {
        roleService.deleteRole(roleId);
        return Map.of("message", "Role deleted successfully!");
    }
}
