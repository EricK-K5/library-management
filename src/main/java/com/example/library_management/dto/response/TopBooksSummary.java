package com.example.library_management.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopBooksSummary {
    long totalBorrowTransactions;
    long distinctBooksBorrowed;
    // null khi khong co luot muon nao trong ky
    Long mostBorrowedBookId;
    String mostBorrowedBookTitle;
    Long mostBorrowedBorrowCount;
}
