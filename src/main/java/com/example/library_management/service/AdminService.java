package com.example.library_management.service;

import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.UserMapper;
import com.example.library_management.repository.RoleRepository;
import com.example.library_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class AdminService {
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    RoleRepository roleRepository;

    //    THEM ROLE LIBRARIAN
    public UserResponse addLibrarian(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));

        Role librarianRole = roleRepository.findByName("LIBRARIAN")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        Role memberRole = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        if (!user.getRoles().contains(memberRole)) {
            throw new AppException(ErrorCode.USER_NOT_MEMBER);
        }

        user.getRoles().add(librarianRole);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    //      XOA ROLE LIBRARIAN
    public UserResponse removeLibrarian(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));

        Role librarianRole = roleRepository.findByName("LIBRARIAN")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        user.getRoles().remove(librarianRole);

        return userMapper.toUserResponse(userRepository.save(user));
    }
}
