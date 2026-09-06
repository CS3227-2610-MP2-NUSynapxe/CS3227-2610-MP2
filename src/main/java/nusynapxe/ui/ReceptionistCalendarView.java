package nusynapxe.ui;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import javafx.scene.layout.VBox;
import nusynapxe.domain.Account;
import nusynapxe.domain.CalendarAppointment;
import nusynapxe.domain.DoctorCalendarWeek;
import nusynapxe.domain.Session;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.CalendarScheduleCalculations;
import nusynapxe.service.CalendarService;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Read-only Doctor schedule calendar used by Receptionists to choose booking slots. */
final class ReceptionistCalendarView {
  private static final long MAX_RANGE_DAYS = 31;
  private static final String WEEK_MODE = "Week";
  private static final String SCHEDULE_MODE = "Schedule";

  private final ClinicServices services;
  private final Session session;
  private final Label feedback;
  private final BiConsumer<Account, LocalDateTime> onSlotSelected;
  private final Consumer<CalendarAppointment> onAppointmentSelected;
  private final Clock clock = Clock.system(CalendarService.CLINIC_ZONE);
  private final SearchSuggestionField<Account> doctor;
  private final DatePicker from;
  private final DatePicker to;
  private final VBox fromField;
  private final VBox toField;
  private final Button previous;
  private final Button next;
  private final ComboBox<String> viewMode;
  private final BorderPane root;
  private final VBox page;
  private LocalDate scheduleAnchor;
  private CalendarScheduleList scheduleList;

