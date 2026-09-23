package com.example.library_management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    Long id;

    String name;

    Set<PermissionResponse> permissions;
}
