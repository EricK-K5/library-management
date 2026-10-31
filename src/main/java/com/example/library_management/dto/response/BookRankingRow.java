package com.example.library_management.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookRankingRow {
    int rank;
    Long bookId;
    String bookTitle;
    String category;
    long borrowCount;
}
