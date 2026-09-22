package nusynapxe.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;

/** Serializes and saves revenue reports without owning any report-screen controls. */
public final class ReportExporter {
  private static final int JSON_CONTROL_CHARACTER_LIMIT = 0x20;

  private ReportExporter() {
    throw new AssertionError("Utility class");
  }

  /** Exports a report through the JavaFX file chooser. */
  public static void export(
      RevenueReport report, Window owner, boolean json, javafx.scene.control.Label feedback) {
    FileChooser chooser = new FileChooser();
    chooser.setInitialFileName(json ? "revenue-report.json" : "revenue-report.csv");
    chooser
        .getExtensionFilters()
        .add(
            new FileChooser.ExtensionFilter(
                json ? "JSON files" : "CSV files", json ? "*.json" : "*.csv"));
    java.io.File target = chooser.showSaveDialog(owner);
    if (target == null) {
      return;
    }
    try {
      Files.writeString(
          target.toPath(), json ? toJson(report) : toCsv(report), StandardCharsets.UTF_8);
      UiComponents.showMessage(feedback, "Revenue report exported");
    } catch (IOException exception) {
      UiComponents.showError(feedback, "Revenue report export failed");
    } catch (ArithmeticException exception) {
      UiComponents.showError(feedback, "Revenue total exceeds the supported range");
    }
  }

  /** Converts a report to the stable CSV export format. */
  public static String toCsv(RevenueReport report) {
    StringBuilder csv =
        new StringBuilder("receipt,dateTime,patientId,patientName,doctor,amount,method\n");
    for (Receipt receipt : report.receipts()) {
      csv.append(receipt.sequenceNumber())
          .append(',')
          .append(receipt.recordedAt())
          .append(',')
          .append(receipt.patientId())
          .append(',')
          .append(csvField(receipt.patientName()))
          .append(',')
          .append(csvField(receipt.doctorName()))
          .append(',')
          .append(formatMinor(receipt.amountMinor()))
          .append(',')
          .append(csvField(receipt.method()))
          .append('\n');
    }
    csv.append('\n')
        .append("summary,successfulPayments,receiptCount,total\n")
        .append("summary,")
        .append(report.receiptCount())
        .append(',')
        .append(report.receiptCount())
        .append(',')
        .append(formatMinor(report.totalMinor()))
        .append('\n');
    appendCsvTotals(csv, "paymentMethod", report.byMethod());
    appendCsvTotals(csv, "doctor", report.byDoctor());
    return csv.toString();
  }

  /** Converts a report to the stable JSON export format. */
  public static String toJson(RevenueReport report) {
    StringBuilder json =
        new StringBuilder("{\"successfulPaymentCount\":")
            .append(report.receiptCount())
            .append(",\"receiptCount\":")
            .append(report.receiptCount())
            .append(",\"total\":")
            .append(jsonString(formatMinor(report.totalMinor())))
            .append(",\"paymentMethods\":")
            .append(jsonTotals(report.byMethod()))
            .append(",\"doctors\":")
            .append(jsonTotals(report.byDoctor()))
            .append(",\"receipts\":[");
    for (int index = 0; index < report.receipts().size(); index++) {
      Receipt receipt = report.receipts().get(index);
      if (index > 0) {
        json.append(',');
      }
      json.append("{\"receiptNumber\":")
          .append(receipt.sequenceNumber())
          .append(",\"dateTime\":")
          .append(jsonString(receipt.recordedAt()))
          .append(",\"patientId\":")
          .append(receipt.patientId())
          .append(",\"patientName\":")
          .append(jsonString(receipt.patientName()))
          .append(",\"doctor\":")
          .append(jsonString(receipt.doctorName()))
          .append(",\"amount\":")
          .append(jsonString(formatMinor(receipt.amountMinor())))
          .append(",\"method\":")
          .append(jsonString(receipt.method()))
          .append('}');
    }
    return json.append("]}").toString();
  }

  static String formatMinor(long amountMinor) {
    return java.math.BigDecimal.valueOf(amountMinor, 2).toPlainString();
  }

  private static void appendCsvTotals(StringBuilder csv, String category, Map<?, Long> totals) {
    totals.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
        .forEach(
            entry ->
                csv.append(category)
                    .append(',')
                    .append(csvField(entry.getKey()))
                    .append(',')
                    .append(formatMinor(entry.getValue()))
                    .append('\n'));
  }

  private static String jsonTotals(Map<?, Long> totals) {
    StringJoiner json = new StringJoiner(",", "{", "}");
    totals.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
        .forEach(
            entry ->
                json.add(
                    jsonString(entry.getKey()) + ":" + jsonString(formatMinor(entry.getValue()))));
    return json.toString();
  }

  private static String csvField(Object value) {
    String text = String.valueOf(value);
    if (text.indexOf(',') < 0
        && text.indexOf('"') < 0
        && text.indexOf('\r') < 0
        && text.indexOf('\n') < 0) {
      return text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }

  private static String jsonString(Object value) {
    String text = String.valueOf(value);
    StringBuilder escaped = new StringBuilder(text.length() + 2).append('"');
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < JSON_CONTROL_CHARACTER_LIMIT) {
            escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.append('"').toString();
  }
}
