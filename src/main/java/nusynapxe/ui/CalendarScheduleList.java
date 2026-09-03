package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import nusynapxe.domain.AppointmentStatus;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarScheduleGroup;
import nusynapxe.domain.CalendarSchedulePage;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarScheduleCalculations;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Displays a Doctor's future appointments as an append-only, lazy schedule list. */
final class CalendarScheduleList extends BorderPane {
  private static final double LOAD_THRESHOLD = 0.82;
  private static final DateTimeFormatter DATE_HEADER_FORMATTER =
      DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

  private final CalendarSchedulePageLoader pageLoader;
  private final Clock clock;
  private final ListView<ScheduleEntry> list = new ListView<>();
  private final ObservableList<ScheduleEntry> entries = FXCollections.observableArrayList();
  private final Label loading = new Label("Loading more appointments…");
  private final Label end = new Label("You have reached the end of the schedule.");
  private final Label error = new Label();
  private final Button retry =
      UiComponents.secondaryButton("Retry", "doctor-calendar-schedule-retry");
  private final VBox state = new VBox(8);
  private final ChangeListener<Scene> sceneListener;
  private final ChangeListener<Number> scrollListener;
  private LocalDate anchor;
  private CalendarScheduleCursor cursor;
  private LocalDateTime now;
  private ScrollBar scrollBar;
  private boolean hasMore;
  private boolean loadingPage;
  private boolean errorVisible;
  private boolean bottomLoadArmed;
  private boolean disposed;

  CalendarScheduleList(ClinicServices services, Session session, LocalDate anchor, Clock clock) {
    this(loaderFor(services, session), anchor, clock);
  }

  CalendarScheduleList(CalendarSchedulePageLoader pageLoader, LocalDate anchor, Clock clock) {
    this.pageLoader = Objects.requireNonNull(pageLoader, "pageLoader");
    this.clock = Objects.requireNonNull(clock, "clock").withZone(CalendarService.CLINIC_ZONE);
    this.now = LocalDateTime.now(this.clock);
    sceneListener =
        (observable, oldScene, newScene) -> {
          if (newScene == null) {
            removeScrollListener();
          } else {
            Platform.runLater(this::installScrollListener);
          }
        };
    scrollListener =
        (observable, oldValue, newValue) -> onScrollPositionChanged(newValue.doubleValue());
    configureList();
    configureState();
    setCenter(list);
    setBottom(state);
    reset(anchor);
    list.sceneProperty().addListener(sceneListener);
  }

  /** Re-anchors the stream and discards pages from the previous anchor. */
  @SuppressWarnings("PMD.NullAssignment")
  void reset(LocalDate newAnchor) {
    if (disposed) {
      return;
    }
    anchor = Objects.requireNonNull(newAnchor, "newAnchor");
    cursor = null;
    hasMore = true;
    loadingPage = false;
    errorVisible = false;
    bottomLoadArmed = true;
    entries.clear();
    list.scrollTo(0);
    updateState();
    loadNextPage();
    Platform.runLater(this::installScrollListener);
  }

  /** Refreshes current-date and elapsed-row styling using the supplied clock. */
  void updateCurrentTime(LocalDateTime currentTime) {
    if (disposed) {
      return;
    }
    now = Objects.requireNonNull(currentTime, "currentTime");
    list.refresh();
  }

  /** Releases listeners and page-owned state when the parent Calendar leaves the scene. */
  @SuppressWarnings("PMD.NullAssignment")
  void dispose() {
    if (disposed) {
      return;
    }
    disposed = true;
    loadingPage = false;
    cursor = null;
    entries.clear();
    removeScrollListener();
    list.sceneProperty().removeListener(sceneListener);
  }

  private void configureList() {
    list.setId("doctor-calendar-schedule-list");
    list.setAccessibleText("Chronological future appointments");
    list.getStyleClass().add("calendar-schedule-list");
    list.setPlaceholder(
        UiComponents.emptyState(
            "doctor-calendar-schedule-empty", "No future appointments are scheduled."));
    list.setCellFactory(view -> createCell());
    list.addEventFilter(ScrollEvent.SCROLL, event -> handleFallbackScroll(event.getDeltaY()));
  }

