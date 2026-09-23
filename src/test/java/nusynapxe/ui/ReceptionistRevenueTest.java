package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javafx.scene.control.TableView;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueReport;
import org.junit.jupiter.api.Test;

final class ReceptionistRevenueTest extends ReceptionistViewTestSupport {
  @Test
  void revenueReportSupportsFiltersAndEmptyState() {
    loginAsReceptionist();
    selectWorkspaceTab(5);
    assertTrue(lookup("#reception-revenue-report-patient").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-doctor").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-report-method").tryQuery().isPresent());
    assertTrue(combo("#reception-revenue-report-method").getItems().contains("All methods"));
    assertEquals("All methods", combo("#reception-revenue-report-method").getValue());
    assertTrue(lookup("#reception-revenue-export-csv").tryQuery().isPresent());
    assertTrue(lookup("#reception-revenue-export-json").tryQuery().isPresent());
    setDatePicker("#reception-revenue-report-from", LocalDate.of(2030, 1, 1));
    setDatePicker("#reception-revenue-report-to", LocalDate.of(2030, 1, 1));
    setText("#reception-revenue-report-patient", "does-not-exist");
    fire("#reception-revenue-report");
    assertTrue(textLabel("#reception-revenue-report-summary").startsWith("Successful payments: 0"));
    assertTrue(
        lookup("#reception-revenue-report-list").queryAs(TableView.class).getItems().isEmpty());
    assertTrue(
        lookup("#reception-revenue-report-list-empty")
            .query()
            .getStyleClass()
            .contains("table-empty-row"));
  }

  @Test
  void revenueReportExportsIncludeReceiptDetails() {
    Receipt receipt =
        new Receipt(
            1,
            2,
            3,
            4,
            "Pat Lee",
            "Dr. Ada",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            7,
            LocalDateTime.of(2026, 9, 1, 12, 30));
    RevenueReport report = new RevenueReport(List.of(receipt));
    String csv = ReceptionistView.reportCsv(report);
    assertTrue(csv.startsWith("receipt,dateTime,patientId,patientName,doctor,amount,method"));
    assertTrue(csv.contains("7,2026-09-01T12:30,4,Pat Lee,Dr. Ada,45.00,CARD"));
    assertTrue(csv.contains("summary,1,1,45.00"));
    assertTrue(csv.contains("paymentMethod,CARD,45.00"));
    assertTrue(csv.contains("doctor,Dr. Ada,45.00"));
    String json = ReceptionistView.reportJson(report);
    assertTrue(json.contains("\"successfulPaymentCount\":1"));
    assertTrue(json.contains("\"receiptCount\":1"));
    assertTrue(json.contains("\"paymentMethods\":{\"CARD\":\"45.00\"}"));
    assertTrue(json.contains("\"doctors\":{\"Dr. Ada\":\"45.00\"}"));
    assertTrue(json.contains("\"patientName\":\"Pat Lee\""));
    assertTrue(json.contains("\"method\":\"CARD\""));
  }

  @Test
  void revenueReportExportsEscapeDynamicText() {
    Receipt receipt =
        new Receipt(
            1,
            2,
            3,
            4,
            "Pat, \"Lee\"\nNorth",
            "Dr. \"Ada\"\\Clinic\tEast\u0001",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            7,
            LocalDateTime.of(2026, 9, 1, 12, 30));
    RevenueReport report = new RevenueReport(List.of(receipt));
    String csv = ReceptionistView.reportCsv(report);
    assertTrue(csv.contains("\"Pat, \"\"Lee\"\"\nNorth\""));
    assertTrue(csv.contains("\"Dr. \"\"Ada\"\"\\Clinic\tEast\u0001\""));
    assertTrue(csv.contains("doctor,\"Dr. \"\"Ada\"\"\\Clinic\tEast\u0001\",45.00"));
    String json = ReceptionistView.reportJson(report);
    assertTrue(json.contains("\"patientName\":\"Pat, \\\"Lee\\\"\\nNorth\""));
    assertTrue(json.contains("\"doctor\":\"Dr. \\\"Ada\\\"\\\\Clinic\\tEast\\u0001\""));
    assertTrue(
        json.contains("\"doctors\":{\"Dr. \\\"Ada\\\"\\\\Clinic\\tEast\\u0001\":\"45.00\"}"));
  }

  @Test
  void revenueReportEscapesAllJsonShortControlCharactersAndEmptyTotals() {
    Receipt receipt =
        new Receipt(
            1,
            2,
            3,
            4,
            "Plain patient",
            "Back\bForm\fReturn\r",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            7,
            LocalDateTime.of(2026, 9, 1, 12, 30));
    String json = ReceptionistView.reportJson(new RevenueReport(List.of(receipt)));

    assertTrue(json.contains("Back\\bForm\\fReturn\\r"));
    Receipt quoteOnly =
        new Receipt(
            2,
            2,
            3,
            4,
            "Quote\"Only",
            "Plain",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            8,
            LocalDateTime.of(2026, 9, 1, 12, 31));
    Receipt newlineOnly =
        new Receipt(
            3,
            2,
            3,
            4,
            "Line\nBreak",
            "Plain",
            4500,
            PaymentMethod.CARD,
            LocalDate.of(2026, 9, 1),
            9,
            LocalDateTime.of(2026, 9, 1, 12, 32));
    String csv =
        ReceptionistView.reportCsv(new RevenueReport(List.of(receipt, quoteOnly, newlineOnly)));
    assertTrue(csv.contains("\"Quote\"\"Only\""));
    assertTrue(csv.contains("\"Line\nBreak\""));
    String multipleReceiptJson =
        ReceptionistView.reportJson(new RevenueReport(List.of(receipt, quoteOnly)));
    assertTrue(multipleReceiptJson.contains("},{\"receiptNumber\""));
    assertTrue(
        ReceptionistView.reportCsv(new RevenueReport(List.of())).contains("summary,0,0,0.00"));
    assertTrue(
        ReceptionistView.reportJson(new RevenueReport(List.of())).contains("\"receipts\":[]"));
  }
}
