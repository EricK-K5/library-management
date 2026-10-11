package com.example.library_management.service;


import com.example.library_management.dto.request.UserCreationRequest;
import com.example.library_management.dto.request.UserUpdateRequest;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class UserService {
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    RoleRepository roleRepository;
    FileStorageService fileStorageService;

    //    CREATE USER
    @Transactional
    public UserResponse createUser(UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USERS_EXISTED);
        }
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        Role role = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new RuntimeException("Role MEMBER not found"));
        user.setRoles(new HashSet<>(Set.of(role)));
        user = userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    //    GET USER
    public UserResponse getUser(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }

    //    GET ALL USER
    public List<UserResponse> getAllUser() {
        return userRepository.findAll().stream().map(userMapper::toUserResponse).toList();
    }

    //    UPDATE USER
    @Transactional
    public UserResponse updateUser(String id, UserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
        userMapper.updateUser(user, request);
//        CHỈ HASH KHI CÓ PASSWORD
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    //    DELETE USER
    @Transactional
    public void deleteUser(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
        userRepository.delete(user);
        // Xoa luon anh avatar (chi chay sau khi commit thanh cong)
        fileStorageService.deleteAfterCommit(user.getAvatarUrl());
    }

    //    AVATAR CUA CHINH MINH
    @Transactional
    public UserResponse updateMyAvatar(MultipartFile file) {
        return changeAvatar(findByUsernameOrThrow(getCurrentUsername()), file);
    }

    @Transactional
    public UserResponse deleteMyAvatar() {
        return removeAvatar(findByUsernameOrThrow(getCurrentUsername()));
    }

    //    AVATAR CUA USER KHAC (ADMIN)
    @Transactional
    public UserResponse updateAvatar(String id, MultipartFile file) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
        return changeAvatar(user, file);
    }

    @Transactional
    public UserResponse deleteAvatar(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
        return removeAvatar(user);
    }

    private UserResponse changeAvatar(User user, MultipartFile file) {
        String oldUrl = user.getAvatarUrl();
        user.setAvatarUrl(fileStorageService.store(
                file, FileStorageService.AVATAR_FOLDER, FileStorageService.AVATAR_MAX_BYTES));
        User saved = userRepository.save(user);
        fileStorageService.deleteAfterCommit(oldUrl);
        return userMapper.toUserResponse(saved);
    }

    private UserResponse removeAvatar(User user) {
        String oldUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        User saved = userRepository.save(user);
        fileStorageService.deleteAfterCommit(oldUrl);
        return userMapper.toUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getMyInfo() {
        User user = findByUsernameOrThrow(getCurrentUsername());
        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse updateMyInfo(UserUpdateRequest request) {
        User user = findByUsernameOrThrow(getCurrentUsername());

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            // <<< TUỲ CHỌN: nếu có existsByEmail, kiểm tra trùng email với người khác
            // if (!request.getEmail().equalsIgnoreCase(user.getEmail())
            //         && userRepository.existsByEmail(request.getEmail())) {
            //     throw new AppException(ErrorCode.EMAIL_EXISTED);
            // }
            user.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getDob() != null) {
            user.setDob(request.getDob());
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        return userMapper.toUserResponse(saved);
    }

    //    lấy username từ SecurityContext
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
//                isAuthenticated = user xác thực hay chưa, nếu chưa thì trả về false
                || !authentication.isAuthenticated()
//                Authentication này có phải là anonymous user không, do anonymous token có thể vẫn có isAuthenticated() == true
//                SPRING SECURITY tạo ra 1 object authentication đặc biệt cho người chưa đăng nhập
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication.getName();
    }

    private User findByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}

