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
import nusynapxe.domain.CalendarTimeSegment.SegmentKind;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.service.CalendarCalculations;
import nusynapxe.service.CalendarService;

/** Renders the scrollable seven-day time grid used by the Doctor Calendar. */
final class CalendarTimeGrid extends BorderPane {
  /** Height of one 30-minute calendar slot, including room for card content. */
  private static final double HALF_HOUR_HEIGHT = 100;

  private static final double APPOINTMENT_INSET = 2;
  private static final double TIME_AXIS_WIDTH = 44;
  private static final double MIN_DAY_COLUMN_WIDTH = 120;
  private static final double DAY_COLUMN_WIDTH = 168;
  private static final int HALF_HOURS_PER_DAY = 48;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private final CalendarWeek week;
  private final DoctorCalendarWeek data;
  private final Clock clock;
  private final InteractionHandlers handlers;
  private final Map<LocalDate, DayColumn> columns = new LinkedHashMap<>();

  CalendarTimeGrid(CalendarWeek week, DoctorCalendarWeek data, Clock clock) {
    this(week, data, clock, InteractionHandlers.none());
  }

  CalendarTimeGrid(
      CalendarWeek week, DoctorCalendarWeek data, Clock clock, InteractionHandlers handlers) {
    this.week = week;
    this.data = data;
    this.clock = clock;
    this.handlers = Objects.requireNonNull(handlers, "handlers");
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
    VBox grid = new VBox();
    grid.setId("doctor-calendar-grid-content");
    grid.getStyleClass().add("calendar-grid-content");
    grid.setMinWidth(TIME_AXIS_WIDTH + (7 * MIN_DAY_COLUMN_WIDTH));
    grid.setPrefWidth(TIME_AXIS_WIDTH + (7 * DAY_COLUMN_WIDTH));
    grid.setMaxWidth(Double.MAX_VALUE);
    grid.setFillWidth(true);
    grid.setSnapToPixel(false);
    List<LocalDate> dates = week.dates();
    HBox headerRow = new HBox();
    headerRow.setMinWidth(TIME_AXIS_WIDTH + (7 * MIN_DAY_COLUMN_WIDTH));
    headerRow.setPrefWidth(TIME_AXIS_WIDTH + (7 * DAY_COLUMN_WIDTH));
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
      label.setPrefHeight(HALF_HOUR_HEIGHT);
      label.setMinHeight(HALF_HOUR_HEIGHT);
      label.setMaxHeight(HALF_HOUR_HEIGHT);
      label.setAlignment(Pos.TOP_LEFT);
      timeAxis.getChildren().add(label);
    }
    HBox bodyRow = new HBox();
    bodyRow.setMinWidth(TIME_AXIS_WIDTH + (7 * MIN_DAY_COLUMN_WIDTH));
    bodyRow.setPrefWidth(TIME_AXIS_WIDTH + (7 * DAY_COLUMN_WIDTH));
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
    ScrollPane scroll = new ScrollPane(grid);
    scroll.setId("doctor-calendar-scroll");
    scroll.setPannable(true);
    scroll.setFitToHeight(false);
    scroll.setFitToWidth(true);
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
    surface.setMinWidth(MIN_DAY_COLUMN_WIDTH);
    surface.setPrefWidth(DAY_COLUMN_WIDTH);
    surface.setMaxWidth(Double.MAX_VALUE);
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
    eventPane.setPickOnBounds(true);
    eventPane.setPrefHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
    eventPane.setMinHeight(HALF_HOUR_HEIGHT * HALF_HOURS_PER_DAY);
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
    eventPane
        .widthProperty()
        .addListener((observable, previous, current) -> layoutEvents(eventPane, placements));
    eventPane.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.PRIMARY && handlers.emptySlot() != null) {
            handlers
                .emptySlot()
                .accept(day.atStartOfDay().plusMinutes(clickedMinute(event.getY())));
          }
        });
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
    content.getChildren().addAll(patient, time, status);
    AppointmentStatus appointmentStatus = block.appointment().status();
    if (handlers.decision() != null
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

  private static void layoutEvents(Pane eventPane, List<EventPlacement> placements) {
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
              (block.endMinute() - block.startMinute()) * HALF_HOUR_HEIGHT / 30.0
                  - (2 * APPOINTMENT_INSET));
      node.resizeRelocate(
          block.lane() * laneWidth,
          block.startMinute() * HALF_HOUR_HEIGHT / 30.0 + APPOINTMENT_INSET,
          laneWidth,
          slotHeight);
    }
  }

  private static int clickedMinute(double y) {
    int halfHour = (int) Math.floor(Math.max(0, y) / HALF_HOUR_HEIGHT);
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

  /** Callbacks supplied by the owning Doctor Calendar page. */
  record InteractionHandlers(
      Consumer<CalendarAppointment> selected,
      BiConsumer<CalendarAppointment, AppointmentStatus> decision,
      Consumer<LocalDateTime> emptySlot) {
    static InteractionHandlers none() {
      return new InteractionHandlers(null, null, null);
    }
  }
}
