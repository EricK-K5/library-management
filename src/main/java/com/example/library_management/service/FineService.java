package com.example.library_management.service;

import com.example.library_management.dto.request.FineCreationRequest;
import com.example.library_management.dto.request.WaiveFineRequest;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.User;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.FineMapper;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.library_management.entity.Book;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.util.OverduePolicy;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class FineService {

    FineRepository fineRepository;
    BorrowRecordRepository borrowRecordRepository;
    UserRepository userRepository;
    FineMapper fineMapper;
    ReservationService reservationService;
    private static final BigDecimal FINE_PER_DAY =
            new BigDecimal("5000");
    private static final int MAX_OVERDUE_FINE_DAYS = 30;

    private static final BigDecimal LOST_PROCESSING_FEE =
            new BigDecimal("20000");

    private static final BigDecimal DEFAULT_REPLACEMENT_COST =
            new BigDecimal("100000");

    // Tao hoac cap nhat Fine qua han.
// Chi FineService duoc tinh so tien phat.
    @Transactional
    public void createOrUpdateOverdueFine(
            BorrowRecord borrowRecord,
            LocalDate today
    ) {
        long lateDays = OverduePolicy.lateDays(
                borrowRecord.getDueDate(),
                today
        );

        if (lateDays <= 0) {
            return;
        }

        long chargedDays = Math.min(lateDays, MAX_OVERDUE_FINE_DAYS);

        BigDecimal amount = FINE_PER_DAY.multiply(
                BigDecimal.valueOf(chargedDays)
        );

        boolean finalized =
                borrowRecord.getStatus() == BorrowStatus.RETURNED
                        || borrowRecord.getStatus() == BorrowStatus.LOST;

        String note = (finalized ? "Tre han " : "Tam tinh: tre han ")
                + lateDays + " ngay"
                + (lateDays > chargedDays
                ? " (tinh toi da " + MAX_OVERDUE_FINE_DAYS + " ngay)"
                : "");

        saveOrUpdateFine(
                borrowRecord,
                FineReason.OVERDUE,
                amount,
                today,
                note
        );
    }

    // Tao hoac cap nhat Fine khi sach bi mat.
    @Transactional
    public void createOrUpdateLostBookFine(
            BorrowRecord borrowRecord,
            Book book,
            BigDecimal lostAmountOverride,
            LocalDate today,
            boolean auto
    ) {
        BigDecimal amount;
        String note;

        if (lostAmountOverride != null) {
            amount = lostAmountOverride;
            note = "Mat sach: so tien do thu thu dinh gia";
        } else {
            BigDecimal price = book.getPrice() != null
                    ? book.getPrice()
                    : DEFAULT_REPLACEMENT_COST;

            amount = price.add(LOST_PROCESSING_FEE);

            note = "Mat sach: gia sach " + price.toPlainString()
                    + (book.getPrice() == null
                    ? " (mac dinh, chua nhap gia)"
                    : "")
                    + " + phi xu ly "
                    + LOST_PROCESSING_FEE.toPlainString();
        }

        if (auto) {
            note = "Tu dong. " + note;
        }

        saveOrUpdateFine(
                borrowRecord,
                FineReason.LOST_BOOK,
                amount,
                today,
                note
        );
    }

    // Scheduler goi moi ngay de cap nhat Fine qua han.
// Khong tao them Fine cho cac record chua qua han.
    @Transactional
    public int refreshOverdueFineEstimates() {
        LocalDate today = LocalDate.now();

        List<BorrowRecord> records =
                borrowRecordRepository.findByStatusInAndDueDateBefore(
                        List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE),
                        today
                );

        for (BorrowRecord record : records) {
            createOrUpdateOverdueFine(record, today);
        }

        return records.size();
    }

    // Moi borrow record chi co mot Fine cho moi reason.
    private void saveOrUpdateFine(
            BorrowRecord borrowRecord,
            FineReason reason,
            BigDecimal amount,
            LocalDate issuedDate,
            String note
    ) {
        Fine fine = fineRepository
                .findByBorrowRecordIdAndReason(borrowRecord.getId(), reason)
                .orElse(null);

        if (fine != null) {
            if (fine.getStatus() == FineStatus.UNPAID) {
                fine.setAmount(amount);
                fine.setNote(truncate(note));
                fineRepository.save(fine);
            }
            return;
        }

        fine = Fine.builder()
                .user(borrowRecord.getUser())
                .borrowRecord(borrowRecord)
                .amount(amount)
                .reason(reason)
                .status(FineStatus.UNPAID)
                .issuedDate(issuedDate)
                .note(truncate(note))
                .build();

        Fine saved = fineRepository.save(fine);

        if (borrowRecord.getFines() != null) {
            borrowRecord.getFines().add(saved);
        }
    }

    private String truncate(String text) {
        return text != null && text.length() > 255
                ? text.substring(0, 255)
                : text;
    }
    // Kiểm tra user có khoản phạt chưa thanh toán hay không.
    @Transactional(readOnly = true)
    public boolean hasUnpaidFine(String userId) {
        return fineRepository.existsByUserIdAndStatus(
                userId,
                FineStatus.UNPAID
        );
    }


    // MEMBER xem phi phat cua chinh minh
    @Transactional(readOnly = true)
    public List<FineResponse> getMyFines() {
        User user = getCurrentUser();
        return fineRepository.findByUserId(user.getId())
                .stream()
                .map(fineMapper::toFineResponse)
                .toList();
    }

    // LIBRARIAN/ADMIN tra cuu, phan trang: loc theo userId va/hoac status (deu khong bat buoc)
    @Transactional(readOnly = true)
    public Page<FineResponse> getFines(
            String keyword,
            FineStatus status,
            Pageable pageable
    ) {
        return fineRepository.searchFines(
                        StringUtils.hasText(keyword)
                                ? keyword.trim()
                                : null,
                        status,
                        pageable
                )
                .map(fineMapper::toFineResponse);
    }

    @Transactional(readOnly = true)
    public FineResponse getFineById(Long id) {
        return fineMapper.toFineResponse(findFine(id));
    }

    // Thu tien tai quay: UNPAID -> PAID
    @Transactional
    public FineResponse payFine(Long id) {
        User staff = getCurrentUser();
        Fine fine = findFine(id);
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw new AppException(ErrorCode.FINE_NOT_UNPAID);
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        fine.setProcessedBy(staff);
        Fine saved = fineRepository.save(fine);
        // user vua het no -> xet lai hang doi cac sach ho dang PENDING (truoc do bi bo qua vi con phat)
        reservationService.promoteQueuesForUser(saved.getUser().getId());
        return fineMapper.toFineResponse(saved);
    }

    // Thanh toan online thanh cong (VNPay): UNPAID -> PAID. PaymentService goi, da khoa dong Fine.
    // processedBy de null vi khong co nhan vien nao xu ly; note ghi ma giao dich VNPay.
    @Transactional
    public void markPaidOnline(Fine fine, String vnpTransactionNo) {
        fine.setStatus(FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        String payNote = "Thanh toan VNPay" + (StringUtils.hasText(vnpTransactionNo) ? " #" + vnpTransactionNo : "");
        String merged = StringUtils.hasText(fine.getNote()) ? fine.getNote() + " | " + payNote : payNote;
        fine.setNote(truncate(merged));
        fineRepository.save(fine);
        // user het no -> xet lai hang doi dat sach, giong payFine tai quay
        reservationService.promoteQueuesForUser(fine.getUser().getId());
    }

    // Mien phat (ADMIN): UNPAID -> WAIVED, bat buoc ghi ly do
    @Transactional
    public FineResponse waiveFine(Long id, WaiveFineRequest request) {
        User staff = getCurrentUser();
        Fine fine = findFine(id);
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw new AppException(ErrorCode.FINE_NOT_UNPAID);
        }
        fine.setStatus(FineStatus.WAIVED);
        fine.setProcessedBy(staff);
        String waiveNote = "Mien: " + request.getReason();
        String merged = StringUtils.hasText(fine.getNote()) ? fine.getNote() + " | " + waiveNote : waiveNote;
        fine.setNote(merged.length() > 255 ? merged.substring(0, 255) : merged);
        Fine saved = fineRepository.save(fine);
        reservationService.promoteQueuesForUser(saved.getUser().getId());
        return fineMapper.toFineResponse(saved);
    }

    // Phat thu cong
    @Transactional
    public FineResponse createManualFine(FineCreationRequest request) {
        User staff = getCurrentUser();

        if (request.getReason() != FineReason.DAMAGED_BOOK) {
            throw new AppException(ErrorCode.FINE_REASON_NOT_ALLOWED);
        }
        BorrowRecord borrowRecord = borrowRecordRepository.findById(request.getBorrowRecordId())
                .orElseThrow(() -> new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));
        if (fineRepository.existsByBorrowRecordIdAndReason(borrowRecord.getId(), request.getReason())) {
            throw new AppException(ErrorCode.FINE_ALREADY_EXISTED);
        }

        Fine fine = Fine.builder()
                .user(borrowRecord.getUser())
                .borrowRecord(borrowRecord)
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(FineStatus.UNPAID)
                .issuedDate(LocalDate.now())
                .processedBy(staff)
                .note(request.getNote())
                .build();
        return fineMapper.toFineResponse(fineRepository.save(fine));
    }

    private Fine findFine(Long id) {
        return fineRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FINE_NOT_EXISTED));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }

    //    Fines chưa thanh toán
    public BigDecimal getTotalUnpaidAmount() {
        return fineRepository.sumAmountByStatus(FineStatus.UNPAID);
    }
}
