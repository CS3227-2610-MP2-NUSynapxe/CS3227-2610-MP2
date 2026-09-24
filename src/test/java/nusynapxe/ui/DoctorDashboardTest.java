package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.persistence.AppointmentRepository;
import org.junit.jupiter.api.Test;

/** Verifies doctor dashboard workflows and appointment-state presentation. */
final class DoctorDashboardTest extends DoctorViewTestSupport {
  @Test
  void dashboardUsesTheFixtureClockForItsInitialDay() {
    loginAsDoctor();

    assertEquals(
        appointmentDate, lookup("#doctor-dashboard-date").queryAs(DatePicker.class).getValue());
  }

  @Test
  void doctorOpensAssignedConsultationAddsPrescriptionAndCompletesVisit() {
    loginAsDoctor();

    verifyThat("#doctor-workspace", isVisible());
    verifyThat("#doctor-master-detail", isVisible());
    assertTrue(lookup("#doctor-detail-scroll").tryQuery().isPresent());
    assertTrue(lookup("#doctor-no-selection").tryQuery().isPresent());
    assertTrue(lookup("#doctor-accept").tryQuery().isEmpty());
    verifyThat("#doctor-dashboard-day-calendar", isVisible());
    assertTrue(lookup("#doctor-appointment-list").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-timeoff-submit").tryQuery().isEmpty());
    DatePicker dashboardDate = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
    assertEquals(appointmentDate, dashboardDate.getValue());
    assertTrue(dashboardDate.getStyleClass().contains("compact-date-picker"));
    assertEquals(
        "Dashboard", lookup("#doctor-schedule-card .page-title").queryAs(Label.class).getText());
    Label dashboardHelp = lookup("#doctor-schedule-card .supporting-text").queryAs(Label.class);
    assertTrue(dashboardHelp.isWrapText());
    assertEquals("Select an appointment to open its clinical context.", dashboardHelp.getText());
    assertEquals(
        12.0,
        javafx.scene.layout.BorderPane.getMargin(lookup("#doctor-dashboard-time-grid").query())
            .getTop(),
        0.1);
    selectDashboardAppointment(1);
    verifyThat("#doctor-selected-appointment", isVisible());

    setText("#doctor-diagnosis", "Seasonal allergies");
    setText("#doctor-consultation-notes", "Discussed symptoms and treatment options");
    setText("#doctor-follow-up", "Review in two weeks");
    fire("#doctor-consultation-save");
    verifyThat("#doctor-feedback", hasText("Consultation saved"));

    setText("#doctor-medication", "Cetirizine");
    setText("#doctor-dosage", "10 mg");
    setText("#doctor-frequency", "Once daily");
    setText("#doctor-duration", "14 days");
    setText("#doctor-instructions", "Take in the evening");
    fire("#doctor-prescription-submit");
    verifyThat("#doctor-feedback", hasText("Prescription added"));

    fire("#doctor-complete");
    verifyThat("#doctor-feedback", hasText("Appointment marked completed"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void doctorCanCheckInAnAcceptedAppointmentFromTheDashboard() throws SQLException {
    new AppointmentRepository(database).updateStatus(1, AppointmentStatus.ACCEPTED);
    loginAsDoctor();
    selectDashboardAppointment(1);
    assertFalse(lookup("#doctor-check-in").queryAs(Button.class).isDisable());
    fire("#doctor-check-in");
    verifyThat("#doctor-feedback", hasText("Patient checked in"));
    assertEquals(AppointmentStatus.CHECKED_IN, services.appointmentService().get(1).status());
  }

  @Test
  void failedClinicalLoadClearsPreviousPatientDetails() throws SQLException {
    loginAsDoctor();
    selectDashboardAppointment(1);
    setText("#doctor-diagnosis", "Previous diagnosis");
    setText("#doctor-consultation-notes", "Previous consultation");
    setText("#doctor-follow-up", "Previous follow-up");
    fire("#doctor-consultation-save");
    verifyThat("#doctor-feedback", hasText("Consultation saved"));

    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.path());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DROP TABLE prescriptions");
      statement.executeUpdate("DROP TABLE clinical_records");
    }

    fire("#doctor-dashboard-refresh");
    assertTrue(lookup("#doctor-diagnosis").queryAs(TextInputControl.class).getText().isEmpty());
    assertTrue(
        lookup("#doctor-consultation-notes").queryAs(TextInputControl.class).getText().isEmpty());
    assertTrue(lookup("#doctor-follow-up").queryAs(TextInputControl.class).getText().isEmpty());
    assertTrue(
        lookup("#doctor-prescription-list")
            .queryAs(javafx.scene.control.ListView.class)
            .getItems()
            .isEmpty());
    assertFalse(lookup("#doctor-consultation-save").queryAs(Button.class).isVisible());
    assertFalse(lookup("#doctor-prescription-submit").queryAs(Button.class).isVisible());
    assertFalse(lookup("#doctor-complete").queryAs(Button.class).isVisible());
  }

  @Test
  void dashboardNavigatesDaysAndRefreshesAVisibleSelection() {
    loginAsDoctor();
    DatePicker date = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
    assertEquals(appointmentDate, date.getValue());
    selectDashboardAppointment(1);
    fire("#doctor-dashboard-refresh");
    verifyThat("#doctor-selected-appointment", isVisible());

    fire("#doctor-dashboard-next");
    assertEquals(appointmentDate.plusDays(1), date.getValue());
    assertTrue(lookup("#doctor-no-selection").query().isVisible());
    interact(
        () -> {
          date.setValue(appointmentDate.plusDays(5));
          date.getOnAction().handle(new javafx.event.ActionEvent());
        });
    assertTrue(lookup("#doctor-calendar-empty").tryQuery().isPresent());
    fire("#doctor-dashboard-today");
    assertEquals(appointmentDate, date.getValue());
  }

  @Test
  void dashboardNavigationRestoresTheLastDateWhenThePickerIsCleared() {
    loginAsDoctor();
    DatePicker date = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);

    interact(() -> date.setValue(null));
    fire("#doctor-dashboard-next");

    assertEquals(appointmentDate, date.getValue());
    verifyThat("#doctor-feedback", isVisible());
  }

  @Test
  void dashboardDateLoadFailureDoesNotLeaveAStaleDateAndGrid() throws SQLException {
    loginAsDoctor();
    DatePicker date = lookup("#doctor-dashboard-date").queryAs(DatePicker.class);
    LocalDate loadedDate = date.getValue();

    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.path());
        var statement = connection.createStatement()) {
      statement.executeUpdate("DROP TABLE appointments");
    }

