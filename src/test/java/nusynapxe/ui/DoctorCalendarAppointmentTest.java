package nusynapxe.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import nusynapxe.domain.Appointment;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.Patient;
import nusynapxe.persistence.AppointmentRepository;
import nusynapxe.persistence.PatientRepository;
import org.junit.jupiter.api.Test;

/** Verifies calendar appointment interactions, dialogs, and layout containment. */
final class DoctorCalendarAppointmentTest extends DoctorCalendarViewTestSupport {
  @Test
  void doctorCanDeclineAndCreateAcceptedAppointmentFromCalendar() throws SQLException {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    assertTrue(lookup("#doctor-calendar-accept-1").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-decline-1").tryQuery().isPresent());
    assertTrue(
        lookup(".calendar-appointment-patient").queryAll().stream()
            .map(node -> ((Label) node).getText())
            .allMatch(name -> !name.startsWith("P")));

    fire("#doctor-calendar-decline-1");
    assertTrue(!lookup("#doctor-calendar-appointment-1-" + today()).tryQuery().isPresent());
    assertEquals(
        AppointmentStatus.DECLINED,
        new AppointmentRepository(database).findById(1).orElseThrow().status());

    fire("#doctor-calendar-add-appointment");
    waitForNode("#doctor-calendar-appointment-dialog-content");
    LocalDate targetDate = today().plusDays(2);
    interact(
        () ->
            lookup("#doctor-calendar-appointment-dialog-date")
                .queryAs(DatePicker.class)
                .setValue(targetDate));
    selectCombo("#doctor-calendar-appointment-dialog-start-hour", "10");
    selectCombo("#doctor-calendar-appointment-dialog-start-minute", "00");
    selectCombo("#doctor-calendar-appointment-dialog-end-hour", "10");
    selectCombo("#doctor-calendar-appointment-dialog-end-minute", "30");
    fire("#doctor-calendar-appointment-dialog-submit");

    Appointment created =
        new AppointmentRepository(database)
            .findByDoctor(doctorId).stream()
                .filter(appointment -> appointment.startsAt().toLocalDate().equals(targetDate))
                .findFirst()
                .orElseThrow();
    assertEquals(AppointmentStatus.ACCEPTED, created.status());
    assertTrue(
        lookup("#doctor-calendar-appointment-" + created.id() + "-" + targetDate)
            .tryQuery()
            .isPresent());
  }

  @Test
  void clickingAnEmptyCalendarSlotUsesTheSharedAppointmentForm() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    LocalDate emptyDate = today();
    interact(
        () ->
            lookup("#doctor-calendar-events-" + emptyDate)
                .queryAs(Pane.class)
                .getOnMouseClicked()
                .handle(
                    new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        20,
                        15 * 100 + 4,
                        20,
                        15 * 100 + 4,
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
    waitForNode("#doctor-calendar-appointment-dialog-content");
    assertEquals(
        emptyDate,
        lookup("#doctor-calendar-appointment-dialog-date").queryAs(DatePicker.class).getValue());
    assertTrue(
        lookup("#doctor-calendar-appointment-dialog-date")
            .queryAs(DatePicker.class)
            .getStyleClass()
            .contains("compact-date-picker"));
    assertEquals(
        "07:30",
        lookup("#doctor-calendar-appointment-dialog-start-hour").queryAs(ComboBox.class).getValue()
            + ":"
            + lookup("#doctor-calendar-appointment-dialog-start-minute")
                .queryAs(ComboBox.class)
                .getValue());
    interact(
        () ->
            ((Stage)
                    lookup("#doctor-calendar-appointment-dialog-cancel")
                        .queryAs(Button.class)
                        .getScene()
                        .getWindow())
                .close());
  }

  @Test
  void crossMidnightAppointmentUsesClippedTimesInItsLabels() throws SQLException {
    LocalDate startDate = today();
    LocalDate nextDate = startDate.plusDays(1);
    Patient patient =
        new PatientRepository(database)
            .create(new Patient(0, "Overnight", "Patient", "1900-01-01", "555-0199", "", ""));
    var crossMidnight =
        new AppointmentRepository(database)
            .create(
                patient.id(),
                doctorId,
                startDate.atTime(23, 30),
                nextDate.atTime(0, 30),
                AppointmentStatus.PENDING);

    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");
    Node clippedBlock =
        lookup("#doctor-calendar-appointment-" + crossMidnight.id() + "-" + nextDate).query();
    Label clippedTime = (Label) clippedBlock.lookup(".calendar-appointment-time");

    assertEquals("00:00 – 00:30", clippedTime.getText());
    assertTrue(clippedBlock.getAccessibleText().contains("00:00 to 00:30"));
  }

  @Test
  void selectingAnAppointmentOpensDetailsWithoutTreatingItAsAnInlineDecision() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    interact(
        () ->
            lookup("#doctor-calendar-appointment-2-" + today()).query().fireEvent(primaryClick()));
    waitForNode("#doctor-calendar-appointment-dialog-content");
    assertTrue(lookup("#doctor-calendar-appointment-dialog-status").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-submit").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-accept").tryQuery().isPresent());
    assertTrue(lookup("#doctor-calendar-appointment-dialog-decline").tryQuery().isPresent());
    interact(
        () ->
            ((Stage)
                    lookup("#doctor-calendar-appointment-dialog-cancel")
                        .queryAs(Button.class)
                        .getScene()
                        .getWindow())
                .hide());
  }

