package com.example.library_management.service;

import com.example.library_management.dto.response.*;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportExcelService {

    public static final String SHEET_SUMMARY = "Summary";
    public static final String SHEET_FINE_DETAILS = "Fine Details";
    public static final String SHEET_BOOK_RANKINGS = "Book Rankings";

    private static final String DATE_FORMAT = "dd/mm/yyyy";
    private static final String DATETIME_FORMAT = "dd/mm/yyyy hh:mm";
    private static final String AMOUNT_FORMAT = "#,##0";
    private static final String COUNT_FORMAT = "#,##0";


    public byte[] buildFineReport(FineReportResponse report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            // ----- Sheet 1: Summary -----
            Sheet summary = wb.createSheet(SHEET_SUMMARY);
            summary.setColumnWidth(0, 44 * 256);
            summary.setColumnWidth(1, 36 * 256);
            int r = 0;
            r = title(summary, r, "Báo cáo vi phạm", styles);
            r = dateTimeRow(summary, r, "Ngày tạo báo cáo", report.getGeneratedAt(), styles);
            r = textRow(summary, r, "Căn cứ ngày", "Ngày ban hành quyết định xử phạt", styles);
            r = dateRow(summary, r, "Từ ngày", report.getStartDate(), styles);
            r = dateRow(summary, r, "Đến ngày", report.getEndDate(), styles);
            r = textRow(summary, r, "Bộ lọc trạng thái", report.getStatus() == null ? "ALL" : report.getStatus().name(), styles);
            r = textRow(summary, r, "Bộ lọc loại vi phạm", report.getFineType() == null ? "ALL" : report.getFineType().name(), styles);
            r = textRow(summary, r, "Ghi chú", "Các khoản tiền phạt tạm tính đối với sách chưa được hoàn trả không được bao gồm.", styles);
            r++;
            FineReportSummary s = report.getSummary();
            r = amountRow(summary, r, "Tổng số tiền phạt đã ghi nhận (VND)", s.getTotalRecordedAmount(), styles);
            r = amountRow(summary, r, "Tổng số tiền phạt đã thanh toán (VND)", s.getTotalPaidAmount(), styles);
            r = amountRow(summary, r, "Tổng số tiền phạt chưa thanh toán (VND)", s.getTotalUnpaidAmount(), styles);
            r = amountRow(summary, r, "Tổng số tiền phạt được miễn (VND)", s.getTotalWaivedAmount(), styles);
            countRow(summary, r, "Tổng số hồ sơ xử phạt", s.getTotalFineRecords(), styles);

            // ----- Sheet 2: Fine Details -----
            Sheet details = wb.createSheet(SHEET_FINE_DETAILS);
            String[] headers = {"Fine ID", "Member ID", "Member name", "Book title", "Fine type",
                    "Amount (VND)", "Status", "Issued date", "Paid date"};
            int[] widths = {10, 38, 28, 40, 16, 16, 12, 14, 14};
            header(details, headers, widths, styles);

            int row = 1;
            for (FineReportRow f : report.getDetails().getContent()) {
                Row x = details.createRow(row++);
                numberCell(x, 0, f.getFineId(), styles.count);
                x.createCell(1).setCellValue(nullToEmpty(f.getMemberId()));
                x.createCell(2).setCellValue(nullToEmpty(f.getMemberName()));
                x.createCell(3).setCellValue(nullToEmpty(f.getBookTitle()));
                x.createCell(4).setCellValue(f.getFineType() == null ? "" : f.getFineType().name());
                amountCell(x, 5, f.getAmount(), styles.amount);
                x.createCell(6).setCellValue(f.getStatus() == null ? "" : f.getStatus().name());
                dateCell(x, 7, f.getIssuedDate(), styles.date);
                dateCell(x, 8, f.getPaidDate(), styles.date);
            }
            details.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.REPORT_EXPORT_FAILED);
        }
    }


    public byte[] buildTopBooksReport(TopBooksReportResponse report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            // ----- Sheet 1: Summary -----
            Sheet summary = wb.createSheet(SHEET_SUMMARY);
            summary.setColumnWidth(0, 44 * 256);
            summary.setColumnWidth(1, 40 * 256);
            int r = 0;
            r = title(summary, r, "Báo cáo về các cuốn sách được mượn nhiều nhất", styles);
            r = dateTimeRow(summary, r, "Ngày tạo báo cáo", report.getGeneratedAt(), styles);
            r = textRow(summary, r, "Quy tắc đếm",
                    "Mỗi giao dịch cho mượn được bàn giao được tính là một lần", styles);
            r = textRow(summary, r, "Căn cứ ngày", "Ngày mượn sách", styles);
            r = dateRow(summary, r, "Từ ngày", report.getStartDate(), styles);
            r = dateRow(summary, r, "Đến ngày", report.getEndDate(), styles);
            r = textRow(summary, r, "Lọc theo thể loại",
                    report.getCategoryId() == null ? "ALL" : report.getCategoryName() + " (ID " + report.getCategoryId() + ")", styles);
            r = textRow(summary, r, "Giới hạn", report.getLimit() == null ? "ALL" : "Top " + report.getLimit(), styles);
            r++;
            TopBooksSummary s = report.getSummary();
            r = countRow(summary, r, "Tổng số lượt mượn", s.getTotalBorrowTransactions(), styles);
            r = countRow(summary, r, "Số đầu sách khác nhau được mượn", s.getDistinctBooksBorrowed(), styles);
            r = textRow(summary, r, "Sách được mượn nhiều nhất",
                    s.getMostBorrowedBookTitle() == null ? "N/A"
                            : s.getMostBorrowedBookTitle() + " (ID " + s.getMostBorrowedBookId() + ")", styles);
            if (s.getMostBorrowedBorrowCount() == null) {
                textRow(summary, r, "Số lượt mượn của sách nhiều nhất", "N/A", styles);
            } else {
                countRow(summary, r, "Số lượt mượn của sách nhiều nhất", s.getMostBorrowedBorrowCount(), styles);
            }

            // ----- Sheet 2: Book Rankings -----
            Sheet rankings = wb.createSheet(SHEET_BOOK_RANKINGS);
            String[] headers = {"Rank", "Book ID", "Book title", "Category", "Borrow count"};
            int[] widths = {8, 10, 44, 24, 14};
            header(rankings, headers, widths, styles);

            int row = 1;
            List<BookRankingRow> list = report.getRankings();
            for (BookRankingRow b : list) {
                Row x = rankings.createRow(row++);
                numberCell(x, 0, b.getRank(), styles.count);
                numberCell(x, 1, b.getBookId(), styles.count);
                x.createCell(2).setCellValue(nullToEmpty(b.getBookTitle()));
                x.createCell(3).setCellValue(nullToEmpty(b.getCategory()));
                numberCell(x, 4, b.getBorrowCount(), styles.count);
            }
            rankings.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.REPORT_EXPORT_FAILED);
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle title;
        final CellStyle label;
        final CellStyle date;
        final CellStyle dateTime;
        final CellStyle amount;
        final CellStyle count;

        Styles(Workbook wb) {
            DataFormat df = wb.createDataFormat();

            Font bold = wb.createFont();
            bold.setBold(true);

            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);

            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            title = wb.createCellStyle();
            title.setFont(big);

            label = wb.createCellStyle();
            label.setFont(bold);

            date = wb.createCellStyle();
            date.setDataFormat(df.getFormat(DATE_FORMAT));
            date.setAlignment(HorizontalAlignment.LEFT);

            dateTime = wb.createCellStyle();
            dateTime.setDataFormat(df.getFormat(DATETIME_FORMAT));
            dateTime.setAlignment(HorizontalAlignment.LEFT);

            amount = wb.createCellStyle();
            amount.setDataFormat(df.getFormat(AMOUNT_FORMAT));
            amount.setAlignment(HorizontalAlignment.RIGHT);

            count = wb.createCellStyle();
            count.setDataFormat(df.getFormat(COUNT_FORMAT));
            count.setAlignment(HorizontalAlignment.RIGHT);
        }
    }

    private static void header(Sheet sheet, String[] headers, int[] widths, Styles styles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.header);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private static int title(Sheet sheet, int r, String text, Styles styles) {
        Cell c = sheet.createRow(r).createCell(0);
        c.setCellValue(text);
        c.setCellStyle(styles.title);
        return r + 2;
    }

    private static Cell label(Sheet sheet, int r, String label, Styles styles) {
        Row row = sheet.createRow(r);
        Cell l = row.createCell(0);
        l.setCellValue(label);
        l.setCellStyle(styles.label);
        return row.createCell(1);
    }

    private static int textRow(Sheet sheet, int r, String label, String value, Styles styles) {
        label(sheet, r, label, styles).setCellValue(value);
        return r + 1;
    }

    private static int dateRow(Sheet sheet, int r, String label, LocalDate value, Styles styles) {
        Cell c = label(sheet, r, label, styles);
        if (value == null) {
            c.setCellValue("All");
        } else {
            c.setCellValue(value);
            c.setCellStyle(styles.date);
        }
        return r + 1;
    }

    private static int dateTimeRow(Sheet sheet, int r, String label, java.time.LocalDateTime value, Styles styles) {
        Cell c = label(sheet, r, label, styles);
        if (value != null) {
            c.setCellValue(value);
            c.setCellStyle(styles.dateTime);
        }
        return r + 1;
    }

    private static int amountRow(Sheet sheet, int r, String label, BigDecimal value, Styles styles) {
        Cell c = label(sheet, r, label, styles);
        c.setCellValue(value == null ? 0d : value.doubleValue());
        c.setCellStyle(styles.amount);
        return r + 1;
    }

    private static int countRow(Sheet sheet, int r, String label, long value, Styles styles) {
        Cell c = label(sheet, r, label, styles);
        c.setCellValue((double) value);
        c.setCellStyle(styles.count);
        return r + 1;
    }

    private static void numberCell(Row row, int col, Number value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value.doubleValue());
        }
        c.setCellStyle(style);
    }

    private static void amountCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0d : value.doubleValue());
        c.setCellStyle(style);
    }

    private static void dateCell(Row row, int col, LocalDate value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value);
            c.setCellStyle(style);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
