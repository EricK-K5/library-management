package com.example.library_management.repository;

import com.example.library_management.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    void existsByName_returnsTrueOnlyForSavedName() {
        categoryRepository.save(Category.builder().name("Science").build());

        assertTrue(categoryRepository.existsByName("Science"));
        assertFalse(categoryRepository.existsByName("History"));
    }
}