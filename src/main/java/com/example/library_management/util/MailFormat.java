package com.example.library_management.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MailFormat {
    private MailFormat() {}
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String date(LocalDate date){
        return date == null ? "" : DATE_TIME_FORMATTER.format(date);
    }

    public  static String money(BigDecimal amount){
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.of("vi", "VN"));
        numberFormat.setMaximumFractionDigits(0);
        return numberFormat.format(amount) + " VND";
    }
}
