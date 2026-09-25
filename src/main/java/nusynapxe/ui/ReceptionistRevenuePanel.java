package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import nusynapxe.ClinicClock;
import nusynapxe.domain.Account;
import nusynapxe.domain.PaymentMethod;
import nusynapxe.domain.Receipt;
import nusynapxe.domain.RevenueSummary;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ValidationException;

/** Owns the Receptionist revenue filters, report actions, and export controls. */
final class ReceptionistRevenuePanel {
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String ALL_METHODS = "All methods";
  private static final String PATIENT_NAME_ID = "Name, NRIC/FIN, phone, or email";

  private final ReceptionistDataLoader dataLoader;
  private final Label feedback;
  private final DatePicker fromDate;
  private final DatePicker toDate;
  private final TextField patient;
  private final SearchSuggestionField<Account> doctor;
  private final ComboBox<String> method;
  private final Label summary;
  private final TableView<Receipt> rows;
  private final TextField legacyDate;
  private final Label legacyRevenue;
  private final VBox root;
  private final Button exportCsv;
  private final Button exportJson;
  private final ReportExportState exportState = new ReportExportState();

  ReceptionistRevenuePanel(ReceptionistDataLoader dataLoader, Label feedback, Clock clock) {
    this.dataLoader = dataLoader;
    this.feedback = feedback;
    Clock clinicClock = ClinicClock.withClinicZone(clock);
    fromDate = datePicker("reception-revenue-report-from", clinicClock);
    toDate = datePicker("reception-revenue-report-to", clinicClock);
    patient = field("reception-revenue-report-patient", PATIENT_NAME_ID);
    doctor = doctorSelector("reception-revenue-report-doctor", ALL_DOCTORS);
    method = UiComponents.compactSelector();
    method.getItems().add(ALL_METHODS);
    for (PaymentMethod value : PaymentMethod.values()) {
      method.getItems().add(displayStatus(value));
    }
    method.setId("reception-revenue-report-method");
    method.getSelectionModel().select(ALL_METHODS);
    Button report = button("Generate report", "reception-revenue-report");
    summary = new Label();
    summary.setId("reception-revenue-report-summary");
    rows = ReceptionistWorkspace.receiptTable("reception-revenue-report-list");
    exportCsv = button("Export CSV", "reception-revenue-export-csv");
    exportJson = button("Export JSON", "reception-revenue-export-json");
    setExportEnabled(false);
    legacyDate = field("reception-revenue-date", "yyyy-MM-dd");
    Button legacySubmit = button("Show revenue", "reception-revenue-submit");
    legacyRevenue = new Label();
    legacyRevenue.setId("reception-revenue");

    report.setOnAction(event -> generateReport());
    legacySubmit.setOnAction(event -> showLegacyRevenue());
    exportCsv.setOnAction(event -> export(false));
    exportJson.setOnAction(event -> export(true));

    FlowPane filters = new FlowPane();
    filters.setId("reception-revenue-filter-grid");
    filters.getStyleClass().addAll("uniform-filter-grid", "single-line-filter-grid");
    filters.setHgap(12);
    filters.setVgap(10);
    filters.setRowValignment(VPos.BOTTOM);
    filters
        .getChildren()
        .addAll(
            UiComponents.fieldGroup("From", fromDate),
            UiComponents.fieldGroup("To", toDate),
            UiComponents.fieldGroup("Patient", patient),
            UiComponents.fieldGroup("Doctor", doctor),
            UiComponents.fieldGroup("Payment method", method),
            report);
    alignFilterAction(report);
    VBox reportCard =
        UiComponents.card(
            "reception-revenue-card",
            UiComponents.sectionHeading("Revenue results"),
            filters,
            new HBox(8, exportCsv, exportJson),
            summary,
            rows);
    VBox legacy = new VBox(legacyDate, legacySubmit, legacyRevenue);
    legacy.setManaged(false);
    legacy.setVisible(false);
    root =
        new VBox(
            12,
            UiComponents.pageTitle("Revenue Reports"),
            UiComponents.supportingText(
                "Filter successful payments and export the resulting report."),
            reportCard,
            legacy);
  }

