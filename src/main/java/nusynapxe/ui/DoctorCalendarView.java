package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarScheduleCalculations;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds and manages the Calendar and Agenda views for a Doctor. */
public final class DoctorCalendarView {
  private static final String CALENDAR_MODE = "Calendar";
  private static final String AGENDA_MODE = "Agenda";
  private static final long MAX_RANGE_DAYS = 31;
  private final ClinicServices services;
  private final Session session;
  private final Runnable onSettings;
  private final Label feedback;
  private final Clock clock;
  private final BorderPane root;
  private final Button previous;
  private final Button next;
  private final DatePicker scheduleDate;
  private final DatePicker from;
  private final DatePicker to;
  private final VBox fromField;
  private final VBox toField;
  private final ComboBox<String> viewMode;
  private final Timeline currentTimeTicker;
  private VBox toolbar;
  private HBox toolbarNavigationGroup;
  private HBox toolbarActionGroup;
  private HBox toolbarTrailingGroup;
  private HBox toolbarMainRow;
  private HBox toolbarActionsRow;
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
    LocalDate today = LocalDate.now(this.clock);
    scheduleAnchor = CalendarScheduleCalculations.today(this.clock);
    previous = UiComponents.secondaryButton("‹", "doctor-calendar-previous");
    next = UiComponents.secondaryButton("›", "doctor-calendar-next");
    scheduleDate = UiComponents.compactDatePicker(scheduleAnchor);
    scheduleDate.setId("doctor-calendar-schedule-date");
    scheduleDate.setShowWeekNumbers(false);
    from = UiComponents.compactDatePicker(today);
    from.setId("doctor-calendar-from");
    from.setShowWeekNumbers(false);
    to = UiComponents.compactDatePicker(today.plusDays(6));
    to.setId("doctor-calendar-to");
    to.setShowWeekNumbers(false);
    fromField = UiComponents.fieldGroup("From", from);
    toField = UiComponents.fieldGroup("To", to);
    viewMode = UiComponents.compactSelector();
    root = buildRoot();
    currentTimeTicker =
        new Timeline(new KeyFrame(Duration.minutes(1), event -> updateCurrentTime()));
    currentTimeTicker.setCycleCount(Timeline.INDEFINITE);
    applyModeVisibility();
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
      if (isCalendarMode()) {
        disposeScheduleList();
        List<LocalDate> dates = selectedDates();
        DoctorCalendarWeek data =
            services.calendarService().getRange(session, from.getValue(), to.getValue());
        grid =
            new CalendarTimeGrid(
                dates,
                data,
                clock,
                new CalendarTimeGrid.InteractionHandlers(
                    this::openAppointment,
                    this::changeDecision,
                    this::openCreateAppointment,
                    this::openTimeOff));
        root.setCenter(grid);
      } else {
        grid = null;
        disposeScheduleList();
        scheduleList =
            new CalendarScheduleList(
                services, session, scheduleAnchor, clock, this::openAppointment);
        root.setCenter(scheduleList);
      }
      scheduleDate.setValue(scheduleAnchor);
      if (shown) {
        currentTimeTicker.play();
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Calendar is temporarily unavailable"));
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
    disposeScheduleList();
  }

  /** Stops all page-owned resources when the Doctor workspace is discarded. */
  public void dispose() {
    shown = false;
    currentTimeTicker.stop();
    disposeScheduleList();
  }

  private BorderPane buildRoot() {
    Button today = UiComponents.secondaryButton("Today", "doctor-calendar-today");
    today.setAccessibleText("Go to today");
    today.setOnAction(event -> goToToday());
    previous.setAccessibleText("Previous");
    previous.setOnAction(event -> goToPrevious());
    next.setAccessibleText("Next");
    next.setOnAction(event -> goToNext());
    Button addAppointment =
        UiComponents.primaryButton("Add appointment", "doctor-calendar-add-appointment");
    addAppointment.setAccessibleText("Add appointment to my schedule");
    addAppointment.setOnAction(event -> openCreateAppointment(null));
    Button blockTime = UiComponents.secondaryButton("Block time", "doctor-calendar-block-time");
    blockTime.setAccessibleText("Block time on my schedule");
    blockTime.setOnAction(event -> openCreateTimeOff());
    Button refreshButton = new Button("↻");
    refreshButton.setId("doctor-calendar-refresh");
    refreshButton.setAccessibleText("Refresh Calendar");
    refreshButton.setTooltip(new javafx.scene.control.Tooltip("Refresh Calendar"));
    refreshButton.getStyleClass().add("calendar-settings-button");
    refreshButton.setOnAction(event -> refresh());
    scheduleDate.setAccessibleText("Choose Agenda start date");
    scheduleDate.setOnAction(event -> selectDate(scheduleDate.getValue()));
    from.setOnAction(event -> refresh());
    to.setOnAction(event -> refresh());
    fromField.setPrefWidth(190);
    toField.setPrefWidth(190);
    viewMode.setId("doctor-calendar-view-mode");
    viewMode.setAccessibleText("Choose Calendar view");
    viewMode.getItems().addAll(CALENDAR_MODE, AGENDA_MODE);
    viewMode.setEditable(false);
    viewMode.setValue(CALENDAR_MODE);
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
    toolbarNavigationGroup =
        new HBox(8, today, previous, scheduleDate, next, fromField, toField, viewMode);
    toolbarNavigationGroup.setAlignment(Pos.BOTTOM_LEFT);
    toolbarNavigationGroup.getStyleClass().add("calendar-toolbar-group");
    toolbarActionGroup = new HBox(8, addAppointment, blockTime);
    toolbarActionGroup.setId("doctor-calendar-action-group");
    toolbarActionGroup.setAlignment(Pos.BOTTOM_LEFT);
    toolbarActionGroup.getStyleClass().add("calendar-toolbar-actions");
    toolbarTrailingGroup = new HBox(8, refreshButton, settings);
    toolbarTrailingGroup.setAlignment(Pos.BOTTOM_LEFT);
    toolbarTrailingGroup.getStyleClass().add("calendar-toolbar-group");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    toolbarMainRow =
        new HBox(8, toolbarNavigationGroup, toolbarActionGroup, spacer, toolbarTrailingGroup);
    toolbarMainRow.setId("doctor-calendar-toolbar-main");
    toolbarMainRow.setAlignment(Pos.BOTTOM_LEFT);
    toolbarActionsRow = new HBox(8);
    toolbarActionsRow.setId("doctor-calendar-toolbar-actions");
    toolbarActionsRow.setAlignment(Pos.BOTTOM_LEFT);
    toolbar = new VBox(8, toolbarMainRow);
    toolbar.setId("doctor-calendar-toolbar");
    toolbar.getStyleClass().add("calendar-toolbar");
    toolbar.widthProperty().addListener((observable, oldWidth, newWidth) -> updateToolbarLayout());
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
    updateToolbarLayout();
    return page;
  }

