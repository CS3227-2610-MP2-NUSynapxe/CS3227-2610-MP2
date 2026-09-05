package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarScheduleCalculations;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds and manages the Week and Schedule Calendar views for a Doctor. */
public final class DoctorCalendarView {
  private static final String WEEK_MODE = "Week";
  private static final String SCHEDULE_MODE = "Schedule";
  private static final DateTimeFormatter SCHEDULE_LABEL_FORMATTER =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
  private final ClinicServices services;
  private final Session session;
  private final Runnable onSettings;
  private final Label feedback;
  private final Clock clock;
  private final BorderPane root;
  private final Button rangeButton;
  private final ComboBox<String> viewMode;
  private final Timeline currentTimeTicker;
  private final CalendarWeekPicker weekPicker;
  private CalendarWeek week;
  private LocalDate scheduleAnchor;
  private CalendarTimeGrid grid;
  private CalendarScheduleList scheduleList;
  private boolean shown;

  /**
   * Creates a Calendar page using the Singapore clinic system clock.
   *
   * @param services application services used for Calendar operations
   * @param session authenticated Doctor session
   * @param onSettings callback used to open Calendar settings
   * @param feedback label used for user-facing operation messages
   * @throws NullPointerException if an argument is {@code null}
   */
  public DoctorCalendarView(
      ClinicServices services, Session session, Runnable onSettings, Label feedback) {
    this(services, session, onSettings, feedback, Clock.system(CalendarService.CLINIC_ZONE));
  }

