package com.example.library_management.mapper;

import com.example.library_management.dto.request.UserCreationRequest;
import com.example.library_management.dto.request.UserUpdateRequest;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.Set;
import java.util.stream.Collectors;

//MAPSTRUCT TU GENERATE RA, DKY THANH SPRING BEAN
@Mapper(componentModel = "spring")

public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    default Set<String> mapRoles(Set<Role> roles) {
        if (roles == null) {
            return null;
        }

        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    @Mapping(target = "roles", ignore = true)
    void updateUser(@MappingTarget User user, UserUpdateRequest userUpdateRequest);
}
