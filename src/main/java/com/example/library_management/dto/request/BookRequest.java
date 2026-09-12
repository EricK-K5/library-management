package com.example.library_management.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookRequest {

    @NotBlank(message = "Tieu de khong duoc de trong")
    @Size(max = 255)
    String title;

    @Size(max = 20)
    String isbn;

    @NotBlank(message = "Tac gia khong duoc de trong")
    String author;

    @Size(max = 150)
    String publisher;

    Integer publishYear;

    @Size(max = 20)
    String language;

    @Size(max = 1000)
    String description;

    String coverImageUrl;

    @NotNull(message = "Tong so ban khong duoc de trong")
    @Min(value = 0, message = "Tong so ban phai >= 0")
    Integer totalCopies;

    @Size(max = 50)
    String shelfLocation;

    @NotNull(message = "Category khong duoc de trong")
    Long categoryId;
}