  private void goToToday() {
    if (isAgendaMode()) {
      scheduleAnchor = CalendarScheduleCalculations.today(clock);
      scheduleDate.setValue(scheduleAnchor);
    } else {
      LocalDate current = LocalDate.now(clock);
      from.setValue(current);
      to.setValue(current.plusDays(6));
    }
    refresh();
  }

  private void goToPrevious() {
    scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, -1);
    scheduleDate.setValue(scheduleAnchor);
    refresh();
  }

  private void goToNext() {
    scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, 1);
    scheduleDate.setValue(scheduleAnchor);
    refresh();
  }

  private void selectDate(LocalDate date) {
    if (date == null) {
      scheduleDate.setValue(scheduleAnchor);
      return;
    }
    scheduleAnchor = date;
    scheduleDate.setValue(scheduleAnchor);
    refresh();
  }

  private void changeMode(String selectedMode) {
    if (selectedMode == null) {
      return;
    }
    applyModeVisibility();
    refresh();
  }

  private void applyModeVisibility() {
    boolean agendaMode = isAgendaMode();
    previous.setVisible(agendaMode);
    previous.setManaged(agendaMode);
    next.setVisible(agendaMode);
    next.setManaged(agendaMode);
    scheduleDate.setVisible(agendaMode);
    scheduleDate.setManaged(agendaMode);
    fromField.setVisible(!agendaMode);
    fromField.setManaged(!agendaMode);
    toField.setVisible(!agendaMode);
    toField.setManaged(!agendaMode);
    updateToolbarLayout();
  }

  private boolean isCalendarMode() {
    return CALENDAR_MODE.equals(viewMode.getValue());
  }

  private boolean isAgendaMode() {
    return AGENDA_MODE.equals(viewMode.getValue());
  }

  private List<LocalDate> selectedDates() {
    LocalDate start = from.getValue();
    LocalDate end = to.getValue();
    if (start == null || end == null) {
      throw new ValidationException("Select both From and To dates");
    }
    if (end.isBefore(start)) {
      throw new ValidationException("Calendar To date must not be before From date");
    }
    if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
      throw new ValidationException(
          "Select a calendar range of " + MAX_RANGE_DAYS + " days or fewer");
    }
    return start.datesUntil(end.plusDays(1)).toList();
  }

  private void updateToolbarLayout() {
    if (toolbar == null) {
      return;
    }
    double availableWidth = toolbar.getWidth();
    double requiredWidth =
        toolbarNavigationGroup.prefWidth(-1)
            + toolbarActionGroup.prefWidth(-1)
            + toolbarTrailingGroup.prefWidth(-1)
            + toolbarMainRow.getSpacing() * 3;
    boolean shouldWrap = availableWidth > 0 && availableWidth + 0.5 < requiredWidth;
    boolean isWrapped = toolbarActionsRow.getChildren().contains(toolbarActionGroup);
    if (shouldWrap == isWrapped) {
      return;
    }
    if (shouldWrap) {
      toolbarMainRow.getChildren().remove(toolbarActionGroup);
      toolbarActionsRow.getChildren().setAll(toolbarActionGroup);
      toolbar.getChildren().setAll(toolbarMainRow, toolbarActionsRow);
    } else {
      toolbarActionsRow.getChildren().clear();
      toolbarMainRow.getChildren().add(1, toolbarActionGroup);
      toolbar.getChildren().setAll(toolbarMainRow);
    }
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void disposeScheduleList() {
    if (scheduleList != null) {
      scheduleList.dispose();
      scheduleList = null;
    }
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

  private void openCreateTimeOff() {
    LocalDate today = LocalDate.now(clock);
    List<LocalDate> visibleDates = selectedDates();
    LocalDate date = visibleDates.contains(today) ? today : visibleDates.getFirst();
    LocalDateTime now = LocalDateTime.now(clock);
    int minute = date.equals(today) ? (now.getMinute() < 30 ? 0 : 30) : 0;
    int hour = date.equals(today) ? now.getHour() : 9;
    if (hour == 23 && minute == 30) {
      hour = 23;
      minute = 0;
    }
    TimeOffDialog.showCreate(services, session, date.atTime(hour, minute), feedback, this::refresh);
  }

  private void openTimeOff(nusynapxe.domain.DoctorTimeOff timeOff) {
    TimeOffDialog.showDetails(services, session, timeOff, feedback, this::refresh);
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
      UiComponents.showError(
          feedback, userMessage(exception, "Appointment decision is temporarily unavailable"));
      refresh();
    }
  }

  private static String userMessage(Exception exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }
}
