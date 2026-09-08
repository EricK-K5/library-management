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
    PERMISSION_EXISTED("Permission existed", 1011, HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_FOUND("Permission not found", 1012, HttpStatus.NOT_FOUND);

    ErrorCode(String message, int code, HttpStatus httpStatus) {
        this.message = message;
        this.code = code;
        this.httpStatus = httpStatus;
    }

    int code;
    String message;
    HttpStatus httpStatus;
}