  private void configureState() {
    loading.setId("doctor-calendar-schedule-loading");
    loading.getStyleClass().add("calendar-schedule-loading");
    loading.setAccessibleText("Loading more schedule appointments");
    end.setId("doctor-calendar-schedule-end");
    end.getStyleClass().add("calendar-schedule-end");
    end.setAccessibleText("End of future appointments");
    error.setId("doctor-calendar-schedule-error");
    error.getStyleClass().add("calendar-schedule-error");
    error.setWrapText(true);
    error.setAccessibleText("Schedule loading error");
    retry.setAccessibleText("Retry loading schedule appointments");
    retry.setOnAction(event -> loadNextPage());
    state.getStyleClass().add("calendar-schedule-state");
    state.setPadding(new Insets(8, 0, 0, 0));
    state.setAlignment(Pos.CENTER_LEFT);
    state.getChildren().addAll(loading, end, error, retry);
    setVisibleAndManaged(loading, false);
    setVisibleAndManaged(end, false);
    setVisibleAndManaged(error, false);
    setVisibleAndManaged(retry, false);
  }

  private ListCell<ScheduleEntry> createCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(ScheduleEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        getStyleClass().removeIf(style -> style.startsWith("calendar-schedule-"));
        setText(null);
        setGraphic(null);
        setAccessibleText(null);
        setId(null);
        if (empty || entry == null) {
          return;
        }
        if (entry instanceof DateEntry dateEntry) {
          renderDate(this, dateEntry.date());
        } else if (entry instanceof AppointmentEntry appointmentEntry) {
          renderAppointment(this, appointmentEntry.appointment());
        }
      }
    };
  }

  private void renderDate(ListCell<ScheduleEntry> cell, LocalDate date) {
    boolean today = date.equals(now.toLocalDate());
    Label dayNumber = new Label(Integer.toString(date.getDayOfMonth()));
    dayNumber.getStyleClass().add("calendar-schedule-day-number");
    String dateText = DATE_HEADER_FORMATTER.format(date);
    Label dayName = new Label(today ? "Today · " + dateText : dateText);
    dayName.getStyleClass().add("calendar-schedule-date-label");
    HBox header = new HBox(12, dayNumber, dayName);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("calendar-schedule-date-header");
    cell.setGraphic(header);
    cell.setId("doctor-calendar-schedule-date-" + date);
    cell.setAccessibleText((today ? "Today, " : "") + DATE_HEADER_FORMATTER.format(date));
    cell.getStyleClass().add("calendar-schedule-date-header");
    if (today) {
      cell.getStyleClass().add("calendar-schedule-today");
    }
  }

  private void renderAppointment(ListCell<ScheduleEntry> cell, CalendarAppointment appointment) {
    String status = appointment.status().name().toLowerCase(Locale.ROOT).replace('_', '-');
    Region marker = new Region();
    marker.setMinSize(10, 10);
    marker.setPrefSize(10, 10);
    marker.setMaxSize(10, 10);
    marker.getStyleClass().add("calendar-schedule-status-marker");
    marker.getStyleClass().add("calendar-schedule-status-" + status);

    Label time = new Label(CalendarScheduleCalculations.formatTimeRange(appointment));
    time.getStyleClass().add("calendar-schedule-time");
    Label patient = new Label(appointment.patientDisplayName());
    patient.getStyleClass().add("calendar-schedule-patient");
    patient.setWrapText(true);
    HBox.setHgrow(patient, Priority.ALWAYS);
    Label statusBadge = UiComponents.statusBadge(appointment.status().name());
    statusBadge.setId("doctor-calendar-schedule-status-" + appointment.appointmentId());
    statusBadge.getStyleClass().add("calendar-schedule-status");

    HBox row = new HBox(10, marker, time, patient, statusBadge);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("calendar-schedule-appointment-row");
    boolean elapsed = CalendarScheduleCalculations.isElapsed(appointment, now);
    if (elapsed) {
      Label past = new Label("Past");
      past.getStyleClass().add("calendar-schedule-past-cue");
      row.getChildren().add(past);
    }
    cell.setGraphic(row);
    cell.setId("doctor-calendar-schedule-appointment-" + appointment.appointmentId());
    cell.setAccessibleText(appointmentAccessibleText(appointment));
    cell.getStyleClass().add("calendar-schedule-appointment-row");
    cell.getStyleClass().add("calendar-schedule-status-" + status);
    if (appointment.status() == AppointmentStatus.CANCELLED) {
      cell.getStyleClass().add("calendar-schedule-cancelled");
    }
    if (elapsed) {
      cell.getStyleClass().add("calendar-schedule-elapsed");
    }
  }

  private String appointmentAccessibleText(CalendarAppointment appointment) {
    String text =
        appointment.patientDisplayName()
            + ", "
            + CalendarScheduleCalculations.formatTimeRange(appointment)
            + ", "
            + UiComponents.humanizeStatus(appointment.status().name());
    if (CalendarScheduleCalculations.isElapsed(appointment, now)) {
      text += ", past appointment";
    }
    if (!appointment.startsAt().toLocalDate().equals(appointment.endsAt().toLocalDate())) {
      text += ", crosses midnight";
    }
    return text;
  }

  private void loadNextPage() {
    if (disposed || loadingPage || !hasMore) {
      return;
    }
    loadingPage = true;
    errorVisible = false;
    updateState();
    try {
      CalendarSchedulePage page =
          pageLoader.load(anchor, cursor, CalendarSchedulePage.DEFAULT_PAGE_SIZE);
      append(page);
      cursor = page.nextCursor();
      hasMore = page.hasMore();
      errorVisible = false;
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      errorVisible = true;
      error.setText(
          exception.getMessage() == null
              ? "The schedule could not be loaded."
              : "The schedule could not be loaded: " + exception.getMessage());
    } finally {
      loadingPage = false;
      updateState();
    }
  }

  private void append(CalendarSchedulePage page) {
    LocalDate lastDate = lastLoadedDate();
    for (CalendarScheduleGroup group :
        CalendarScheduleCalculations.groupByDate(page.appointments())) {
      if (!group.date().equals(lastDate)) {
        entries.add(new DateEntry(group.date()));
      }
      for (CalendarAppointment appointment : group.appointments()) {
        entries.add(new AppointmentEntry(appointment));
      }
      lastDate = group.date();
    }
    list.setItems(entries);
  }

  private LocalDate lastLoadedDate() {
    for (int index = entries.size() - 1; index >= 0; index--) {
      ScheduleEntry entry = entries.get(index);
      if (entry instanceof DateEntry dateEntry) {
        return dateEntry.date();
      }
    }
    return null;
  }

  private void updateState() {
    setVisibleAndManaged(loading, loadingPage);
    setVisibleAndManaged(error, errorVisible);
    setVisibleAndManaged(retry, errorVisible);
    setVisibleAndManaged(end, !loadingPage && !errorVisible && !entries.isEmpty() && !hasMore);
  }

  private void installScrollListener() {
    if (disposed || list.getScene() == null) {
      return;
    }
    list.applyCss();
    Node candidate = list.lookup(".scroll-bar:vertical");
    if (candidate instanceof ScrollBar candidateBar && candidateBar != scrollBar) {
      removeScrollListener();
      scrollBar = candidateBar;
      scrollBar.valueProperty().addListener(scrollListener);
      bottomLoadArmed = scrollBar.getValue() < LOAD_THRESHOLD;
    }
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void removeScrollListener() {
    if (scrollBar != null) {
      scrollBar.valueProperty().removeListener(scrollListener);
      scrollBar = null;
    }
  }

  private void onScrollPositionChanged(double value) {
    if (value < LOAD_THRESHOLD) {
      bottomLoadArmed = true;
    } else if (bottomLoadArmed) {
      bottomLoadArmed = false;
      loadNextPage();
    }
  }

  private void handleFallbackScroll(double deltaY) {
    if (scrollBar != null) {
      return;
    }
    if (deltaY > 0) {
      bottomLoadArmed = true;
    } else if (deltaY < 0 && bottomLoadArmed) {
      bottomLoadArmed = false;
      Platform.runLater(this::loadNextPage);
    }
  }

  private static void setVisibleAndManaged(Node node, boolean visible) {
    node.setVisible(visible);
    node.setManaged(visible);
  }

  private static CalendarSchedulePageLoader loaderFor(ClinicServices services, Session session) {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(session, "session");
    return (anchor, cursor, pageSize) ->
        services.calendarService().getSchedulePage(session, anchor, cursor, pageSize);
  }

  private interface ScheduleEntry {
    // Marker interface for date headers and appointment rows.
  }

  private record DateEntry(LocalDate date) implements ScheduleEntry {
    // Date header entry.
  }

  private record AppointmentEntry(CalendarAppointment appointment) implements ScheduleEntry {
    // Appointment row entry.
  }
}