  @Test
  void calendarColumnsTouchAndAppointmentControlsStayInsideTheirDayColumn() {
    loginAsDoctor();
    fire("#doctor-nav-calendar");
    waitForNode("#doctor-calendar-page");

    Node timeHeader = lookup("#doctor-calendar-time-header").query();
    Node firstDayHeader = lookup(".calendar-day-header").queryAll().iterator().next();
    Node lastDayHeader =
        lookup(".calendar-day-header").queryAll().stream()
            .reduce((first, second) -> second)
            .orElseThrow();
    Bounds timeBounds = timeHeader.localToScene(timeHeader.getBoundsInLocal());
    Bounds firstDayBounds = firstDayHeader.localToScene(firstDayHeader.getBoundsInLocal());
    Bounds lastDayBounds = lastDayHeader.localToScene(lastDayHeader.getBoundsInLocal());
    assertEquals(timeBounds.getMaxX(), firstDayBounds.getMinX(), 0.1);
    Node timeAxis = lookup("#doctor-calendar-time-axis").query();
    Node firstDayColumn = lookup(".calendar-day-column").queryAll().iterator().next();
    Bounds timeAxisBounds = timeAxis.localToScene(timeAxis.getBoundsInLocal());
    Bounds firstDayColumnBounds = firstDayColumn.localToScene(firstDayColumn.getBoundsInLocal());
    assertEquals(timeBounds.getMaxX(), timeAxisBounds.getMaxX(), 0.1);
    assertEquals(timeAxisBounds.getMaxX(), firstDayColumnBounds.getMinX(), 0.1);

    Node appointment = lookup("#doctor-calendar-appointment-1-" + today()).query();
    Node oneHourAppointment = lookup("#doctor-calendar-appointment-2-" + today()).query();
    Node oneHourTimeOff = lookup("#doctor-calendar-time-off-" + timeOffId + "-" + today()).query();
    Node dayColumn = lookup("#doctor-calendar-day-column-" + today()).query();
    Node eventSurface = lookup("#doctor-calendar-events-" + today()).query();
    assertContained(appointment, dayColumn);
    Bounds appointmentBounds = appointment.localToScene(appointment.getBoundsInLocal());
    Bounds oneHourAppointmentBounds =
        oneHourAppointment.localToScene(oneHourAppointment.getBoundsInLocal());
    Bounds oneHourTimeOffBounds = oneHourTimeOff.localToScene(oneHourTimeOff.getBoundsInLocal());
    Bounds eventSurfaceBounds = eventSurface.localToScene(eventSurface.getBoundsInLocal());
    assertEquals(eventSurfaceBounds.getMinX(), appointmentBounds.getMinX(), 0.1);
    assertEquals(eventSurfaceBounds.getMaxX(), appointmentBounds.getMaxX(), 0.1);
    assertTrue(appointmentBounds.getHeight() > 32);
    Bounds thirtyMinuteSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-16").query());
    Bounds oneHourStartSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-18").query());
    Bounds oneHourEndSlot =
        sceneBounds(lookup("#doctor-calendar-period-" + today() + "-19").query());
    assertEquals(thirtyMinuteSlot.getHeight() - 4, appointmentBounds.getHeight(), 2.0);
    assertEquals(thirtyMinuteSlot.getHeight() * 2 - 4, oneHourAppointmentBounds.getHeight(), 2.0);
    assertEquals(thirtyMinuteSlot.getHeight() * 2 - 4, oneHourTimeOffBounds.getHeight(), 2.0);
    assertTrue(oneHourTimeOff.getStyleClass().contains("calendar-time-off-block"));
    assertTrue(oneHourTimeOff.getAccessibleText().startsWith("Blocked time"));
    assertEquals(oneHourStartSlot.getMinY() + 2, oneHourAppointmentBounds.getMinY(), 2.0);
    assertEquals(oneHourEndSlot.getMaxY() - 2, oneHourAppointmentBounds.getMaxY(), 2.0);
    assertFullyContained(lookup("#doctor-calendar-accept-1").query(), appointment);
    assertFullyContained(lookup("#doctor-calendar-decline-1").query(), appointment);
    assertContained(lookup("#doctor-calendar-accept-1").query(), dayColumn);
    assertContained(lookup("#doctor-calendar-decline-1").query(), dayColumn);

    ScrollPane scroll = lookup("#doctor-calendar-scroll").queryAs(ScrollPane.class);
    Node viewport = scroll.lookup(".viewport");
    Bounds viewportBounds = viewport.localToScene(viewport.getBoundsInLocal());
    if (scroll.getHmax() > 0) {
      interact(() -> scroll.setHvalue(1));
      org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
      lastDayBounds = lastDayHeader.localToScene(lastDayHeader.getBoundsInLocal());
    }
    assertEquals(viewportBounds.getMaxX(), lastDayBounds.getMaxX(), 0.1);
  }

  private static Bounds sceneBounds(Node node) {
    return node.localToScene(node.getBoundsInLocal());
  }
}