  VBox view() {
    return root;
  }

  void refreshDoctors() {
    dataLoader.refreshDoctors(doctor, feedback, false);
    doctor.clearSelection();
  }

  private void generateReport() {
    long generation = exportState.begin();
    setExportEnabled(false);
    rows.getItems().clear();
    summary.setText("Generating report…");
    try {
      dataLoader.revenueReport(
          fromDate.getValue(),
          toDate.getValue(),
          patient.getText(),
          doctor.getValue() == null ? null : doctor.getValue().id(),
          selectedPaymentMethod(method.getValue()),
          report -> {
            if (!exportState.complete(generation, report)) {
              return;
            }
            setExportEnabled(true);
            rows.setItems(FXCollections.observableArrayList(report.receipts()));
            summary.setText(ReceptionistRevenueView.formatSummary(report));
            UiComponents.showMessage(feedback, "Revenue report generated");
          },
          failure -> {
            if (!exportState.isCurrent(generation)) {
              return;
            }
            summary.setText("Report generation failed");
            showTaskError("Revenue report is temporarily unavailable", failure);
          });
    } catch (ValidationException exception) {
      if (exportState.isCurrent(generation)) {
        summary.setText("Report generation failed");
        UiComponents.showError(feedback, exception.getMessage());
      }
    } catch (ArithmeticException exception) {
      if (exportState.isCurrent(generation)) {
        summary.setText("Report generation failed");
        UiComponents.showError(feedback, "Revenue total exceeds the supported range");
      }
    }
  }

  private void showLegacyRevenue() {
    try {
      dataLoader.dailyRevenue(
          LocalDate.parse(legacyDate.getText()),
          this::displayLegacyRevenue,
          failure -> showTaskError("Revenue is temporarily unavailable", failure));
    } catch (java.time.format.DateTimeParseException exception) {
      UiComponents.showError(feedback, "Revenue is temporarily unavailable");
    }
  }

  private void displayLegacyRevenue(RevenueSummary value) {
    legacyRevenue.setText(
        value.transactionCount()
            + " successful payment(s), total "
            + ReportExporter.formatMinor(value.totalMinor()));
  }

  private void export(boolean json) {
    exportState
        .current()
        .ifPresent(
            report -> {
              javafx.stage.Window owner =
                  rows.getScene() == null ? null : rows.getScene().getWindow();
              ReportExporter.export(report, owner, json, feedback);
            });
  }

  private void setExportEnabled(boolean enabled) {
    exportCsv.setDisable(!enabled);
    exportJson.setDisable(!enabled);
  }

  private void showTaskError(String fallback, Throwable failure) {
    if (failure instanceof ValidationException || failure instanceof AuthorizationException) {
      UiComponents.showError(feedback, failure.getMessage());
    } else {
      UiComponents.showError(feedback, fallback);
    }
  }

  private static DatePicker datePicker(String id, Clock clock) {
    DatePicker picker = UiComponents.compactDatePicker(LocalDate.now(clock));
    picker.setId(id);
    return picker;
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static Button button(String text, String id) {
    Button button = new Button(text);
    button.setId(id);
    return button;
  }

  private static SearchSuggestionField<Account> doctorSelector(String id, String prompt) {
    return new SearchSuggestionField<>(
        id,
        prompt,
        ReceptionistRevenuePanel::doctorLabel,
        doctor -> doctor.displayName() + " " + doctor.username());
  }

  private static String doctorLabel(Account account) {
    return account == null ? "" : account.displayName() + " (" + account.username() + ")";
  }

  private static String displayStatus(Object value) {
    return value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
  }

  private static PaymentMethod selectedPaymentMethod(String value) {
    if (value == null || ALL_METHODS.equals(value)) {
      return null;
    }
    return PaymentMethod.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_'));
  }

  private static void alignFilterAction(Button button) {
    button.setMinHeight(38);
    button.setPrefHeight(38);
  }
}
