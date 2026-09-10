package com.example.library_management.mapper;

import com.example.library_management.dto.response.RoleResponse;
import com.example.library_management.dto.response.RoleSummaryResponse;
import com.example.library_management.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = PermissionMapper.class)
public interface RoleMapper {
    RoleResponse toRoleResponse(Role role);

    @Mapping(target = "permissionCount", expression = "java(role.getPermissions().size())")
    RoleSummaryResponse toRoleSummaryResponse(Role role);
}
