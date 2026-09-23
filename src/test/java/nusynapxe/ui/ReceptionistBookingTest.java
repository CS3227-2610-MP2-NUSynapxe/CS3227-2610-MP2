package nusynapxe.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.domain.Sex;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.service.CalendarService;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

final class ReceptionistBookingTest extends ReceptionistViewTestSupport {
  @Test
  void receptionistBooksChecksInChecksOutAndViewsRevenue() throws SQLException {
    loginAsReceptionist();
    verifyThat("#receptionist-workspace", isVisible());
    assertEquals(6, workspaceTabs().getTabs().size());
    verifyThat("#reception-navigation-title", hasText("Navigation"));
    verifyThat("#reception-nav-directory", hasText("Directory"));
    verifyThat("#reception-nav-appointments", hasText("Appointments"));
    verifyThat("#reception-nav-calendar", hasText("Calendar"));
    fire("#reception-nav-calendar");
    verifyThat("#reception-calendar-page", isVisible());
    assertTrue(lookup("#reception-calendar-doctor").tryQuery().isPresent());
    fire("#reception-nav-directory");
    assertTrue(lookup("#reception-schedule-date").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-doctor").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-status").tryQuery().isPresent());
    assertTrue(lookup("#reception-schedule-summary").tryQuery().isPresent());
    assertFalse(lookup("#reception-refresh").tryQuery().isPresent());
    assertFalse(lookup("#reception-register-id").tryQuery().isPresent());

    fire("#reception-patient-open-register");
    verifyThat("#reception-patient-register-view", isVisible());
    selectCombo("#reception-register-identity-type", IdentityType.NRIC);
    selectCombo("#reception-register-sex", Sex.FEMALE);
    setText("#reception-register-first-name", "Pat");
    setText("#reception-register-last-name", "Lee");
    setText("#reception-register-identity-number", "S1234567D");
    setDate("#reception-register-date-of-birth", LocalDate.of(1990, 1, 1));
    setText("#reception-register-phone-number", "5550100");
    setText("#reception-register-email", "pat@example.test");
    setText("#reception-register-address", "Address");
    fire("#reception-patient-register");
    verifyThat("#reception-feedback", hasText("Patient registered"));
    selectWorkspaceTab(1);
    verifyThat("#reception-book", isVisible());

    LocalDateTime start =
        LocalDateTime.now(CalendarService.CLINIC_ZONE)
            .minusHours(1)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    LocalDateTime end = start.plusMinutes(30);
    setDatePicker("#reception-appointment-date", start.toLocalDate());
    selectCombo("#reception-start-hour", String.format("%02d", start.getHour()));
    selectCombo("#reception-start-minute", String.format("%02d", start.getMinute()));
    selectCombo("#reception-end-hour", String.format("%02d", end.getHour()));
    selectCombo("#reception-end-minute", String.format("%02d", end.getMinute()));
    fire("#reception-book");
    verifyThat("#reception-feedback", hasText("Appointment booked and awaiting Doctor acceptance"));
    assertTrue(textLabel("#reception-schedule-summary").contains("Pending: 1"));
    selectCombo("#reception-schedule-status", "Pending");
    assertEquals(1, appointmentList().getItems().size());
    selectCombo("#reception-schedule-status", "All statuses");

    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    List<Appointment> bookedAppointments =
        services.appointmentService().allAppointments(receptionistSession);
    assertThat(bookedAppointments, hasSize(1));
    Appointment appointment = bookedAppointments.get(0);
    services.appointmentService().accept(doctorSession, appointment.id());

    selectWorkspaceTab(3);
    assertTrue(lookup("#reception-check-in-queue-list").tryQuery().isPresent());
    assertTrue(lookup("#reception-check-in-queue-summary").tryQuery().isPresent());
    selectFirstAppointment("#reception-check-in-queue-list");
    services.appointmentService().checkIn(receptionistSession, appointment.id());
    assertEquals(
        AppointmentStatus.CHECKED_IN, services.appointmentService().get(appointment.id()).status());
    services.appointmentService().complete(doctorSession, appointment.id());

    selectWorkspaceTab(4);
    selectFirstAppointment("#reception-checkout-appointment-list");
    setText("#reception-charge", "45.00");
    fire("#reception-checkout");
    verifyThat("#reception-feedback", hasText("Checkout completed"));

    selectWorkspaceTab(5);
    setText("#reception-revenue-date", LocalDate.now().toString());
    fire("#reception-revenue-submit");
    verifyThat("#reception-revenue", hasText("1 successful payment(s), total 45.00"));

