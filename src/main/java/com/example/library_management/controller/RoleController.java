package com.example.library_management.controller;

import com.example.library_management.dto.response.RoleResponse;
import com.example.library_management.dto.response.RoleSummaryResponse;
import com.example.library_management.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    // Danh sach role de hien thi cho user bam vao
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public List<RoleSummaryResponse> getAllRoles() {
        return roleService.getAllRoles();
    }

    // Chi tiet 1 role kem permission - goi khi bam vao role
    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('user:read')")
    public RoleResponse getRoleDetail(@PathVariable Long roleId) {
        return roleService.getRoleDetail(roleId);
    }
}
