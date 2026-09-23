package com.example.library_management.exception;

import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Getter
public enum ErrorCode {
    INVALID_KEY("Invalid key", 0001, HttpStatus.BAD_REQUEST),
    UNCATEGORIZED_EXCEPTION("Uncategorized error", 9999, HttpStatus.INTERNAL_SERVER_ERROR),
    USERS_EXISTED("User existed", 1002, HttpStatus.BAD_REQUEST),
    USER_INVALID("User must be at least 4 character", 1003, HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD("Password must be least at 8 character", 1004, HttpStatus.BAD_REQUEST),
    USERS_NOT_EXISTED("User not existed", 1005, HttpStatus.NOT_FOUND),
    UNAUTHENTICATED("Unauthenticate", 1006, HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("You don't had permission", 1007, HttpStatus.FORBIDDEN),
    INVALID_DOB("Your age must be at least {min}", 1008, HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED("Role not existed", 1009, HttpStatus.BAD_REQUEST),
    USER_NOT_MEMBER("User is not member", 1010, HttpStatus.BAD_REQUEST),

    CATEGORY_EXISTED("Category existed", 2001, HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_EXISTED("Category not existed", 2002, HttpStatus.NOT_FOUND),
    CATEGORY_IN_USE("Category is in use", 2003, HttpStatus.BAD_REQUEST),


    BOOK_ISBN_EXISTED("Book ISBN existed", 3001, HttpStatus.BAD_REQUEST),
    BOOK_NOT_EXISTED("Book not existed", 3002, HttpStatus.NOT_FOUND),
    BOOK_HAS_BORROW_RECORDS("Book has borrow records", 3003, HttpStatus.BAD_REQUEST),
    BOOK_NOT_AVAILABLE("Book is not available", 3004, HttpStatus.BAD_REQUEST),
    BOOK_DISCONTINUED("Book is discontinued", 3005, HttpStatus.BAD_REQUEST),
    TOTAL_COPIES_LESS_THAN_BORROWED("Total copies cannot be less than borrowed copies", 3005, HttpStatus.BAD_REQUEST),

    BORROW_RECORD_NOT_EXISTED("Borrow record not existed", 4001, HttpStatus.NOT_FOUND),
    BORROW_LIMIT_EXCEEDED("Borrow limit exceeded", 4002, HttpStatus.BAD_REQUEST),
    BORROW_ALREADY_RETURNED("Borrow record already returned", 4003, HttpStatus.BAD_REQUEST),
    BORROW_ALREADY_LOST("Borrow record already marked as LOST", 4004, HttpStatus.BAD_REQUEST),
    BORROW_CANNOT_MARK_LOST("Borrow record cannot be marked as LOST", 4005, HttpStatus.BAD_REQUEST),


    RESERVATION_NOT_EXISTED("Reservation not existed", 5001, HttpStatus.NOT_FOUND),
    RESERVATION_ALREADY_PROCESSED("Reservation already processed", 5002, HttpStatus.BAD_REQUEST),
    RESERVATION_DUPLICATED("Reservation duplicated", 5003, HttpStatus.BAD_REQUEST),
    RESERVATION_NOT_PENDING("Reservation not pending", 5004, HttpStatus.BAD_REQUEST),
    RESERVATION_HAS_ACTIVE_PENDING("Reservation has active pending", 5005, HttpStatus.BAD_REQUEST),
    RESERVATION_NOT_ACCEPTED("Reservation not accepted", 5006, HttpStatus.BAD_REQUEST),

    FINE_NOT_UNPAID("Fine is not unpaid", 6000, HttpStatus.BAD_REQUEST),
    FINE_REASON_NOT_ALLOWED("Fine reason is not allowed", 6003, HttpStatus.BAD_REQUEST),
    FINE_ALREADY_EXISTED("Fine already existed", 6004, HttpStatus.BAD_REQUEST),
    FINE_NOT_EXISTED("Fine not existed", 6005, HttpStatus.NOT_FOUND),

    USER_HAS_OVERDUE_BOOK("User has overdue books", 7001, HttpStatus.BAD_REQUEST),
    USER_HAS_UNPAID_FINE("User has unpaid fine", 7002, HttpStatus.BAD_REQUEST);


    ErrorCode(String message, int code, HttpStatus httpStatus) {
        this.message = message;
        this.code = code;
        this.httpStatus = httpStatus;
    }

    int code;
    String message;
    HttpStatus httpStatus;
}
