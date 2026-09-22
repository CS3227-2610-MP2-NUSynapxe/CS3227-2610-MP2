package nusynapxe.ui;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import nusynapxe.domain.Receipt;

/** Formatting boundary for Receptionist checkout and receipt details. */
final class ReceptionistCheckoutView {
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private ReceptionistCheckoutView() {
    throw new AssertionError("Utility class");
  }

  static String formatReceipt(Receipt receipt) {
    return formatReceiptRow(receipt) + "\nRecorded: " + receipt.recordedAt() + "\nStatus: PAID";
  }

  private static String formatReceiptRow(Receipt receipt) {
    return String.format(
        Locale.ROOT,
        "Receipt %s-%04d | Date: %s | Patient: %s (%s) | Doctor: %s | Amount: %s | Method: %s",
        receipt.receiptDate(),
        receipt.sequenceNumber(),
        receipt.recordedAt().format(DATE_TIME_FORMAT),
        receipt.patientName(),
        receipt.patientId(),
        receipt.doctorName(),
        ReportExporter.formatMinor(receipt.amountMinor()),
        receipt.method());
  }
}
