package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds and manages the read-only weekly Calendar page for a Doctor. */
public final class DoctorCalendarView {
  private final ClinicServices services;
  private final Session session;
  private final Runnable onSettings;
  private final Label feedback;
  private final Clock clock;
  private final BorderPane root;
  private final Button rangeButton;
  private final Timeline currentTimeTicker;
  private final CalendarWeekPicker weekPicker;
  private CalendarWeek week;
  private CalendarTimeGrid grid;
  private boolean shown;

  /** Creates a Calendar page using the Singapore clinic system clock. */
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
    this.clock = Objects.requireNonNull(clock, "clock");
    DoctorCalendarSettings settings = loadSettings();
    week = CalendarWeek.today(clock, settings.firstDayOfWeek());
    rangeButton = new Button(week.label());
    weekPicker = new CalendarWeekPicker(this::selectDate);
    root = buildRoot();
    currentTimeTicker =
        new Timeline(new KeyFrame(Duration.minutes(1), event -> updateCurrentTime()));
    currentTimeTicker.setCycleCount(Timeline.INDEFINITE);
    refresh();
  }

  /** Returns the Calendar page node. */
  public Parent view() {
    return root;
  }

  /** Refreshes the selected week and its saved display settings. */
  public void refresh() {
    try {
      DoctorCalendarSettings settings = services.calendarService().getSettings(session);
      week = CalendarWeek.containing(week.start(), settings.firstDayOfWeek());
      DoctorCalendarWeek data = services.calendarService().getWeek(session, week.start());
      rangeButton.setText(week.label());
      grid = new CalendarTimeGrid(week, data, clock);
      root.setCenter(grid);
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
  }

  /** Stops all page-owned resources when the Doctor workspace is discarded. */
  public void dispose() {
    shown = false;
    currentTimeTicker.stop();
    weekPicker.hide();
  }

  private BorderPane buildRoot() {
    Button today = UiComponents.secondaryButton("Today", "doctor-calendar-today");
    today.setAccessibleText("Go to the current week");
    today.setOnAction(event -> goTo(CalendarWeek.today(clock, currentSettings().firstDayOfWeek())));
    Button previous = UiComponents.secondaryButton("‹", "doctor-calendar-previous");
    previous.setAccessibleText("Previous week");
    previous.setOnAction(event -> goTo(week.previous()));
    Button next = UiComponents.secondaryButton("›", "doctor-calendar-next");
    next.setAccessibleText("Next week");
    next.setOnAction(event -> goTo(week.next()));
    rangeButton.setId("doctor-calendar-week-picker");
    rangeButton.setAccessibleText("Choose a week");
    rangeButton.getStyleClass().add("calendar-range-button");
    rangeButton.setOnAction(event -> weekPicker.show(rangeButton, week));
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
    HBox toolbar = new HBox(8, today, previous, next, rangeButton, spacer, settings);
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

  private void selectDate(LocalDate date) {
    DoctorCalendarSettings settings = currentSettings();
    goTo(CalendarWeek.containing(date, settings.firstDayOfWeek()));
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
    if (grid != null) {
      grid.updateCurrentTime(LocalDateTime.now(clock));
    }
  }

  private static String userMessage(Exception exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }
}
