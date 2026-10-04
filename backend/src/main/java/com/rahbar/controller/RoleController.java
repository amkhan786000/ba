package com.rahbar.controller;

import com.rahbar.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Role management screen of the Admin panel (Super Admin and Application Administrator only). */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAnyRole('1','2')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** Also feeds the role dropdown of the user screens. */
    @GetMapping
    public List<Map<String, Object>> listRoles() {
        return roleService.listRoles();
    }

    @GetMapping("/{roleId}")
    public Map<String, Object> getRole(@PathVariable Integer roleId) {
        return roleService.getRole(roleId);
    }

    @PostMapping
    public Map<String, Object> createRole(@RequestBody Map<String, String> body) {
        return roleService.createRole(body.get("roleName"), body.get("description"));
    }

    @PutMapping("/{roleId}")
    public Map<String, Object> updateRole(@PathVariable Integer roleId, @RequestBody Map<String, String> body) {
        return roleService.updateRole(roleId, body.get("roleName"), body.get("description"));
    }

    @DeleteMapping("/{roleId}")
    public Map<String, String> deleteRole(@PathVariable Integer roleId) {
        roleService.deleteRole(roleId);
        return Map.of("message", "Role deleted successfully!");
    }
}
