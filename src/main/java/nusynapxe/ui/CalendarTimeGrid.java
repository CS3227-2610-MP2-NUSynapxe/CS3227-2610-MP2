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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarAppointmentBlock;
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.service.CalendarCalculations;
import nusynapxe.service.CalendarService;

/** Renders the scrollable seven-day time grid used by the Doctor Calendar. */
final class CalendarTimeGrid extends BorderPane {
  private static final double HALF_HOUR_HEIGHT = 32;
  private static final double TIME_AXIS_WIDTH = 76;
  private static final double DAY_COLUMN_WIDTH = 168;
  private static final int HALF_HOURS_PER_DAY = 48;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private final CalendarWeek week;
  private final DoctorCalendarWeek data;
  private final Clock clock;
  private final Map<LocalDate, DayColumn> columns = new LinkedHashMap<>();

  CalendarTimeGrid(CalendarWeek week, DoctorCalendarWeek data, Clock clock) {
    this.week = week;
    this.data = data;
    this.clock = clock;
    setId("doctor-calendar-time-grid");
    getStyleClass().add("calendar-time-grid");
    build();
    updateCurrentTime(LocalDateTime.now(clock));
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
        column.currentLine.setTranslateY(currentMinute * HALF_HOUR_HEIGHT / 30.0);
      }
    }
  }

  private void build() {
    GridPane grid = new GridPane();
    grid.setId("doctor-calendar-grid-content");
    grid.getStyleClass().add("calendar-grid-content");
    grid.getColumnConstraints().add(new ColumnConstraints(TIME_AXIS_WIDTH));
    for (int index = 0; index < 7; index++) {
      grid.getColumnConstraints().add(new ColumnConstraints(DAY_COLUMN_WIDTH));
    }
    grid.add(header("Time", "doctor-calendar-time-header"), 0, 0);
    List<LocalDate> dates = week.dates();
    for (int index = 0; index < dates.size(); index++) {
      grid.add(dayHeader(dates.get(index)), index + 1, 0);
    }
    VBox timeAxis = new VBox();
    timeAxis.setId("doctor-calendar-time-axis");
    timeAxis.setPrefWidth(TIME_AXIS_WIDTH);
    for (int index = 0; index < HALF_HOURS_PER_DAY; index++) {
      Label label =
          new Label(TIME_FORMAT.format(java.time.LocalTime.MIDNIGHT.plusMinutes(index * 30L)));
      label.setId("doctor-calendar-time-label-" + index);
      label.getStyleClass().add("calendar-time-label");
      label.setPrefHeight(HALF_HOUR_HEIGHT);
      label.setMinHeight(HALF_HOUR_HEIGHT);
      label.setMaxHeight(HALF_HOUR_HEIGHT);
      label.setAlignment(Pos.TOP_RIGHT);
      timeAxis.getChildren().add(label);
    }
    grid.add(timeAxis, 0, 1);
    for (int index = 0; index < dates.size(); index++) {
      DayColumn column = buildDayColumn(dates.get(index), data.appointments());
      columns.put(dates.get(index), column);
      grid.add(column.surface, index + 1, 1);
    }
    ScrollPane scroll = new ScrollPane(grid);
    scroll.setId("doctor-calendar-scroll");
    scroll.setPannable(true);
    scroll.setFitToHeight(false);
    scroll.setFitToWidth(false);
    VBox center = new VBox(8);
    if (data.appointments().isEmpty()) {
      Label empty = UiComponents.emptyState("doctor-calendar-empty", "No appointments this week.");
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
    surface.setMinWidth(DAY_COLUMN_WIDTH);
    surface.setPrefWidth(DAY_COLUMN_WIDTH);
    surface.setMinHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    surface.setPrefHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    VBox periods = new VBox();
    periods.setMouseTransparent(true);
    periods.setPrefHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    List<Region> periodCells = new ArrayList<>();
    for (int index = 0; index < HALF_HOURS_PER_DAY; index++) {
      Region period = new Region();
      period.setId("doctor-calendar-period-" + day + "-" + index);
      period.getStyleClass().add("calendar-period");
      period.setPrefHeight(HALF_HOUR_HEIGHT);
      period.setMinHeight(HALF_HOUR_HEIGHT);
      period.setMaxHeight(HALF_HOUR_HEIGHT);
      periods.getChildren().add(period);
      periodCells.add(period);
    }
    Pane eventPane = new Pane();
    eventPane.setId("doctor-calendar-events-" + day);
    eventPane.setMouseTransparent(true);
    eventPane.setPrefHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    eventPane.setMinHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    eventPane.setPrefWidth(DAY_COLUMN_WIDTH);
    List<EventPlacement> placements = new ArrayList<>();
    for (CalendarAppointmentBlock block : CalendarCalculations.blocksForDay(day, appointments)) {
      Node node = appointmentNode(block);
      eventPane.getChildren().add(node);
      placements.add(new EventPlacement(node, block));
    }
    eventPane
        .widthProperty()
        .addListener((observable, previous, current) -> layoutEvents(eventPane, placements));
    layoutEvents(eventPane, placements);
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
    Label patient = new Label(block.appointment().patientDisplayName());
    patient.getStyleClass().add("calendar-appointment-patient");
    patient.setWrapText(true);
    Label time =
        new Label(
            formatTime(block.appointment().startsAt())
                + " – "
                + formatTime(block.appointment().endsAt()));
    time.getStyleClass().add("calendar-appointment-time");
    Label status = UiComponents.statusBadge(block.appointment().status().name());
    status.getStyleClass().add("calendar-appointment-status");
    content.getChildren().addAll(patient, time, status);
    StackPane blockNode = new StackPane(content);
    blockNode.setId(
        "doctor-calendar-appointment-" + block.appointment().appointmentId() + "-" + block.day());
    blockNode.getStyleClass().add("calendar-appointment-block");
    blockNode
        .getStyleClass()
        .add(
            "calendar-appointment-status-"
                + block.appointment().status().name().toLowerCase(Locale.ROOT).replace('_', '-'));
    if (block.appointment().status() == nusynapxe.domain.AppointmentStatus.CANCELLED) {
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
    return blockNode;
  }

  private static void layoutEvents(Pane eventPane, List<EventPlacement> placements) {
    double width = eventPane.getWidth();
    if (width <= 0) {
      width = eventPane.getPrefWidth();
    }
    for (EventPlacement placement : placements) {
      CalendarAppointmentBlock block = placement.block();
      double laneWidth = width / block.laneCount();
      double eventWidth = Math.max(46, laneWidth - 8);
      Node node = placement.node();
      node.resizeRelocate(
          block.lane() * laneWidth + 4,
          block.startMinute() * HALF_HOUR_HEIGHT / 30.0 + 2,
          eventWidth,
          Math.max(24, (block.endMinute() - block.startMinute()) * HALF_HOUR_HEIGHT / 30.0 - 4));
    }
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

  private Node dayHeader(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    Label name = new Label(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
    name.getStyleClass().add("calendar-day-name");
    Label dateLabel = new Label(DATE_FORMAT.format(date));
    dateLabel.getStyleClass().add("calendar-day-date");
    VBox header = new VBox(2, name, dateLabel);
    header.setId("doctor-calendar-day-header-" + date);
    header.setAlignment(Pos.CENTER);
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
    label.setAlignment(Pos.CENTER_RIGHT);
    return label;
  }

  private record DayColumn(StackPane surface, List<Region> periods, Region currentLine) {
    // Mutable JavaFX nodes are intentionally held by this private view value.
  }

  private record EventPlacement(Node node, CalendarAppointmentBlock block) {
    // Immutable association used during layout.
  }
}