  DoctorCalendarView(
      ClinicServices services, Session session, Runnable onSettings, Label feedback, Clock clock) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.onSettings = Objects.requireNonNull(onSettings, "onSettings");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.clock = Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    DoctorCalendarSettings settings = loadSettings();
    week = CalendarWeek.today(clock, settings.firstDayOfWeek());
    scheduleAnchor = CalendarScheduleCalculations.today(clock);
    rangeButton = new Button(week.label());
    viewMode = UiComponents.compactSelector();
    weekPicker = new CalendarWeekPicker(this::selectDate, clock);
    root = buildRoot();
    currentTimeTicker =
        new Timeline(new KeyFrame(Duration.minutes(1), event -> updateCurrentTime()));
    currentTimeTicker.setCycleCount(Timeline.INDEFINITE);
    refresh();
  }

  /**
   * Returns the Calendar page node.
   *
   * @return root node for the Calendar page
   */
  public Parent view() {
    return root;
  }

  /** Refreshes the active Calendar mode and its saved display settings. */
  @SuppressWarnings("PMD.NullAssignment")
  public void refresh() {
    try {
      DoctorCalendarSettings settings = services.calendarService().getSettings(session);
      if (isWeekMode()) {
        disposeScheduleList();
        week = CalendarWeek.containing(week.start(), settings.firstDayOfWeek());
        DoctorCalendarWeek data = services.calendarService().getWeek(session, week.start());
        grid =
            new CalendarTimeGrid(
                week,
                data,
                clock,
                new CalendarTimeGrid.InteractionHandlers(
                    this::openAppointment, this::changeDecision, this::openCreateAppointment));
        root.setCenter(grid);
      } else {
        grid = null;
        disposeScheduleList();
        scheduleList =
            new CalendarScheduleList(
                services, session, scheduleAnchor, clock, this::openAppointment);
        root.setCenter(scheduleList);
      }
      updateRangeLabel();
      if (shown) {
        currentTimeTicker.play();
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      feedback.setText(userMessage(exception, "Calendar is temporarily unavailable"));
    }
  }

  /** Marks the page visible and starts its current-time refresh. */
  public void show() {
    shown = true;
    refresh();
    currentTimeTicker.play();
  }

  /** Marks the page hidden and pauses its current-time refresh. */
  public void hide() {
    shown = false;
    currentTimeTicker.pause();
    weekPicker.hide();
    disposeScheduleList();
  }

  /** Stops all page-owned resources when the Doctor workspace is discarded. */
  public void dispose() {
    shown = false;
    currentTimeTicker.stop();
    weekPicker.hide();
    disposeScheduleList();
  }

  private BorderPane buildRoot() {
    Button today = UiComponents.secondaryButton("Today", "doctor-calendar-today");
    today.setAccessibleText("Go to today");
    today.setOnAction(event -> goToToday());
    Button previous = UiComponents.secondaryButton("‹", "doctor-calendar-previous");
    previous.setAccessibleText("Previous week");
    previous.setOnAction(event -> goToPrevious());
    Button next = UiComponents.secondaryButton("›", "doctor-calendar-next");
    next.setAccessibleText("Next week");
    next.setOnAction(event -> goToNext());
    Button addAppointment =
        UiComponents.primaryButton("Add appointment", "doctor-calendar-add-appointment");
    addAppointment.setAccessibleText("Add appointment to my schedule");
    addAppointment.setOnAction(event -> openCreateAppointment(null));
    rangeButton.setId("doctor-calendar-week-picker");
    rangeButton.setAccessibleText("Choose a week");
    rangeButton.getStyleClass().add("calendar-range-button");
    rangeButton.setOnAction(event -> weekPicker.show(rangeButton, pickerWeek()));
    viewMode.setId("doctor-calendar-view-mode");
    viewMode.setAccessibleText("Choose Calendar view");
    viewMode.getItems().addAll(WEEK_MODE, SCHEDULE_MODE);
    viewMode.setEditable(false);
    viewMode.setValue(WEEK_MODE);
    viewMode.setOnAction(event -> changeMode(viewMode.getValue()));
    Button settings = new Button("⚙");
    settings.setId("doctor-calendar-settings");
    settings.setAccessibleText("Open Calendar settings");
    settings.setTooltip(new javafx.scene.control.Tooltip("Calendar settings"));
    settings.getStyleClass().add("calendar-settings-button");
    settings.setOnAction(
        event -> {
          hide();
          onSettings.run();
        });
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox toolbar =
        new HBox(8, today, previous, next, rangeButton, viewMode, addAppointment, spacer, settings);
    toolbar.setId("doctor-calendar-toolbar");
    toolbar.getStyleClass().add("calendar-toolbar");
    toolbar.setAlignment(Pos.CENTER_LEFT);
    Label title = UiComponents.pageTitle("Calendar");
    Label supporting =
        UiComponents.supportingText(
            "Working hours and breaks shade the grid only. Appointments outside them remain visible.");
    VBox heading = new VBox(4, title, supporting, toolbar);
    heading.setPadding(new Insets(0, 0, 12, 0));
    BorderPane page = new BorderPane();
    page.setId("doctor-calendar-page");
    page.getStyleClass().add("calendar-page");
    page.setPadding(new Insets(4, 0, 0, 0));
    page.setTop(heading);
    return page;
  }

  private void goTo(CalendarWeek selectedWeek) {
    week = Objects.requireNonNull(selectedWeek, "selectedWeek");
    refresh();
  }

  private void goToToday() {
    if (isScheduleMode()) {
      scheduleAnchor = CalendarScheduleCalculations.today(clock);
      refresh();
    } else {
      goTo(CalendarWeek.today(clock, currentSettings().firstDayOfWeek()));
    }
  }

  private void goToPrevious() {
    if (isScheduleMode()) {
      scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, -1);
      refresh();
    } else {
      goTo(week.previous());
    }
  }

  private void goToNext() {
    if (isScheduleMode()) {
      scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, 1);
      refresh();
    } else {
      goTo(week.next());
    }
  }

  private void selectDate(LocalDate date) {
    if (isScheduleMode()) {
      scheduleAnchor = Objects.requireNonNull(date, "date");
      refresh();
    } else {
      DoctorCalendarSettings settings = currentSettings();
      goTo(CalendarWeek.containing(date, settings.firstDayOfWeek()));
    }
  }

  private void changeMode(String selectedMode) {
    if (selectedMode == null) {
      return;
    }
    weekPicker.hide();
    refresh();
  }

  private boolean isWeekMode() {
    return WEEK_MODE.equals(viewMode.getValue());
  }

  private boolean isScheduleMode() {
    return SCHEDULE_MODE.equals(viewMode.getValue());
  }

  private CalendarWeek pickerWeek() {
    if (isScheduleMode()) {
      return CalendarWeek.containing(scheduleAnchor, currentSettings().firstDayOfWeek());
    }
    return week;
  }

  private void updateRangeLabel() {
    if (isScheduleMode()) {
      rangeButton.setText(SCHEDULE_LABEL_FORMATTER.format(scheduleAnchor));
      rangeButton.setAccessibleText("Choose a Schedule start date");
    } else {
      rangeButton.setText(week.label());
      rangeButton.setAccessibleText("Choose a week");
    }
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void disposeScheduleList() {
    if (scheduleList != null) {
      scheduleList.dispose();
      scheduleList = null;
    }
  }

  private DoctorCalendarSettings currentSettings() {
    try {
      return services.calendarService().getSettings(session);
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      feedback.setText(userMessage(exception, "Calendar settings are temporarily unavailable"));
      return DoctorCalendarSettings.defaults(session.accountId());
    }
  }

  private DoctorCalendarSettings loadSettings() {
    return currentSettings();
  }

  private void updateCurrentTime() {
    LocalDateTime currentTime = LocalDateTime.now(clock);
    if (grid != null) {
      grid.updateCurrentTime(currentTime);
    }
    if (scheduleList != null) {
      scheduleList.updateCurrentTime(currentTime);
    }
  }

  private void openCreateAppointment(LocalDateTime initialStart) {
    AppointmentDialog.showCreate(
        services, session, session.accountId(), initialStart, feedback, this::refresh);
  }

  private void openAppointment(CalendarAppointment appointment) {
    if (appointment.status() == AppointmentStatus.PENDING
        || appointment.status() == AppointmentStatus.ACCEPTED) {
      AppointmentDialog.showDoctorEdit(
          services, session, appointment.appointmentId(), feedback, this::refresh);
    }
  }

  private void changeDecision(CalendarAppointment appointment, AppointmentStatus decision) {
    try {
      if (decision == AppointmentStatus.ACCEPTED) {
        services.appointmentService().accept(session, appointment.appointmentId());
        feedback.setText("Appointment accepted");
      } else if (decision == AppointmentStatus.DECLINED) {
        services.appointmentService().decline(session, appointment.appointmentId());
        feedback.setText("Appointment declined");
      }
      refresh();
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      feedback.setText(userMessage(exception, "Appointment decision is temporarily unavailable"));
      refresh();
    }
  }

  private static String userMessage(Exception exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }
}
