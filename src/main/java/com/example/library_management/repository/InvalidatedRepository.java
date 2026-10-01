package com.example.library_management.repository;

import com.example.library_management.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public interface InvalidatedRepository extends JpaRepository<InvalidatedToken, String> {
    @Modifying
    @Transactional
    void deleteByExpiryTimeBefore(Date now);
}