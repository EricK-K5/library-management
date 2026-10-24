package com.example.library_management.dto.response;

import java.time.LocalDate;

/**
 * Kết quả sau khi user bấm "Xác nhận". Frontend phân biệt theo {@code type}.
 *
 * <ul>
 *   <li>RESERVE_BOOK: recordId = id reservation; status = ACCEPTED (đã giữ sách, expiryDate = hạn đến lấy)
 *       hoặc PENDING (hết sách, đang xếp hàng, có queuePosition). Trường không áp dụng sẽ là null.</li>
 * </ul>
 */
public record ChatActionResult(
        String type,
        Long recordId,
        String bookTitle,
        String status,
        LocalDate expiryDate,
        Integer queuePosition) {
}