package nusynapxe.ui;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarAppointmentBlock;
import nusynapxe.domain.CalendarTimeOffBlock;
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.DoctorTimeOff;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.service.CalendarCalculations;
import nusynapxe.service.CalendarService;

/** Renders the scrollable seven-day time grid used by the Doctor Calendar. */
final class CalendarTimeGrid extends BorderPane {
  private static final double APPOINTMENT_INSET = 2;
  private static final double TIME_AXIS_WIDTH = 44;
  private static final double MIN_DAY_COLUMN_WIDTH = 120;
  private static final double DAY_COLUMN_WIDTH = 168;
  private static final int HALF_HOURS_PER_DAY = 48;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private final List<LocalDate> dates;
  private final DoctorCalendarWeek data;
  private final Clock clock;
  private final InteractionHandlers handlers;
  private final DisplayProfile profile;
  private final Map<LocalDate, DayColumn> columns = new LinkedHashMap<>();
  private ScrollPane scroll;

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
    this.handlers = Objects.requireNonNull(handlers, "handlers");
    this.profile = Objects.requireNonNull(profile, "profile");
    setId("doctor-calendar-time-grid");
    getStyleClass().add("calendar-time-grid");
    getStyleClass().add("calendar-time-grid-" + profile.styleName());
    build();
    updateCurrentTime(LocalDateTime.now(clock));
    scrollToUsefulTime(LocalDateTime.now(clock));
  }

  /** Refreshes elapsed shading and the current-time line from the supplied local time. */
  void updateCurrentTime(LocalDateTime now) {
    for (Map.Entry<LocalDate, DayColumn> entry : columns.entrySet()) {
      DayColumn column = entry.getValue();
      for (int index = 0; index < column.periods.size(); index++) {
        int minute = index * 30;
        SegmentKind kind =
            CalendarCalculations.classify(entry.getKey(), minute, data.settings(), now);
        Region period = column.periods.get(index);
        period.getStyleClass().removeIf(style -> style.startsWith("calendar-period-"));
        period.getStyleClass().add("calendar-period-" + kind.name().toLowerCase(Locale.ROOT));
        period.setAccessibleText(periodDescription(entry.getKey(), minute, data.settings(), kind));
      }
      int currentMinute = CalendarCalculations.currentMinute(entry.getKey(), now);
      column.currentLine.setVisible(currentMinute >= 0);
      column.currentLine.setManaged(currentMinute >= 0);
      if (currentMinute >= 0) {
        column.currentLine.setTranslateY(currentMinute * profile.halfHourHeight() / 30.0);
      }
    }
  }

  private void build() {
    VBox grid = new VBox();
    grid.setId("doctor-calendar-grid-content");
    grid.getStyleClass().add("calendar-grid-content");
    grid.setMinWidth(TIME_AXIS_WIDTH + (dates.size() * MIN_DAY_COLUMN_WIDTH));
    grid.setPrefWidth(TIME_AXIS_WIDTH + (dates.size() * DAY_COLUMN_WIDTH));
    grid.setMaxWidth(Double.MAX_VALUE);
    grid.setFillWidth(true);
    grid.setSnapToPixel(false);
    HBox headerRow = new HBox();
    headerRow.setMinWidth(TIME_AXIS_WIDTH + (dates.size() * MIN_DAY_COLUMN_WIDTH));
    headerRow.setPrefWidth(TIME_AXIS_WIDTH + (dates.size() * DAY_COLUMN_WIDTH));
    headerRow.setMaxWidth(Double.MAX_VALUE);
    headerRow.setSnapToPixel(false);
    Label timeHeader = header("Time", "doctor-calendar-time-header");
    timeHeader.setMinWidth(TIME_AXIS_WIDTH);
    timeHeader.setPrefWidth(TIME_AXIS_WIDTH);
    timeHeader.setMaxWidth(TIME_AXIS_WIDTH);
    headerRow.getChildren().add(timeHeader);
    for (LocalDate date : dates) {
      Region dayHeader = dayHeader(date);
      dayHeader.minWidth(MIN_DAY_COLUMN_WIDTH);
      dayHeader.prefWidth(DAY_COLUMN_WIDTH);
      dayHeader.maxWidth(Double.MAX_VALUE);
      HBox.setHgrow(dayHeader, Priority.ALWAYS);
      headerRow.getChildren().add(dayHeader);
    }
    VBox timeAxis = new VBox();
    timeAxis.setId("doctor-calendar-time-axis");
    timeAxis.setMinWidth(TIME_AXIS_WIDTH);
    timeAxis.setPrefWidth(TIME_AXIS_WIDTH);
    timeAxis.setMaxWidth(TIME_AXIS_WIDTH);
    for (int index = 0; index < HALF_HOURS_PER_DAY; index++) {
      Label label =
          new Label(TIME_FORMAT.format(java.time.LocalTime.MIDNIGHT.plusMinutes(index * 30L)));
      label.setId("doctor-calendar-time-label-" + index);
      label.getStyleClass().add("calendar-time-label");
      label.setPrefHeight(profile.halfHourHeight());
      label.setMinHeight(profile.halfHourHeight());
      label.setMaxHeight(profile.halfHourHeight());
      label.setAlignment(Pos.TOP_LEFT);
      timeAxis.getChildren().add(label);
    }
    HBox bodyRow = new HBox();
    bodyRow.setMinWidth(TIME_AXIS_WIDTH + (dates.size() * MIN_DAY_COLUMN_WIDTH));
    bodyRow.setPrefWidth(TIME_AXIS_WIDTH + (dates.size() * DAY_COLUMN_WIDTH));
    bodyRow.setMaxWidth(Double.MAX_VALUE);
    bodyRow.setSnapToPixel(false);
    bodyRow.getChildren().add(timeAxis);
    for (LocalDate date : dates) {
      DayColumn column = buildDayColumn(date, data.appointments());
      columns.put(date, column);
      HBox.setHgrow(column.surface, Priority.ALWAYS);
      bodyRow.getChildren().add(column.surface);
    }
    grid.getChildren().addAll(headerRow, bodyRow);
    scroll = new ScrollPane(grid);
    scroll.setId("doctor-calendar-scroll");
    scroll.setPannable(true);
    scroll.setFitToHeight(false);
    scroll.setFitToWidth(true);
    VBox center = new VBox(8);
    if (data.appointments().isEmpty() && data.timeOff().isEmpty()) {
      Label empty =
          UiComponents.emptyState(
              "doctor-calendar-empty", "No appointments or blocked time in this date range.");
      center.getChildren().add(empty);
    }
    center.getChildren().add(scroll);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    setCenter(center);
  }

  private DayColumn buildDayColumn(LocalDate day, List<CalendarAppointment> appointments) {
    StackPane surface = new StackPane();
    surface.setId("doctor-calendar-day-column-" + day);
    surface.getStyleClass().add("calendar-day-column");
    surface.setMinWidth(MIN_DAY_COLUMN_WIDTH);
    surface.setPrefWidth(DAY_COLUMN_WIDTH);
    surface.setMaxWidth(Double.MAX_VALUE);
    surface.setMinHeight(profile.halfHourHeight() * HALF_HOURS_PER_DAY);
    surface.setPrefHeight(profile.halfHourHeight() * HALF_HOURS_PER_DAY);
    VBox periods = new VBox();
    periods.setMouseTransparent(true);
    periods.setPrefHeight(profile.halfHourHeight() * HALF_HOURS_PER_DAY);
    List<Region> periodCells = new ArrayList<>();
    for (int index = 0; index < HALF_HOURS_PER_DAY; index++) {
      Region period = new Region();
      period.setId("doctor-calendar-period-" + day + "-" + index);
      period.getStyleClass().add("calendar-period");
      period.setPrefHeight(profile.halfHourHeight());
      period.setMinHeight(profile.halfHourHeight());
      period.setMaxHeight(profile.halfHourHeight());
      periods.getChildren().add(period);
      periodCells.add(period);
    }
    Pane eventPane = new Pane();
    eventPane.setId("doctor-calendar-events-" + day);
    eventPane.setPickOnBounds(true);
    eventPane.setPrefHeight(profile.halfHourHeight() * HALF_HOURS_PER_DAY);
    eventPane.setMinHeight(profile.halfHourHeight() * HALF_HOURS_PER_DAY);
    eventPane.setPrefWidth(DAY_COLUMN_WIDTH);
    eventPane.setMinWidth(MIN_DAY_COLUMN_WIDTH);
    eventPane.setMaxWidth(Double.MAX_VALUE);
    Rectangle clip = new Rectangle();
    clip.widthProperty().bind(eventPane.widthProperty());
    clip.heightProperty().bind(eventPane.heightProperty());
    eventPane.setClip(clip);
    List<EventPlacement> placements = new ArrayList<>();
    for (CalendarAppointmentBlock block : CalendarCalculations.blocksForDay(day, appointments)) {
      Node node = appointmentNode(block);
      eventPane.getChildren().add(node);
      placements.add(new EventPlacement(node, block));
    }
    List<TimeOffPlacement> timeOffPlacements = new ArrayList<>();
    for (CalendarTimeOffBlock block :
        CalendarCalculations.timeOffBlocksForDay(day, data.timeOff())) {
      Node node = timeOffNode(block);
      eventPane.getChildren().add(node);
      timeOffPlacements.add(new TimeOffPlacement(node, block));
    }
    eventPane
        .widthProperty()
        .addListener(
            (observable, previous, current) -> {
              layoutEvents(eventPane, placements);
              layoutTimeOff(eventPane, timeOffPlacements);
            });
    eventPane.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.PRIMARY && handlers.emptySlot() != null) {
            handlers
                .emptySlot()
                .accept(day.atStartOfDay().plusMinutes(clickedMinute(event.getY())));
          }
        });
    layoutEvents(eventPane, placements);
    layoutTimeOff(eventPane, timeOffPlacements);
    Region currentLine = new Region();
    currentLine.setId("doctor-calendar-current-time-line-" + day);
    currentLine.getStyleClass().add("calendar-current-time-line");
    currentLine.setPrefHeight(2);
    currentLine.setMinHeight(2);
    currentLine.setMaxHeight(2);
    currentLine.setVisible(false);
    currentLine.setManaged(false);
    currentLine.setAccessibleText("Current time indicator for " + day);
    currentLine.prefWidthProperty().bind(surface.widthProperty());
    currentLine.setMouseTransparent(true);
    StackPane.setAlignment(periods, Pos.TOP_LEFT);
    StackPane.setAlignment(eventPane, Pos.TOP_LEFT);
    StackPane.setAlignment(currentLine, Pos.TOP_LEFT);
    surface.getChildren().addAll(periods, eventPane, currentLine);
    return new DayColumn(surface, periodCells, currentLine);
  }

  private Node appointmentNode(CalendarAppointmentBlock block) {
    VBox content = new VBox(2);
    content.getStyleClass().add("calendar-appointment-content");
    content.setFillWidth(true);
    Label patient = new Label(block.appointment().patientDisplayName());
    patient.getStyleClass().add("calendar-appointment-patient");
    patient.setWrapText(true);
    patient.setMinWidth(0);
    patient.setMaxWidth(Double.MAX_VALUE);
    patient.setEllipsisString("…");
    Label time =
        new Label(
            formatTime(block.appointment().startsAt())
                + " – "
                + formatTime(block.appointment().endsAt()));
    time.getStyleClass().add("calendar-appointment-time");
    Label status = UiComponents.statusBadge(block.appointment().status().name());
    status.getStyleClass().add("calendar-appointment-status");
    if (profile == DisplayProfile.COMPACT) {
      time.setText(
          time.getText()
              + " · "
              + UiComponents.humanizeStatus(block.appointment().status().name()));
      content.getChildren().addAll(patient, time);
    } else {
      content.getChildren().addAll(patient, time, status);
    }
    AppointmentStatus appointmentStatus = block.appointment().status();
    if (profile.inlineDecisions()
        && handlers.decision() != null
        && (appointmentStatus == AppointmentStatus.PENDING
            || appointmentStatus == AppointmentStatus.ACCEPTED)) {
      Button accept =
          UiComponents.primaryButton(
              "Accept", "doctor-calendar-accept-" + block.appointment().appointmentId());
      accept.getStyleClass().add("calendar-appointment-action");
      accept.setAccessibleText("Accept appointment " + block.appointment().appointmentId());
      accept.setDisable(appointmentStatus == AppointmentStatus.ACCEPTED);
      accept.setOnAction(
          event -> {
            handlers.decision().accept(block.appointment(), AppointmentStatus.ACCEPTED);
            event.consume();
          });
      Button decline =
          UiComponents.dangerButton(
              "Decline", "doctor-calendar-decline-" + block.appointment().appointmentId());
      decline.getStyleClass().add("calendar-appointment-action");
      decline.setAccessibleText("Decline appointment " + block.appointment().appointmentId());
      decline.setOnAction(
          event -> {
            handlers.decision().accept(block.appointment(), AppointmentStatus.DECLINED);
            event.consume();
          });
      HBox actions = new HBox(3, accept, decline);
      actions.setAlignment(Pos.BOTTOM_RIGHT);
      actions.getStyleClass().add("calendar-appointment-actions");
      Region actionSpacer = new Region();
      VBox.setVgrow(actionSpacer, Priority.ALWAYS);
      content.getChildren().add(actionSpacer);
      content.getChildren().add(actions);
    }
    StackPane blockNode = new StackPane(content);
    blockNode.setManaged(false);
    blockNode.setMinWidth(0);
    blockNode.setMaxWidth(Double.MAX_VALUE);
    blockNode.setMinHeight(0);
    blockNode.setMaxHeight(Double.MAX_VALUE);
    blockNode.setId(
        "doctor-calendar-appointment-" + block.appointment().appointmentId() + "-" + block.day());
    blockNode.getStyleClass().add("calendar-appointment-block");
    blockNode
        .getStyleClass()
        .add(
            "calendar-appointment-status-"
                + block.appointment().status().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    if (block.appointment().status() == AppointmentStatus.CANCELLED) {
      blockNode.getStyleClass().add("calendar-appointment-cancelled");
    }
    blockNode.setAccessibleText(
        block.appointment().patientDisplayName()
            + ", "
            + formatTime(block.appointment().startsAt())
            + " to "
            + formatTime(block.appointment().endsAt())
            + ", "
            + UiComponents.humanizeStatus(block.appointment().status().name()));
    blockNode.setFocusTraversable(handlers.selected() != null);
    blockNode.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.PRIMARY && handlers.selected() != null) {
            handlers.selected().accept(block.appointment());
            event.consume();
          }
        });
    blockNode.setOnKeyPressed(
        event -> {
          if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)
              && handlers.selected() != null) {
            handlers.selected().accept(block.appointment());
            event.consume();
          }
        });
    return blockNode;
  }

  private Node timeOffNode(CalendarTimeOffBlock block) {
    Label title = new Label("Blocked time");
    title.getStyleClass().add("calendar-time-off-title");
    Label time =
        new Label(
            formatTime(block.timeOff().startsAt()) + " – " + formatTime(block.timeOff().endsAt()));
    time.getStyleClass().add("calendar-time-off-time");
    VBox content = new VBox(2, title, time);
    StackPane node = new StackPane(content);
    node.setManaged(false);
    node.setId("doctor-calendar-time-off-" + block.timeOff().id() + "-" + block.day());
    node.getStyleClass().add("calendar-time-off-block");
    node.setAccessibleText(
        "Blocked time, "
            + formatTime(block.timeOff().startsAt())
            + " to "
            + formatTime(block.timeOff().endsAt()));
    node.setFocusTraversable(handlers.timeOffSelected() != null);
    node.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.PRIMARY && handlers.timeOffSelected() != null) {
            handlers.timeOffSelected().accept(block.timeOff());
            event.consume();
          }
        });
    node.setOnKeyPressed(
        event -> {
          if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)
              && handlers.timeOffSelected() != null) {
            handlers.timeOffSelected().accept(block.timeOff());
            event.consume();
          }
        });
    return node;
  }

  private void layoutTimeOff(Pane eventPane, List<TimeOffPlacement> placements) {
    double width = eventPane.getWidth() > 0 ? eventPane.getWidth() : eventPane.getPrefWidth();
    for (TimeOffPlacement placement : placements) {
      CalendarTimeOffBlock block = placement.block();
      double height =
          Math.max(
              1,
              (block.endMinute() - block.startMinute()) * profile.halfHourHeight() / 30.0
                  - (2 * APPOINTMENT_INSET));
      placement
          .node()
          .resizeRelocate(
              0,
              block.startMinute() * profile.halfHourHeight() / 30.0 + APPOINTMENT_INSET,
              width,
              height);
    }
  }

  /** Scrolls the timeline near the most useful visible minute. */
  void scrollToUsefulTime(LocalDateTime now) {
    int minute;
    if (dates.contains(now.toLocalDate())) {
      minute = Math.max(0, CalendarCalculations.currentMinute(now.toLocalDate(), now) - 60);
    } else {
      minute = earliestVisibleMinute();
    }
    int target = minute;
    Platform.runLater(
        () -> scroll.setVvalue(Math.max(0, Math.min(1, target / (double) (24 * 60)))));
  }

  private int earliestVisibleMinute() {
    for (LocalDate date : dates) {
      int earliest = WorkingInterval.MINUTES_PER_DAY;
      for (CalendarAppointmentBlock block :
          CalendarCalculations.blocksForDay(date, data.appointments())) {
        earliest = Math.min(earliest, block.startMinute());
      }
      for (CalendarTimeOffBlock block :
          CalendarCalculations.timeOffBlocksForDay(date, data.timeOff())) {
        earliest = Math.min(earliest, block.startMinute());
      }
      if (earliest < WorkingInterval.MINUTES_PER_DAY) {
        return Math.max(0, earliest - 30);
      }
      List<WorkingInterval> working = data.settings().intervals(date.getDayOfWeek());
      if (!working.isEmpty()) {
        return working.getFirst().startMinute();
      }
    }
    return 0;
  }

  private void layoutEvents(Pane eventPane, List<EventPlacement> placements) {
    double width = eventPane.getWidth();
    if (width <= 0) {
      width = eventPane.getPrefWidth();
    }
    for (EventPlacement placement : placements) {
      CalendarAppointmentBlock block = placement.block();
      double laneWidth = width / block.laneCount();
      Node node = placement.node();
      double slotHeight =
          Math.max(
              1,
              (block.endMinute() - block.startMinute()) * profile.halfHourHeight() / 30.0
                  - (2 * APPOINTMENT_INSET));
      node.resizeRelocate(
          block.lane() * laneWidth,
          block.startMinute() * profile.halfHourHeight() / 30.0 + APPOINTMENT_INSET,
          laneWidth,
          slotHeight);
    }
  }

  private int clickedMinute(double y) {
    int halfHour = (int) Math.floor(Math.max(0, y) / profile.halfHourHeight());
    return Math.min((HALF_HOURS_PER_DAY - 2) * 30, halfHour * 30);
  }

  private static String formatTime(java.time.LocalDateTime timestamp) {
    return TIME_FORMAT.format(timestamp.toLocalTime());
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

  private static String formatMinute(int minute) {
    return TIME_FORMAT.format(java.time.LocalTime.MIDNIGHT.plusMinutes(minute));
  }

  private Region dayHeader(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    Label name = new Label(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
    name.getStyleClass().add("calendar-day-name");
    Label dateLabel = new Label(DATE_FORMAT.format(date));
    dateLabel.getStyleClass().add("calendar-day-date");
    VBox header = new VBox(2, name, dateLabel);
    header.setId("doctor-calendar-day-header-" + date);
    header.setAlignment(Pos.CENTER);
    header.setMaxWidth(Double.MAX_VALUE);
    header.getStyleClass().add("calendar-day-header");
    if (date.equals(LocalDate.now(clock.withZone(CalendarService.CLINIC_ZONE)))) {
      header.getStyleClass().add("calendar-current-day");
    }
    return header;
  }

  private static Label header(String text, String id) {
    Label label = new Label(text);
    label.setId(id);
    label.getStyleClass().add("calendar-time-header");
    label.setMaxWidth(Double.MAX_VALUE);
    label.setAlignment(Pos.CENTER_LEFT);
    return label;
  }

  private record DayColumn(StackPane surface, List<Region> periods, Region currentLine) {
    // Mutable JavaFX nodes are intentionally held by this private view value.
  }

  private record EventPlacement(Node node, CalendarAppointmentBlock block) {
    // Immutable association used during layout.
  }

  private record TimeOffPlacement(Node node, CalendarTimeOffBlock block) {
    // Immutable association used during layout.
  }

  enum DisplayProfile {
    FULL(100, true, "full"),
    COMPACT(44, false, "compact");

    private final double halfHourHeight;
    private final boolean inlineDecisions;
    private final String styleName;

    DisplayProfile(double halfHourHeight, boolean inlineDecisions, String styleName) {
      this.halfHourHeight = halfHourHeight;
      this.inlineDecisions = inlineDecisions;
      this.styleName = styleName;
    }

    double halfHourHeight() {
      return halfHourHeight;
    }

    boolean inlineDecisions() {
      return inlineDecisions;
    }

    String styleName() {
      return styleName;
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
