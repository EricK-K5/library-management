package com.example.library_management.enums;

public enum Code {
    PROCESSED,          // xu ly xong (thanh cong hoac that bai, xem txStatus)
    ALREADY_CONFIRMED,  // giao dich da duoc xu ly truoc do (IPN den truoc Return hoac nguoc lai)
    NOT_FOUND,          // khong co vnp_TxnRef nay
    INVALID_AMOUNT,     // vnp_Amount khong khop so tien luc tao giao dich
    INVALID_SIGNATURE   // sai chu ky
}
