package com.example.library_management.service;

import com.example.library_management.entity.Permission;
import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.repository.PermissionRepository;
import com.example.library_management.repository.RoleRepository;
import com.example.library_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeedingService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void seed() {
        // TAO ROLE
        Role adminRole = getOrCreateRole("ADMIN");
        Role memberRole = getOrCreateRole("MEMBER");
        Role librarianRole = getOrCreateRole("LIBRARIAN");

        // TAO PERMISSION (luon dam bao ton tai, khong anh huong role)
        Permission bookRead = createPermission("book:read", "View books", "BOOK");
        Permission bookCreate = createPermission("book:create", "Create books", "BOOK");
        Permission bookUpdate = createPermission("book:update", "Update books", "BOOK");
        Permission bookDelete = createPermission("book:delete", "Delete books", "BOOK");

        Permission userRead = createPermission("user:read", "View users", "USER");
        Permission userCreate = createPermission("user:create", "Create users", "USER");
        Permission userUpdate = createPermission("user:update", "Update users", "USER");
        Permission userDelete = createPermission("user:delete", "Delete users", "USER");

        Permission borrowRead = createPermission("borrow:read", "View borrow records", "BORROW");
        Permission borrowCreate = createPermission("borrow:create", "Create borrow records", "BORROW");
        Permission borrowReturn = createPermission("borrow:return", "Return books", "BORROW");
        Permission borrowRenew = createPermission("borrow:renew", "Renew borrowed books", "BORROW");
        Permission borrowManage = createPermission("borrow:manage", "Manage borrow records", "BORROW");

        Permission reservationRead = createPermission("reservation:read", "View reservations", "RESERVATION");
        Permission reservationCreate = createPermission("reservation:create", "Create reservations", "RESERVATION");
        Permission reservationCancel = createPermission("reservation:cancel", "Cancel reservations", "RESERVATION");
        Permission reservationManage = createPermission("reservation:manage", "Manage reservations", "RESERVATION");

        Permission fineRead = createPermission("fine:read", "View fines", "FINE");
        Permission fineCreate = createPermission("fine:create", "Create fines", "FINE");
        Permission finePay = createPermission("fine:pay", "Pay fines", "FINE");
        Permission fineWaive = createPermission("fine:waive", "Waive fines", "FINE");
        Permission fineManage = createPermission("fine:manage", "Manage fines", "FINE");

        Permission reportRead = createPermission("report:read", "View reports", "REPORT");
        Permission reportExport = createPermission("report:export", "Export reports", "REPORT");

        // GAN PERMISSION MAC DINH
        // Chi gan neu role dang KHONG co permission nao
        if (memberRole.getPermissions().isEmpty()) {
            memberRole.setPermissions(new HashSet<>(Set.of(
                    bookRead,
                    borrowRead, borrowCreate, borrowRenew,
                    reservationRead, reservationCreate, reservationCancel,
                    fineRead, finePay
            )));
            roleRepository.save(memberRole);
            log.info("Seeded default permissions for MEMBER role.");
        }

        if (librarianRole.getPermissions().isEmpty()) {
            librarianRole.setPermissions(new HashSet<>(Set.of(
                    bookRead, bookCreate, bookUpdate, bookDelete,
                    userRead,
                    borrowRead, borrowCreate, borrowReturn, borrowRenew, borrowManage,
                    reservationRead, reservationManage,
                    fineRead, fineCreate, finePay,fineManage,
                    reportRead
            )));
            roleRepository.save(librarianRole);
            log.info("Seeded default permissions for LIBRARIAN role.");
        }

        if (adminRole.getPermissions().isEmpty()) {
            adminRole.setPermissions(new HashSet<>(Set.of(
                    bookRead, bookCreate, bookUpdate, bookDelete,
                    userRead, userCreate, userUpdate, userDelete,
                    borrowRead, borrowCreate, borrowReturn, borrowRenew, borrowManage,
                    reservationRead, reservationCreate, reservationCancel, reservationManage,
                    fineRead, fineCreate, finePay, fineWaive, fineManage,
                    reportRead, reportExport
            )));
            roleRepository.save(adminRole);
            log.info("Seeded default permissions for ADMIN role.");
        }

        // TAO ADMIN USER
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@library.com")
                    .fullName("System Administrator")
                    .phoneNumber("0123456789")
                    .roles(new HashSet<>(Set.of(adminRole)))
                    .build();

            userRepository.save(admin);
            log.info("Default ADMIN account created.");
        }
    }

    private Role getOrCreateRole(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).build()
                ));
    }

    private Permission createPermission(String code, String description, String module) {
        return permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                                .code(code)
                                .description(description)
                                .module(module)
                                .build()
                ));
    }
}
