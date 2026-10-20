package com.example.library_management.event;

import java.time.LocalDate;

public class MailEvents {
//    Tranh khoi tao lai
    private MailEvents(){}

//    Dat sach thanh cong, giu
    public record ReservationAccept(
            String email,
            String fullName,
            String bookTitle,
            LocalDate expiryDate
    ){}

//    Het sach, doi
    public record ReservationQueued(
            String email,
            String fullName,
            String bookTitle,
            Integer queuePosition
    ){}

//    Giao sach
    public record BookBorrowed(
            String email,
            String fullName,
            String bookTitle,
            LocalDate borrowDate,
            LocalDate dueDate
){}
}
