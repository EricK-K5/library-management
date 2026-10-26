package com.example.library_management.repository;

import com.example.library_management.entity.Reservation;
import com.example.library_management.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(String userId);

    List<Reservation> findByBookId(Long bookId);

    // Phan trang cho man hinh thu thu; nap san user + book trong cung 1 query (tranh N+1)
    @EntityGraph(attributePaths = {"user", "book"})
    Page<Reservation> findByBookId(Long bookId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "book"})
    Page<Reservation> findAll(Pageable pageable);

    // Danh sach cho man hinh thu thu, loc tuy chon theo status va keyword (khop username / fullName cua user hoac title cua sach).
    // keyword da lowercase + boc %...% (hoac null = khong loc), status null = khong loc. Sort lay tu Pageable.
    // JOIN FETCH chi cho quan he to-one (user, book) nen phan trang van chay tren DB; countQuery rieng.
    @Query(value = """
            SELECT r FROM Reservation r
            JOIN FETCH r.user u JOIN FETCH r.book bk
            WHERE (:status IS NULL OR r.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            """,
            countQuery = """
            SELECT COUNT(r) FROM Reservation r
            JOIN r.user u JOIN r.book bk
            WHERE (:status IS NULL OR r.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            """)
    Page<Reservation> search(@Param("status") ReservationStatus status,
                             @Param("keyword") String keyword,
                             Pageable pageable);

    // Hang doi PENDING cua NHIEU sach trong 1 query, chi lay (bookId, reservationId), dung cung thu tu voi findQueueForUpdate.
    // Dung de tinh queuePosition cho ca 1 trang thay vi 1 query / reservation.
    @Query("""
            SELECT r.book.id, r.id FROM Reservation r
            WHERE r.book.id IN :bookIds AND r.status = :status
            ORDER BY r.book.id ASC, r.createdAt ASC, r.id ASC
            """)
    List<Object[]> findQueueRows(@Param("bookIds") Collection<Long> bookIds,
                                 @Param("status") ReservationStatus status);

    List<Reservation> findByStatus(ReservationStatus status);

    // Hang doi dat truoc cua 1 quyen sach, dau hang = createdAt som nhat, id nho nhat neu trung
    List<Reservation> findByBookIdAndStatusOrderByCreatedAtAscIdAsc(Long bookId, ReservationStatus status);

    // Lay ban ghi PENDING/ACCEPTED dau hang, dung pessimistic lock de tranh 2 thread cung promote 1 luc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.book.id = :bookId AND r.status = :status
            ORDER BY r.createdAt ASC, r.id ASC
            """)
    List<Reservation> findQueueForUpdate(@Param("bookId") Long bookId, @Param("status") ReservationStatus status);

    Optional<Reservation> findByUserIdAndBookIdAndStatus(String userId, Long bookId, ReservationStatus status);

    boolean existsByUserIdAndBookIdAndStatusIn(String userId, Long bookId, Collection<ReservationStatus> statuses);

    List<Reservation> findByUserIdAndBookIdAndStatusIn(String userId, Long bookId, Collection<ReservationStatus> statuses);

    long countByUserIdAndStatusIn(String userId, Collection<ReservationStatus> statuses);

    // Chan xoa user khi con reservation PENDING/ACCEPTED
    boolean existsByUserIdAndStatusIn(String userId, Collection<ReservationStatus> statuses);

    // Chi lay id (khong nap entity vao persistence context) - tung reservation se duoc khoa + doc lai khi xu ly
    @Query("SELECT r.id FROM Reservation r WHERE r.status = :status AND r.expiryDate < :date")
    List<Long> findIdsByStatusAndExpiryDateBefore(@Param("status") ReservationStatus status,
                                                  @Param("date") LocalDate date);

    // Cac sach ma user dang xep hang (PENDING), sap xep theo id de khoa Book luon theo 1 thu tu co dinh (tranh deadlock)
    @Query("""
            SELECT DISTINCT r.book.id FROM Reservation r
            WHERE r.user.id = :userId AND r.status = :status
            ORDER BY r.book.id
            """)
    List<Long> findBookIdsByUserIdAndStatus(@Param("userId") String userId,
                                            @Param("status") ReservationStatus status);

    // Reservation tao truoc moc thoi gian :before (dung de don PENDING qua lau)
    @Query("SELECT r.id FROM Reservation r WHERE r.status = :status AND r.createdAt < :before")
    List<Long> findIdsByStatusAndCreatedAtBefore(@Param("status") ReservationStatus status,
                                                 @Param("before") LocalDateTime before);

    // Lay bookId ma khong nap Reservation, de khoa Book truoc roi moi khoa Reservation (xem ReservationService.lockReservation)
    @Query("SELECT r.book.id FROM Reservation r WHERE r.id = :id")
    Optional<Long> findBookIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBookIdAndStatus(Long bookId, ReservationStatus status);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);

    boolean existsByBookIdAndStatusIn(Long bookId, Collection<ReservationStatus> statuses);
}