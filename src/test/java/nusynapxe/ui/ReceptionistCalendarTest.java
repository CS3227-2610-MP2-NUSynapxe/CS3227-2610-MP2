package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import java.sql.SQLException;
import java.time.LocalDate;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.domain.Role;
import nusynapxe.domain.Session;
import nusynapxe.persistence.PatientRepository;
import nusynapxe.service.CalendarService;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

final class ReceptionistCalendarTest extends ReceptionistViewTestSupport {
  @Test
  void calendarSlotOpensBookingPopupAndRefreshesAfterSave() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    LocalDate selectedDate = LocalDate.now(java.time.ZoneId.of("Asia/Singapore"));
    var timeOff =
        services
            .appointmentService()
            .blockTimeOff(
                new Session(doctor.id(), doctor.username(), Role.DOCTOR),
                selectedDate.atTime(15, 0),
                selectedDate.atTime(16, 0));
    loginAsReceptionist();
    fire("#reception-nav-calendar");
    DatePicker from = lookup("#reception-calendar-from").queryAs(DatePicker.class);
    DatePicker to = lookup("#reception-calendar-to").queryAs(DatePicker.class);
    TextField calendarDoctor = textField("#reception-calendar-doctor");
    verifyThat("#reception-calendar-doctor-field", isVisible());
    clickOn(calendarDoctor);
    SearchSuggestionField<?> calendarDoctorSuggestions =
        (SearchSuggestionField<?>) calendarDoctor.getParent();
    interact(
        () ->
            calendarDoctor.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.DOWN,
                    false,
                    false,
                    false,
                    false)));
    assertFalse(calendarDoctorSuggestions.suggestionList().getItems().isEmpty());
    interact(
        () ->
            calendarDoctor.fireEvent(
                new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    javafx.scene.input.KeyCode.ENTER,
                    false,
                    false,
                    false,
                    false)));
    assertFalse(from.isShowWeekNumbers());
    assertFalse(to.isShowWeekNumbers());
    assertTrue(lookup("#reception-calendar-week-picker").tryQuery().isEmpty());
    interact(
        () -> {
          from.setValue(selectedDate);
          to.setValue(selectedDate.plusDays(2));
          to.getOnAction().handle(new javafx.event.ActionEvent());
        });
    WaitForAsyncUtils.waitForFxEvents();
    assertTrue(lookup("#doctor-calendar-day-column-" + selectedDate).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(1)).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(2)).tryQuery().isPresent());
    assertTrue(
        lookup("#doctor-calendar-day-column-" + selectedDate.plusDays(3)).tryQuery().isEmpty());
    var timeOffNode =
        lookup("#doctor-calendar-time-off-" + timeOff.id() + "-" + selectedDate).query();
    assertFalse(timeOffNode.isFocusTraversable());
    interact(() -> timeOffNode.getOnMouseClicked().handle(primaryClick()));
    assertTrue(lookup("#doctor-calendar-time-off-details").tryQuery().isEmpty());
    interact(
        () ->
            lookup("#doctor-calendar-events-" + selectedDate)
                .queryAs(Pane.class)
                .getOnMouseClicked()
                .handle(
                    new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        20,
                        20 * 100 + 4,
                        20,
                        20 * 100 + 4,
                        MouseButton.PRIMARY,
                        1,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null)));
    waitForNode("#reception-calendar-appointment-dialog-content");
    verifyThat("#reception-calendar-appointment-dialog-scroll", isVisible());
    Stage dialog =
        (Stage)
            lookup("#reception-calendar-appointment-dialog-submit")
                .queryAs(Button.class)
                .getScene()
                .getWindow();
    assertEquals("Book appointment", dialog.getTitle());
    fire("#reception-calendar-appointment-dialog-submit");
    Appointment created =
        services.appointmentService().allAppointments(receptionistSession()).stream()
            .filter(appointment -> appointment.patientId() == patient.id())
            .findFirst()
            .orElseThrow();
    assertEquals(AppointmentStatus.PENDING, created.status());
    assertTrue(
        lookup("#doctor-calendar-appointment-" + created.id() + "-" + selectedDate)
            .tryQuery()
            .isPresent());
  }

  @Test
  void receptionistScheduleArrowsMoveByWholeWeeks() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    LocalDate targetDate = LocalDate.now(CalendarService.CLINIC_ZONE).plusDays(5);
    services
        .appointmentService()
        .book(
            receptionistSession(),
            patient.id(),
            doctor.id(),
            targetDate.atTime(9, 0),
            targetDate.atTime(9, 30));
    loginAsReceptionist();
    fire("#reception-nav-calendar");
    selectCombo("#reception-calendar-view-mode", "Schedule");
    waitForNode("#reception-calendar-schedule-list");
    waitForNode("#doctor-calendar-schedule-date-" + targetDate);
    fire("#reception-calendar-next");
    WaitForAsyncUtils.waitForFxEvents();
    assertTrue(
        lookup("#doctor-calendar-schedule-date-" + targetDate).tryQuery().isEmpty(),
        "Receptionist Schedule should advance by seven days");
  }

  @Test
  void receptionistCanRescheduleADeclinedAppointmentFromTheDashboard() throws SQLException {
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Pat", "Lee", "1900-01-01", "555-0100", "", ""));
    Session receptionistSession =
        new Session(receptionist.id(), receptionist.username(), Role.RECEPTIONIST);
    Session doctorSession = new Session(doctor.id(), doctor.username(), Role.DOCTOR);
    LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Singapore")).plusDays(1);
    Appointment appointment =
        services
            .appointmentService()
            .book(
                receptionistSession,
                patient.id(),
                doctor.id(),
                date.atTime(9, 0),
                date.atTime(9, 30));
    services.appointmentService().decline(doctorSession, appointment.id());
    loginAsReceptionist();
    selectWorkspaceTab(1);
    selectCombo("#reception-schedule-status", "Declined");
    assertEquals(1, appointmentList().getItems().size());
    selectFirstAppointment("#reception-appointment-list");
    fire("#reception-reschedule");
    waitForNode("#reception-reschedule-dialog-content");
    LocalDate replacementDate = date.plusDays(1);
    interact(
        () ->
            lookup("#reception-reschedule-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(replacementDate));
    selectCombo("#reception-reschedule-dialog-start-hour", "10");
    selectCombo("#reception-reschedule-dialog-start-minute", "00");
    selectCombo("#reception-reschedule-dialog-end-hour", "10");
    selectCombo("#reception-reschedule-dialog-end-minute", "30");
    fire("#reception-reschedule-dialog-submit");
    assertEquals(
        AppointmentStatus.PENDING, services.appointmentService().get(appointment.id()).status());
    selectCombo("#reception-schedule-status", "All statuses");
    assertEquals(
        AppointmentStatus.PENDING, appointmentList().getItems().get(0).appointment().status());
  }
}
