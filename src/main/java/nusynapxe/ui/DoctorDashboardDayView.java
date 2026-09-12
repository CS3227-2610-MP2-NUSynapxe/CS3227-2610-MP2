package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Compact single-day Calendar used as the Doctor Dashboard master pane. */
final class DoctorDashboardDayView {
  private static final String TICKER_RUNNING_PROPERTY = "tickerRunning";
  private final ClinicServices services;
  private final Session session;
  private final Label feedback;
  private final Consumer<CalendarAppointment> onSelectionChanged;
  private final Clock clock;
  private final BorderPane root = new BorderPane();
  private final DatePicker date;
  private final Timeline ticker;
  private CalendarTimeGrid grid;
  private long selectedAppointmentId;
  private boolean shown;

  DoctorDashboardDayView(
      ClinicServices services,
      Session session,
      Label feedback,
      Consumer<CalendarAppointment> onSelectionChanged) {
    this(
        services, session, feedback, onSelectionChanged, Clock.system(CalendarService.CLINIC_ZONE));
  }

  DoctorDashboardDayView(
      ClinicServices services,
      Session session,
      Label feedback,
      Consumer<CalendarAppointment> onSelectionChanged,
      Clock clock) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.onSelectionChanged = Objects.requireNonNull(onSelectionChanged, "onSelectionChanged");
    this.clock = Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    date = new DatePicker(LocalDate.now(this.clock));
    date.setId("doctor-dashboard-date");
    date.setShowWeekNumbers(false);
    date.setAccessibleText("Dashboard date");
    root.setId("doctor-dashboard-day-calendar");
    root.getStyleClass().add("doctor-dashboard-day-calendar");
    root.getProperties().put(TICKER_RUNNING_PROPERTY, false);
    root.setTop(toolbar());
    ticker = new Timeline(new KeyFrame(Duration.minutes(1), event -> updateCurrentTime()));
    ticker.setCycleCount(Timeline.INDEFINITE);
    refresh();
  }

  Parent view() {
    return root;
  }

  LocalDate selectedDate() {
    return date.getValue();
  }

  void show() {
    shown = true;
    refresh();
    ticker.play();
    root.getProperties().put(TICKER_RUNNING_PROPERTY, true);
  }

  void hide() {
    shown = false;
    ticker.pause();
    root.getProperties().put(TICKER_RUNNING_PROPERTY, false);
  }

  void dispose() {
    shown = false;
    ticker.stop();
    root.getProperties().put(TICKER_RUNNING_PROPERTY, false);
  }

  void refresh() {
    LocalDate selectedDate = date.getValue();
    if (selectedDate == null) {
      UiComponents.showError(feedback, "Select a Dashboard date");
      return;
    }
    try {
      DoctorCalendarWeek data =
          services.calendarService().getRange(session, selectedDate, selectedDate);
      CalendarAppointment retained =
          data.appointments().stream()
              .filter(appointment -> appointment.appointmentId() == selectedAppointmentId)
              .findFirst()
              .orElse(null);
      if (selectedAppointmentId != 0 && retained == null) {
        selectedAppointmentId = 0;
        onSelectionChanged.accept(null);
      }
      grid =
          new CalendarTimeGrid(
              List.of(selectedDate),
              data,
              clock,
              new CalendarTimeGrid.InteractionHandlers(this::selectAppointment, null, null, null),
              CalendarTimeGrid.DisplayProfile.COMPACT);
      grid.setId("doctor-dashboard-time-grid");
      root.setCenter(grid);
      if (retained != null) {
        onSelectionChanged.accept(retained);
      }
      if (shown) {
        ticker.play();
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback,
          exception.getMessage() == null
              ? "Dashboard schedule is temporarily unavailable"
              : exception.getMessage());
    }
  }

  private HBox toolbar() {
    Button previous = UiComponents.secondaryButton("‹", "doctor-dashboard-previous");
    previous.setAccessibleText("Previous day");
    previous.setTooltip(new Tooltip("Previous day"));
    previous.setOnAction(event -> moveDate(-1));
    Button next = UiComponents.secondaryButton("›", "doctor-dashboard-next");
    next.setAccessibleText("Next day");
    next.setTooltip(new Tooltip("Next day"));
    next.setOnAction(event -> moveDate(1));
    Button today = UiComponents.secondaryButton("Today", "doctor-dashboard-today");
    today.setAccessibleText("Go to today");
    today.setOnAction(
        event -> {
          date.setValue(LocalDate.now(clock));
          refresh();
        });
    Button refresh = new Button("↻");
    refresh.setId("doctor-dashboard-refresh");
    refresh.setAccessibleText("Refresh Dashboard schedule");
    refresh.setTooltip(new Tooltip("Refresh Dashboard schedule"));
    refresh.getStyleClass().add("calendar-settings-button");
    refresh.setOnAction(event -> refresh());
    date.setOnAction(event -> dateChanged());
    HBox toolbar = new HBox(6, today, previous, next, date, refresh);
    toolbar.setId("doctor-dashboard-calendar-toolbar");
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("calendar-toolbar");
    return toolbar;
  }

  private void moveDate(long days) {
    date.setValue(date.getValue().plusDays(days));
    dateChanged();
  }

  private void dateChanged() {
    if (selectedAppointmentId != 0) {
      selectedAppointmentId = 0;
      onSelectionChanged.accept(null);
    }
    refresh();
  }

  private void selectAppointment(CalendarAppointment appointment) {
    selectedAppointmentId = appointment.appointmentId();
    onSelectionChanged.accept(appointment);
  }

  private void updateCurrentTime() {
    if (grid != null) {
      grid.updateCurrentTime(LocalDateTime.now(clock));
    }
  }
}