    fire("#doctor-dashboard-next");

    assertEquals(loadedDate, date.getValue());
    assertTrue(lookup("#doctor-dashboard-time-grid").tryQuery().isPresent());
    verifyThat("#doctor-feedback", isVisible());
  }

  @Test
  void compactDashboardKeepsProportionalAppointmentGeometryAndNoInlineActions() {
    loginAsDoctor();
    var thirtyMinutes =
        lookup("#doctor-calendar-appointment-1-" + appointmentDate).query().getBoundsInParent();
    var oneHour =
        lookup("#doctor-calendar-appointment-2-" + appointmentDate).query().getBoundsInParent();
    assertEquals(thirtyMinutes.getHeight() * 2 + 4, oneHour.getHeight(), 0.1);
    assertTrue(lookup("#doctor-calendar-accept-1").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-calendar-decline-1").tryQuery().isEmpty());
    assertTrue(
        lookup("#doctor-dashboard-time-grid")
            .query()
            .getStyleClass()
            .contains("calendar-time-grid-compact"));
  }

  @Test
  void pendingSelectionShowsPatientNameDetailsAndPendingActions() throws SQLException {
    Appointment pending = createAvailableAppointment("Pending", "Patient");

    loginAsDoctor();
    selectDashboardAppointment(pending.id());

    Label header = lookup("#doctor-selected-appointment").queryAs(Label.class);
    assertTrue(header.getText().contains("Pending Patient"));
    assertTrue(header.getText().contains("Pending"));
    assertFalse(header.getText().contains("P%06d".formatted(pending.patientId())));
    assertFalse(header.getText().contains("Selected appointment"));
    assertTrue(header.getStyleClass().contains("status-pending"));
    assertTrue(lookup("#doctor-patient-details-card").query().isVisible());
    assertTrue(lookup("#doctor-patient-details").tryQuery().isPresent());
    assertTrue(lookup("#doctor-patient-details .text-field").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-patient-details .text-area").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-accept").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-decline").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-reschedule").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-check-in").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-consultation-card").tryQuery().isEmpty());
  }

  @Test
  void acceptedSelectionShowsAcceptedActionsWithoutAccept() {
    loginAsDoctor();
    selectDashboardAppointment(2);

    Label header = lookup("#doctor-selected-appointment").queryAs(Label.class);
    assertTrue(header.getText().contains("Alex Tan"));
    assertTrue(header.getText().contains("Accepted"));
    assertTrue(header.getStyleClass().contains("status-accepted"));
    assertTrue(lookup("#doctor-decline").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-reschedule").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-check-in").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-accept").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-consultation-card").tryQuery().isEmpty());
  }

  @Test
  void checkedInSelectionShowsClinicalWorkflowWithoutLifecycleActions() {
    loginAsDoctor();
    selectDashboardAppointment(1);

    Label header = lookup("#doctor-selected-appointment").queryAs(Label.class);
    assertTrue(header.getText().contains("Pat Lee"));
    assertTrue(header.getText().contains("Checked In"));
    assertTrue(header.getStyleClass().contains("status-checked-in"));
    assertTrue(lookup("#doctor-patient-details-card").query().isVisible());
    assertTrue(lookup("#doctor-consultation-card").query().isVisible());
    assertTrue(lookup("#doctor-prescription-card").query().isVisible());
    assertTrue(lookup("#doctor-completion-card").query().isVisible());
    assertTrue(lookup("#doctor-consultation-save").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-prescription-submit").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-complete").queryAs(Button.class).isVisible());
    assertTrue(lookup("#doctor-accept").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-decline").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-reschedule").tryQuery().isEmpty());
    assertTrue(lookup("#doctor-check-in").tryQuery().isEmpty());
  }

  @Test
  void pendingRescheduleOpensTheCalendarAppointmentEditor() throws SQLException {
    Appointment pending = createAvailableAppointment("Reschedule", "Patient");

    loginAsDoctor();
    selectDashboardAppointment(pending.id());
    fire("#doctor-reschedule");
    waitForNode("#doctor-calendar-appointment-dialog-content");
    interact(
        () ->
            lookup("#doctor-calendar-appointment-dialog-content")
                .query()
                .getScene()
                .getWindow()
                .hide());
    assertEquals(
        AppointmentStatus.PENDING, services.appointmentService().get(pending.id()).status());
  }

  @Test
  void decliningPendingSelectionClearsDashboardDetails() throws SQLException {
    Appointment pending = createAvailableAppointment("Decline", "Patient");

    loginAsDoctor();
    selectDashboardAppointment(pending.id());
    fire("#doctor-decline");

    verifyThat("#doctor-feedback", hasText("Appointment declined"));
    assertTrue(lookup("#doctor-no-selection").query().isVisible());
    assertFalse(lookup("#doctor-selected-appointment").queryAs(Label.class).isManaged());
    assertEquals(
        AppointmentStatus.DECLINED, services.appointmentService().get(pending.id()).status());
  }
}
