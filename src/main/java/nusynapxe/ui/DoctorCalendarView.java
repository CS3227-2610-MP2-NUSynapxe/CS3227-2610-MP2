package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.Session;
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
  private final ClinicTaskRunner taskRunner;
  private final BooleanSupplier workspaceActive;
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
  private DoctorCalendarToolbar.Controls toolbarControls;
  private LocalDate scheduleAnchor;
  private CalendarTimeGrid grid;
  private CalendarScheduleList scheduleList;
  private boolean shown;
  private long refreshGeneration;

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
    this(
        services,
        session,
        onSettings,
        feedback,
        Clock.system(CalendarService.CLINIC_ZONE),
        ClinicTaskRunner.immediate());
  }

  DoctorCalendarView(
      ClinicServices services, Session session, Runnable onSettings, Label feedback, Clock clock) {
    this(services, session, onSettings, feedback, clock, ClinicTaskRunner.immediate());
  }

  DoctorCalendarView(
      ClinicServices services,
      Session session,
      Runnable onSettings,
      Label feedback,
      Clock clock,
      ClinicTaskRunner taskRunner) {
    this(services, session, onSettings, feedback, clock, taskRunner, () -> true);
  }

  DoctorCalendarView(
      ClinicServices services,
      Session session,
      Runnable onSettings,
      Label feedback,
      Clock clock,
      ClinicTaskRunner taskRunner,
      BooleanSupplier workspaceActive) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.onSettings = Objects.requireNonNull(onSettings, "onSettings");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.clock = Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    this.workspaceActive = Objects.requireNonNull(workspaceActive, "workspaceActive");
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
    refreshGeneration++;
    long generation = refreshGeneration;
    try {
      if (isCalendarMode()) {
        disposeScheduleList();
        CalendarRangeSnapshot range = selectedRange();
        List<LocalDate> dates = selectedDates(range);
        submit(
            () -> services.calendarService().getRange(session, range.from(), range.to()),
            data -> {
              if (generation != refreshGeneration) {
                return;
              }
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
              scheduleDate.setValue(scheduleAnchor);
              if (shown) {
                currentTimeTicker.play();
              }
            },
            failure -> {
              if (generation == refreshGeneration) {
                UiComponents.showError(
                    feedback, userMessage(failure, "Calendar is temporarily unavailable"));
              }
            });
      } else {
        grid = null;
        disposeScheduleList();
        scheduleList =
            new CalendarScheduleList(
                services, session, scheduleAnchor, clock, taskRunner, this::openAppointment);
        root.setCenter(scheduleList);
        scheduleDate.setValue(scheduleAnchor);
        if (shown) {
          currentTimeTicker.play();
        }
      }
    } catch (ValidationException exception) {
      UiComponents.showError(
          feedback, userMessage(exception, "Calendar is temporarily unavailable"));
    }
  }

  private <T> void submit(
      ClinicTaskRunner.ClinicTask<T> task,
      java.util.function.Consumer<T> onSuccess,
      java.util.function.Consumer<Throwable> onFailure) {
    try {
      taskRunner.submit(task, onSuccess, onFailure);
    } catch (RejectedExecutionException exception) {
      onFailure.accept(exception);
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
    refreshGeneration++;
    currentTimeTicker.stop();
    disposeScheduleList();
  }

  private BorderPane buildRoot() {
    toolbarControls =
        DoctorCalendarToolbar.create(
            previous,
            next,
            scheduleDate,
            from,
            to,
            fromField,
            toField,
            viewMode,
            this::goToToday,
            this::goToPrevious,
            this::goToNext,
            () -> openCreateAppointment(null),
            this::openCreateTimeOff,
            this::refresh,
            () -> {
              hide();
              onSettings.run();
            },
            this::selectDate,
            this::changeMode);
    return toolbarControls.page();
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
    return selectedDates(validateRange(start, end));
  }

  private List<LocalDate> selectedDates(CalendarRangeSnapshot range) {
    LocalDate start = range.from();
    LocalDate end = range.to();
    return start.datesUntil(end.plusDays(1)).toList();
  }

  private CalendarRangeSnapshot selectedRange() {
    return validateRange(from.getValue(), to.getValue());
  }

  private CalendarRangeSnapshot validateRange(LocalDate start, LocalDate end) {
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
    return CalendarRangeSnapshot.capture(start, end);
  }

  private void updateToolbarLayout() {
    if (toolbarControls != null) {
      DoctorCalendarToolbar.updateLayout(toolbarControls);
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
        services,
        session,
        session.accountId(),
        initialStart,
        feedback,
        this::refresh,
        clock,
        taskRunner,
        workspaceActive);
  }

  private void openCreateTimeOff() {
    try {
      LocalDate today = LocalDate.now(clock);
      LocalDate date;
      if (isAgendaMode()) {
        date = scheduleAnchor;
      } else {
        List<LocalDate> visibleDates = selectedDates();
        date = visibleDates.contains(today) ? today : visibleDates.getFirst();
      }
      LocalDateTime now = LocalDateTime.now(clock);
      int minute = date.equals(today) ? (now.getMinute() < 30 ? 0 : 30) : 0;
      int hour = date.equals(today) ? now.getHour() : 9;
      if (hour == 23 && minute == 30) {
        hour = 23;
        minute = 0;
      }
      TimeOffDialog.showCreate(
          services,
          session,
          date.atTime(hour, minute),
          feedback,
          this::refresh,
          taskRunner,
          workspaceActive);
    } catch (ValidationException | IllegalArgumentException exception) {
      UiComponents.showError(feedback, userMessage(exception, "Select a valid calendar range"));
    }
  }

  private void openTimeOff(nusynapxe.domain.DoctorTimeOff timeOff) {
    TimeOffDialog.showDetails(
        services, session, timeOff, feedback, this::refresh, taskRunner, workspaceActive);
  }

  private void openAppointment(CalendarAppointment appointment) {
    if (appointment.status() == AppointmentStatus.PENDING
        || appointment.status() == AppointmentStatus.ACCEPTED) {
      AppointmentDialog.showDoctorEdit(
          services,
          session,
          appointment.appointmentId(),
          feedback,
          this::refresh,
          taskRunner,
          workspaceActive);
    }
  }

  private void changeDecision(CalendarAppointment appointment, AppointmentStatus decision) {
    submit(
        () -> {
          if (decision == AppointmentStatus.ACCEPTED) {
            services.appointmentService().accept(session, appointment.appointmentId());
          } else if (decision == AppointmentStatus.DECLINED) {
            services.appointmentService().decline(session, appointment.appointmentId());
          }
          return null;
        },
        ignored -> {
          feedback.setText(
              decision == AppointmentStatus.ACCEPTED
                  ? "Appointment accepted"
                  : "Appointment declined");
          refresh();
        },
        failure -> {
          UiComponents.showError(
              feedback, userMessage(failure, "Appointment decision is temporarily unavailable"));
          refresh();
        });
  }

  private static String userMessage(Throwable exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }
}
