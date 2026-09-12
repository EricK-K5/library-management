package com.example.library_management.mapper;

import com.example.library_management.dto.response.PermissionResponse;
import com.example.library_management.entity.Permission;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PermissionMapper {
//    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);

//    void updatePermission(@MappingTarget Permission permission, PermissionRequest request);
}
