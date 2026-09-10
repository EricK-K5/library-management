package com.example.library_management.exception;

import com.example.library_management.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.Objects;
@Slf4j
@ControllerAdvice
public class GlobalException {
    private static final String MIN_ATTRIBUTE = "min";

//    EXCEPTION
    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handleException(Exception e) {
        log.error("Uncategorized error: ", e);
        ApiResponse apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getHttpStatus()).body(apiResponse);
    }
//    APP EXCEPTION
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handlingAppException(AppException exception) {
        log.error("Uncategorized error: ", exception);
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
    }

//    VALID EXCEPTION
    @ExceptionHandler(value =MethodArgumentNotValidException .class)
    ResponseEntity<ApiResponse> handlingException(MethodArgumentNotValidException exception) {
        log.error("Uncategorized error: ", exception);
        String enumKey = exception.getFieldError().getDefaultMessage();
        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
            var constrainViolation =
                    exception.getBindingResult().getAllErrors().getFirst().unwrap(ConstraintViolation.class);
            attributes = constrainViolation.getConstraintDescriptor().getAttributes();
        }
        catch (IllegalArgumentException e) {
        }

        ApiResponse apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(
                Objects.nonNull(attributes)
                ?mapAttribute(errorCode.getMessage(), attributes)
                        :errorCode.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(apiResponse);
        }
        private String mapAttribute(String message, Map<String, Object> attribute) {
            String minValue = String.valueOf(attribute.get(MIN_ATTRIBUTE));
            return message.replace("{" + MIN_ATTRIBUTE + "}", minValue);
        }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse> handlingAccessDeniedException(AccessDeniedException exception) {
        log.error("Access denied error: ", exception);
        ApiResponse apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNAUTHORIZED.getMessage());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus()).body(apiResponse);
    }
}