  ReceptionistCalendarView(
      ClinicServices services,
      Session session,
      Label feedback,
      BiConsumer<Account, LocalDateTime> onSlotSelected,
      Consumer<CalendarAppointment> onAppointmentSelected) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.onSlotSelected = Objects.requireNonNull(onSlotSelected, "onSlotSelected");
    this.onAppointmentSelected =
        Objects.requireNonNull(onAppointmentSelected, "onAppointmentSelected");
    doctor =
        new SearchSuggestionField<>(
            "reception-calendar-doctor",
            "Search Doctor by name or username",
            ReceptionistCalendarView::doctorLabel,
            account -> account.displayName() + " " + account.username());
    LocalDate today = LocalDate.now(clock);
    scheduleAnchor = CalendarScheduleCalculations.today(clock);
    from = new DatePicker(today);
    from.setId("reception-calendar-from");
    from.setShowWeekNumbers(false);
    to = new DatePicker(today.plusDays(6));
    to.setId("reception-calendar-to");
    to.setShowWeekNumbers(false);
    fromField = UiComponents.fieldGroup("From", from);
    toField = UiComponents.fieldGroup("To", to);
    previous = UiComponents.secondaryButton("‹", "reception-calendar-previous");
    previous.setAccessibleText("Previous");
    next = UiComponents.secondaryButton("›", "reception-calendar-next");
    next.setAccessibleText("Next");
    viewMode = UiComponents.compactSelector();
    root = buildRoot();
    page =
        new VBox(
            12,
            UiComponents.pageTitle("Calendar"),
            UiComponents.supportingText(
                "Choose a Doctor and date range, then click an available time to book."),
            root);
    page.setId("reception-calendar-page");
    VBox.setVgrow(root, Priority.ALWAYS);
    doctor.valueProperty().addListener((observable, previousValue, selected) -> refresh());
    from.setOnAction(event -> refresh());
    to.setOnAction(event -> refresh());
    applyModeVisibility();
    refreshDoctors();
    refresh();
  }

  Parent view() {
    return page;
  }

  void refreshDoctors() {
    try {
      Account previousDoctor = doctor.getValue();
      doctor.setItems(services.accountService().listDoctors(session));
      if (previousDoctor != null) {
        doctor.getItems().stream()
            .filter(account -> account.id() == previousDoctor.id())
            .findFirst()
            .ifPresent(doctor::select);
      } else if (!doctor.getItems().isEmpty()) {
        doctor.select(doctor.getItems().getFirst());
      }
    } catch (SQLException | AuthorizationException exception) {
      UiComponents.showError(feedback, "Doctors are temporarily unavailable");
    }
  }

  void refresh() {
    Account selected = doctor.getValue();
    if (selected == null) {
      disposeScheduleList();
      root.setCenter(
          UiComponents.emptyState(
              "reception-calendar-select-doctor", "Select a Doctor to view available slots."));
      return;
    }
    try {
      if (isScheduleMode()) {
        disposeScheduleList();
        scheduleList =
            new CalendarScheduleList(
                scheduleLoader(selected.id()), scheduleAnchor, clock, onAppointmentSelected);
        scheduleList.setId("reception-calendar-schedule-list");
        root.setCenter(scheduleList);
      } else {
        disposeScheduleList();
        List<LocalDate> dates = selectedDates();
        DoctorCalendarWeek data =
            services
                .calendarService()
                .getReceptionistRange(session, selected.id(), from.getValue(), to.getValue());
        CalendarTimeGrid grid =
            new CalendarTimeGrid(
                dates,
                data,
                clock,
                new CalendarTimeGrid.InteractionHandlers(
                    onAppointmentSelected, null, start -> onSlotSelected.accept(selected, start)));
        grid.setId("reception-calendar-time-grid");
        root.setCenter(grid);
      }
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      UiComponents.showError(
          feedback,
          exception.getMessage() == null
              ? "Calendar is temporarily unavailable"
              : exception.getMessage());
    }
  }

  private BorderPane buildRoot() {
    Button today = UiComponents.secondaryButton("Today", "reception-calendar-today");
    today.setOnAction(event -> goToToday());
    previous.setOnAction(event -> goToPrevious());
    next.setOnAction(event -> goToNext());
    VBox doctorField = UiComponents.fieldGroup("Doctor", doctor);
    fromField.setPrefWidth(190);
    toField.setPrefWidth(190);
    doctorField.setId("reception-calendar-doctor-field");
    doctorField.setMinWidth(300);
    doctorField.setPrefWidth(360);
    doctorField.setMaxWidth(420);
    doctor.setMinWidth(300);
    doctor.setPrefWidth(360);
    viewMode.setId("reception-calendar-view-mode");
    viewMode.setAccessibleText("Choose Calendar view");
    viewMode.getItems().addAll(WEEK_MODE, SCHEDULE_MODE);
    viewMode.setEditable(false);
    viewMode.setValue(WEEK_MODE);
    viewMode.setOnAction(event -> changeMode());
    HBox toolbar = new HBox(12, today, previous, next, fromField, toField, doctorField, viewMode);
    toolbar.setAlignment(Pos.BOTTOM_LEFT);
    toolbar.getStyleClass().add("calendar-toolbar");
    VBox heading = new VBox(4, UiComponents.sectionHeading("Doctor schedule"), toolbar);
    heading.setPadding(new Insets(0, 0, 12, 0));
    BorderPane page = new BorderPane();
    page.setId("reception-calendar-card");
    page.getStyleClass().add("calendar-page");
    page.setTop(heading);
    return page;
  }

  private void goToToday() {
    if (isScheduleMode()) {
      scheduleAnchor = CalendarScheduleCalculations.today(clock);
    } else {
      LocalDate current = LocalDate.now(clock);
      from.setValue(current);
      to.setValue(current.plusDays(6));
    }
    refresh();
  }

  private void goToPrevious() {
    scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, -1);
    refresh();
  }

  private void goToNext() {
    scheduleAnchor = CalendarScheduleCalculations.moveAnchor(scheduleAnchor, 1);
    refresh();
  }

  private void changeMode() {
    applyModeVisibility();
    refresh();
  }

  private void applyModeVisibility() {
    boolean scheduleMode = isScheduleMode();
    previous.setVisible(scheduleMode);
    previous.setManaged(scheduleMode);
    next.setVisible(scheduleMode);
    next.setManaged(scheduleMode);
    fromField.setVisible(!scheduleMode);
    fromField.setManaged(!scheduleMode);
    toField.setVisible(!scheduleMode);
    toField.setManaged(!scheduleMode);
  }

  private boolean isScheduleMode() {
    return SCHEDULE_MODE.equals(viewMode.getValue());
  }

  @SuppressWarnings("PMD.NullAssignment")
  private void disposeScheduleList() {
    if (scheduleList != null) {
      scheduleList.dispose();
      scheduleList = null;
    }
  }

  private CalendarSchedulePageLoader scheduleLoader(long doctorId) {
    return (anchor, cursor, pageSize) ->
        services
            .calendarService()
            .getReceptionistSchedulePage(session, doctorId, anchor, cursor, pageSize);
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

  private static String doctorLabel(Account account) {
    String displayName = account.displayName() == null ? "" : account.displayName().strip();
    return displayName.isBlank() ? account.username() : displayName + " · " + account.username();
  }
}
