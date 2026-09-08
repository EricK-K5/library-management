package com.example.library_management.repository;

import com.example.library_management.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {

    Optional<Category> findByName(String name);

    // Category gốc (không có cha)
//    List<Category> findByParentIsNull();

    // Category con của 1 category cha
//    List<Category> findByParentId(String parentId);
}
