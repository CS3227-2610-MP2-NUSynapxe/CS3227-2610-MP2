package nusynapxe.ui;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
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
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.service.CalendarCalculations;

/** Builds day columns and appointment nodes for the scrollable calendar grid. */
final class CalendarTimeGridLayout {
  private static final double APPOINTMENT_INSET = 2;
  private static final int HALF_HOURS_PER_DAY = 48;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private final List<LocalDate> dates;
  private final DoctorCalendarWeek data;
  private final Clock clock;
  private final CalendarTimeGrid.InteractionHandlers handlers;
  private final CalendarTimeGrid.DisplayProfile profile;

  CalendarTimeGridLayout(
      List<LocalDate> dates,
      DoctorCalendarWeek data,
      Clock clock,
      CalendarTimeGrid.InteractionHandlers handlers,
      CalendarTimeGrid.DisplayProfile profile) {
    this.dates = dates;
    this.data = data;
    this.clock = clock;
    this.handlers = handlers;
    this.profile = profile;
  }

  /** Builds the grid center, scroll container, and columns in their display order. */
  Layout build() {
    VBox grid = new VBox();
    grid.setId("doctor-calendar-grid-content");
    grid.getStyleClass().add("calendar-grid-content");
    grid.setMinWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH));
    grid.setPrefWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.DAY_COLUMN_WIDTH));
    grid.setMaxWidth(Double.MAX_VALUE);
    grid.setFillWidth(true);
    grid.setSnapToPixel(false);

    HBox headerRow = new HBox();
    headerRow.setMinWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH));
    headerRow.setPrefWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.DAY_COLUMN_WIDTH));
    headerRow.setMaxWidth(Double.MAX_VALUE);
    headerRow.setSnapToPixel(false);
    Label timeHeader = header("Time", "doctor-calendar-time-header");
    timeHeader.setMinWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
    timeHeader.setPrefWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
    timeHeader.setMaxWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
    headerRow.getChildren().add(timeHeader);
    for (LocalDate date : dates) {
      Region dayHeader = dayHeader(date);
      dayHeader.minWidth(CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH);
      dayHeader.prefWidth(CalendarTimeGrid.DAY_COLUMN_WIDTH);
      dayHeader.maxWidth(Double.MAX_VALUE);
      HBox.setHgrow(dayHeader, Priority.ALWAYS);
      headerRow.getChildren().add(dayHeader);
    }

    VBox timeAxis = new VBox();
    timeAxis.setId("doctor-calendar-time-axis");
    timeAxis.setMinWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
    timeAxis.setPrefWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
    timeAxis.setMaxWidth(CalendarTimeGrid.TIME_AXIS_WIDTH);
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
    bodyRow.setMinWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH));
    bodyRow.setPrefWidth(
        CalendarTimeGrid.TIME_AXIS_WIDTH + (dates.size() * CalendarTimeGrid.DAY_COLUMN_WIDTH));
    bodyRow.setMaxWidth(Double.MAX_VALUE);
    bodyRow.setSnapToPixel(false);
    bodyRow.getChildren().add(timeAxis);
    Map<LocalDate, DayColumn> columns = new LinkedHashMap<>();
    for (LocalDate date : dates) {
      DayColumn column = buildDayColumn(date, data.appointments());
      columns.put(date, column);
      HBox.setHgrow(column.surface(), Priority.ALWAYS);
      bodyRow.getChildren().add(column.surface());
    }
    grid.getChildren().addAll(headerRow, bodyRow);

    ScrollPane scroll = new ScrollPane(grid);
    scroll.setId("doctor-calendar-scroll");
    scroll.setPannable(true);
    scroll.setFitToHeight(false);
    scroll.setFitToWidth(true);
    VBox center = new VBox(8);
    if (data.appointments().isEmpty() && data.timeOff().isEmpty()) {
      center
          .getChildren()
          .add(
              UiComponents.emptyState(
                  "doctor-calendar-empty", "No appointments or blocked time in this date range."));
    }
    center.getChildren().add(scroll);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    return new Layout(center, scroll, columns);
  }

  private DayColumn buildDayColumn(LocalDate day, List<CalendarAppointment> appointments) {
    StackPane surface = new StackPane();
    surface.setId("doctor-calendar-day-column-" + day);
    surface.getStyleClass().add("calendar-day-column");
    surface.setMinWidth(CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH);
    surface.setPrefWidth(CalendarTimeGrid.DAY_COLUMN_WIDTH);
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
    eventPane.setPrefWidth(CalendarTimeGrid.DAY_COLUMN_WIDTH);
    eventPane.setMinWidth(CalendarTimeGrid.MIN_DAY_COLUMN_WIDTH);
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
    String start = CalendarTimeGrid.formatMinute(block.startMinute());
    String end = CalendarTimeGrid.formatMinute(block.endMinute());
    Label time = new Label(start + " – " + end);
    time.getStyleClass().add("calendar-appointment-time");
    Label status = UiComponents.statusBadge(block.appointment().status().name());
    status.getStyleClass().add("calendar-appointment-status");
    if (profile == CalendarTimeGrid.DisplayProfile.COMPACT) {
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
      content.getChildren().addAll(actionSpacer, actions);
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
            + start
            + " to "
            + end
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
    String start = CalendarTimeGrid.formatMinute(block.startMinute());
    String end = CalendarTimeGrid.formatMinute(block.endMinute());
    Label title = new Label("Blocked time");
    title.getStyleClass().add("calendar-time-off-title");
    Label time = new Label(start + " – " + end);
    time.getStyleClass().add("calendar-time-off-time");
    StackPane node = new StackPane(new VBox(2, title, time));
    node.setManaged(false);
    node.setId("doctor-calendar-time-off-" + block.timeOff().id() + "-" + block.day());
    node.getStyleClass().add("calendar-time-off-block");
    node.setAccessibleText("Blocked time, " + start + " to " + end);
    node.setFocusTraversable(handlers.timeOffSelected() != null);
    node.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.PRIMARY) {
            if (handlers.timeOffSelected() != null) {
              handlers.timeOffSelected().accept(block.timeOff());
            }
            event.consume();
          }
        });
    node.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            if (handlers.timeOffSelected() != null) {
              handlers.timeOffSelected().accept(block.timeOff());
            }
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

  private void layoutEvents(Pane eventPane, List<EventPlacement> placements) {
    double width = eventPane.getWidth() > 0 ? eventPane.getWidth() : eventPane.getPrefWidth();
    for (EventPlacement placement : placements) {
      CalendarAppointmentBlock block = placement.block();
      double laneWidth = width / block.laneCount();
      double slotHeight =
          Math.max(
              1,
              (block.endMinute() - block.startMinute()) * profile.halfHourHeight() / 30.0
                  - (2 * APPOINTMENT_INSET));
      placement
          .node()
          .resizeRelocate(
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
    if (date.equals(LocalDate.now(clock))) {
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

  record Layout(VBox center, ScrollPane scroll, Map<LocalDate, DayColumn> columns) {}

  record DayColumn(StackPane surface, List<Region> periods, Region currentLine) {}

  private record EventPlacement(Node node, CalendarAppointmentBlock block) {}

  private record TimeOffPlacement(Node node, CalendarTimeOffBlock block) {}
}