    fire("#logout-button");
    verifyThat("#login-view", isVisible());
  }

  @Test
  void doctorAndPatientSearchFieldsShowKeyboardSelectableSuggestions() {
    loginAsReceptionist();
    selectWorkspaceTab(1);
    String[] selectors = {
      "#reception-start-hour",
      "#reception-start-minute",
      "#reception-end-hour",
      "#reception-end-minute",
      "#reception-schedule-status",
      "#reception-check-in-queue-status",
      "#reception-revenue-report-method"
    };
    for (String selector : selectors) {
      assertTrue(
          combo(selector).getStyleClass().contains("compact-selector"),
          "Expected compact selector style for " + selector);
    }

    TextField doctorSearch = textField("#reception-doctor");
    interact(() -> doctorSearch.clear());
    clickOn(doctorSearch);
    interact(() -> doctorSearch.setText(doctor.displayName()));
    SearchSuggestionField<?> doctorSuggestions =
        (SearchSuggestionField<?>) doctorSearch.getParent();
    assertFalse(doctorSuggestions.suggestionList().getItems().isEmpty());
    interact(
        () ->
            doctorSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    interact(
        () ->
            doctorSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.ENTER,
                    false,
                    false,
                    false,
                    false)));
    interact(() -> combo("#reception-start-hour").requestFocus());
    WaitForAsyncUtils.waitForFxEvents();
    assertTrue(
        doctorSearch.getText().contains(doctor.displayName()),
        () -> "Doctor search text after keyboard selection: " + doctorSearch.getText());
    assertTrue(lookup("#reception-appointment-patient").query() instanceof TextField);
    assertEquals(
        GridPane.getRowIndex(lookup("#reception-appointment-date").query().getParent()),
        GridPane.getRowIndex(lookup("#reception-start").query().getParent()));
    assertEquals(
        GridPane.getRowIndex(lookup("#reception-appointment-date").query().getParent()),
        GridPane.getRowIndex(lookup("#reception-end").query().getParent()));
    layoutWorkspace();
    Node appointmentDate = lookup("#reception-appointment-date").query();
    Node appointmentStart = lookup("#reception-start").query();
    Node appointmentEnd = lookup("#reception-end").query();
    assertCompactDatePickerBounds((DatePicker) appointmentDate);
    assertEquals(
        appointmentStart.getBoundsInParent().getWidth(),
        appointmentEnd.getBoundsInParent().getWidth(),
        2.0);
  }

  @Test
  void everyReceptionDatePickerUsesTheSharedMinimalStyle() {
    loginAsReceptionist();
    String[] selectors = {
      "#reception-schedule-date",
      "#reception-appointment-date",
      "#reception-calendar-from",
      "#reception-calendar-to",
      "#reception-check-in-queue-date",
      "#reception-checkout-date",
      "#reception-receipt-date",
      "#reception-revenue-report-from",
      "#reception-revenue-report-to"
    };
    for (String selector : selectors) {
      assertTrue(
          lookup(selector)
              .queryAs(DatePicker.class)
              .getStyleClass()
              .contains("compact-date-picker"),
          "Expected minimal date-picker style for " + selector);
    }
  }

  @Test
  void everyReceptionDatePickerUsesTheSharedCompactWidth() {
    loginAsReceptionist();
    layoutWorkspace();
    String[] selectors = {
      "#reception-schedule-date",
      "#reception-appointment-date",
      "#reception-calendar-from",
      "#reception-calendar-to",
      "#reception-check-in-queue-date",
      "#reception-checkout-date",
      "#reception-receipt-date",
      "#reception-revenue-report-from",
      "#reception-revenue-report-to"
    };
    for (String selector : selectors) {
      DatePicker picker = lookup(selector).queryAs(DatePicker.class);
      picker.applyCss();
      picker.layout();
      assertEquals(150.0, picker.getPrefWidth(), 0.1, "Unexpected width for " + selector);
      assertTrue(picker.getMaxWidth() <= 170.0, "Unexpected max width for " + selector);
    }
  }

  @Test
  void checkoutAndReceiptFiltersShowSelectedDatesAtCompactWidths() {
    loginAsReceptionist();
    selectWorkspaceTab(4);
    DatePicker checkoutDate = lookup("#reception-checkout-date").queryAs(DatePicker.class);
    interact(() -> checkoutDate.setValue(LocalDate.of(2026, Month.SEPTEMBER, 6)));
    WaitForAsyncUtils.waitForFxEvents();
    layoutWorkspace();
    assertFalse(checkoutDate.getEditor().getText().isBlank());
    assertCompactDatePickerBounds(checkoutDate);
    assertTrue(
        lookup("#reception-checkout-ready-tab").queryAs(VBox.class).getPadding().getTop() >= 20);
    interact(
        () ->
            lookup("#reception-checkout-tabs")
                .queryAs(TabPane.class)
                .getSelectionModel()
                .select(1));
    DatePicker receiptDate = lookup("#reception-receipt-date").queryAs(DatePicker.class);
    interact(() -> receiptDate.setValue(LocalDate.of(2026, Month.SEPTEMBER, 6)));
    WaitForAsyncUtils.waitForFxEvents();
    layoutWorkspace();
    assertFalse(receiptDate.getEditor().getText().isBlank());
    assertCompactDatePickerBounds(receiptDate);
    assertTrue(lookup("#reception-receipts-tab").queryAs(VBox.class).getPadding().getTop() >= 20);
  }

  @Test
  void patientBookingSearchSupportsMouseSelection() throws SQLException {
    new PatientRepository(database)
        .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    loginAsReceptionist();
    selectWorkspaceTab(1);
    TextField patientSearch = textField("#reception-appointment-patient");
    interact(patientSearch::clear);
    interact(patientSearch::requestFocus);
    interact(() -> patientSearch.setText("Pat Lee"));
    WaitForAsyncUtils.waitForFxEvents();
    SearchSuggestionField<?> patientSuggestions =
        (SearchSuggestionField<?>) patientSearch.getParent();
    interact(
        () ->
            patientSearch.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    interact(
        () -> {
          ListView<?> suggestions = patientSuggestions.suggestionList();
          suggestions.getSelectionModel().selectFirst();
          suggestions.fireEvent(ReceptionistViewTestSupport.mouseEvent(MouseEvent.MOUSE_PRESSED));
          suggestions.fireEvent(ReceptionistViewTestSupport.mouseEvent(MouseEvent.MOUSE_RELEASED));
        });
    assertTrue(
        patientSearch.getText().contains("Pat Lee"), "Mouse selection did not update the editor");
  }
}
