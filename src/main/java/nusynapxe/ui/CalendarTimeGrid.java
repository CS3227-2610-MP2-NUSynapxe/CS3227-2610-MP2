package nusynapxe.ui;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.service.CalendarCalculations;

/** Renders the scrollable seven-day time grid used by the Doctor Calendar. */
final class CalendarTimeGrid extends BorderPane {
  static final double APPOINTMENT_INSET = 2;
  static final double TIME_AXIS_WIDTH = 44;
  static final double MIN_DAY_COLUMN_WIDTH = 120;
  static final double DAY_COLUMN_WIDTH = 168;
  static final int HALF_HOURS_PER_DAY = 48;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

  private final List<LocalDate> dates;
  private final DoctorCalendarWeek data;
  private final Clock clock;
  private final CalendarTimeGridLayout.Layout layout;
  private final DisplayProfile profile;

  CalendarTimeGrid(CalendarWeek week, DoctorCalendarWeek data, Clock clock) {
    this(week.dates(), data, clock, InteractionHandlers.none(), DisplayProfile.FULL);
  }

  CalendarTimeGrid(
      CalendarWeek week, DoctorCalendarWeek data, Clock clock, InteractionHandlers handlers) {
    this(week.dates(), data, clock, handlers, DisplayProfile.FULL);
  }

  CalendarTimeGrid(
      List<LocalDate> dates, DoctorCalendarWeek data, Clock clock, InteractionHandlers handlers) {
    this(dates, data, clock, handlers, DisplayProfile.FULL);
  }

  CalendarTimeGrid(
      List<LocalDate> dates,
      DoctorCalendarWeek data,
      Clock clock,
      InteractionHandlers handlers,
      DisplayProfile profile) {
    Objects.requireNonNull(dates, "dates");
    if (dates.isEmpty()) {
      throw new IllegalArgumentException("Calendar dates must not be empty");
    }
    this.dates = List.copyOf(dates);
    this.data = data;
    this.clock = clock;
    this.profile = Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(handlers, "handlers");
    setId("doctor-calendar-time-grid");
    getStyleClass().add("calendar-time-grid");
    getStyleClass().add("calendar-time-grid-" + profile.styleName());
    layout = new CalendarTimeGridLayout(this.dates, data, clock, handlers, profile).build();
    setCenter(layout.center());
    updateCurrentTime(LocalDateTime.now(clock));
    scrollToUsefulTime(LocalDateTime.now(clock));
  }

  /** Refreshes elapsed shading and the current-time line from the supplied local time. */
  void updateCurrentTime(LocalDateTime now) {
    for (Map.Entry<LocalDate, CalendarTimeGridLayout.DayColumn> entry :
        layout.columns().entrySet()) {
      CalendarTimeGridLayout.DayColumn column = entry.getValue();
      for (int index = 0; index < column.periods().size(); index++) {
        int minute = index * 30;
        SegmentKind kind =
            CalendarCalculations.classify(entry.getKey(), minute, data.settings(), now);
        Region period = column.periods().get(index);
        period.getStyleClass().removeIf(style -> style.startsWith("calendar-period-"));
        period.getStyleClass().add("calendar-period-" + kind.name().toLowerCase(Locale.ROOT));
        period.setAccessibleText(periodDescription(entry.getKey(), minute, data.settings(), kind));
      }
      int currentMinute = CalendarCalculations.currentMinute(entry.getKey(), now);
      column.currentLine().setVisible(currentMinute >= 0);
      column.currentLine().setManaged(currentMinute >= 0);
      if (currentMinute >= 0) {
        column.currentLine().setTranslateY(currentMinute * profile.halfHourHeight() / 30.0);
      }
    }
  }

  /** Scrolls the timeline near the most useful visible minute. */
  void scrollToUsefulTime(LocalDateTime now) {
    int minute = CalendarCalculations.initialScrollMinute(dates, data, now);
    Platform.runLater(
        () -> {
          layout.scroll().applyCss();
          layout.scroll().layout();
          layout.scroll().setVvalue(scrollValueForMinute(minute));
        });
  }

  private double scrollValueForMinute(int minute) {
    ScrollPane scroll = layout.scroll();
    Bounds viewport = scroll.getViewportBounds();
    Node content = scroll.getContent();
    if (content == null || viewport.getHeight() <= 0) {
      return 0;
    }
    double contentHeight = content.getLayoutBounds().getHeight();
    double maxScroll = contentHeight - viewport.getHeight();
    if (maxScroll <= 0) {
      return 0;
    }
    double bodyTop = 0;
    double bodyHeight = contentHeight;
    if (content instanceof VBox grid && grid.getChildren().size() > 1) {
      Node body = grid.getChildren().get(1);
      bodyTop = body.getLayoutY();
      bodyHeight = body.getLayoutBounds().getHeight();
    }
    if (bodyHeight <= 0) {
      return 0;
    }
    double clampedMinute = Math.max(0, Math.min(WorkingInterval.MINUTES_PER_DAY, minute));
    double targetOffset = bodyTop + clampedMinute * bodyHeight / WorkingInterval.MINUTES_PER_DAY;
    return Math.max(0, Math.min(1, targetOffset / maxScroll));
  }

  private static String periodDescription(
      LocalDate day, int minute, DoctorCalendarSettings settings, SegmentKind kind) {
    boolean enabled = settings.isEnabled(day.getDayOfWeek());
    boolean insideWorkingHours =
        settings.intervals(day.getDayOfWeek()).stream()
            .anyMatch(interval -> interval.contains(minute));
    String state;
    if (kind == SegmentKind.ELAPSED) {
      state =
          !enabled
              ? "elapsed time on a disabled day"
              : insideWorkingHours ? "elapsed working time" : "elapsed non-working time";
    } else if (!enabled) {
      state = "disabled working day";
    } else if (!insideWorkingHours) {
      state = "outside working hours or break";
    } else {
      state = "working time";
    }
    return day + " " + formatMinute(minute) + ": " + state;
  }

  static String formatMinute(int minute) {
    return TIME_FORMAT.format(java.time.LocalTime.MIDNIGHT.plusMinutes(minute));
  }

  enum DisplayProfile {
    FULL(100, true, "full"),
    COMPACT(44, false, "compact");

    private final double slotHeight;
    private final boolean showInlineDecisions;
    private final String cssSuffix;

    DisplayProfile(double halfHourHeight, boolean inlineDecisions, String styleName) {
      this.slotHeight = halfHourHeight;
      this.showInlineDecisions = inlineDecisions;
      this.cssSuffix = styleName;
    }

    double halfHourHeight() {
      return slotHeight;
    }

    boolean inlineDecisions() {
      return showInlineDecisions;
    }

    String styleName() {
      return cssSuffix;
    }
  }

  /** Callbacks supplied by the owning Doctor Calendar page. */
  record InteractionHandlers(
      Consumer<CalendarAppointment> selected,
      BiConsumer<CalendarAppointment, AppointmentStatus> decision,
      Consumer<LocalDateTime> emptySlot,
      Consumer<DoctorTimeOff> timeOffSelected) {
    static InteractionHandlers none() {
      return new InteractionHandlers(null, null, null, null);
    }
  }
}
