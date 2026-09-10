package com.example.library_management.service;

import com.example.library_management.dto.response.RoleResponse;
import com.example.library_management.dto.response.RoleSummaryResponse;
import com.example.library_management.entity.Role;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.RoleMapper;
import com.example.library_management.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public List<RoleSummaryResponse> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(roleMapper::toRoleSummaryResponse)
                .toList();
        }
    @Transactional(readOnly = true)
    public RoleResponse getRoleDetail(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));
        return roleMapper.toRoleResponse(role);
    }
}
