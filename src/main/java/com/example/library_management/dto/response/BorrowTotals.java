package com.example.library_management.dto.response;

/** Tong so luot muon va so dau sach khac nhau trong ky bao cao. */
public record BorrowTotals(Long totalTransactions, Long distinctBooks) {
}
