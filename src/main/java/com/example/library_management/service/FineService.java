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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    // MEMBER xem phi phat cua chinh minh
    @Transactional(readOnly = true)
    public List<FineResponse> getMyFines() {
        User user = getCurrentUser();
        return fineRepository.findByUserId(user.getId())
                .stream()
                .map(fineMapper::toFineResponse)
                .toList();
    }

    // LIBRARIAN/ADMIN tra cuu: loc theo userId va/hoac status (deu khong bat buoc)
    @Transactional(readOnly = true)
    public List<FineResponse> getFines(String userId, FineStatus status) {
        List<Fine> fines;
        if (StringUtils.hasText(userId) && status != null) {
            fines = fineRepository.findByUserIdAndStatus(userId, status);
        } else if (StringUtils.hasText(userId)) {
            fines = fineRepository.findByUserId(userId);
        } else if (status != null) {
            fines = fineRepository.findByStatus(status);
        } else {
            fines = fineRepository.findAll();
        }
        return fines.stream().map(fineMapper::toFineResponse).toList();
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
        return fineMapper.toFineResponse(fineRepository.save(fine));
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
        return fineMapper.toFineResponse(fineRepository.save(fine));
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
}
