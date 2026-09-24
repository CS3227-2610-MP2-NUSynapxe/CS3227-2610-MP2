package nusynapxe.ui;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Session;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Doctor-owned Calendar working-hours settings page. */
public final class DoctorCalendarSettingsView {
  private static final String SETTINGS_ID_PREFIX = "doctor-calendar-settings-";
  private static final List<String> START_OPTIONS = timeOptions(0, 1410);
  private static final List<String> END_OPTIONS = timeOptions(30, WorkingInterval.MINUTES_PER_DAY);

  private final ClinicServices services;
  private final Session session;
  private final Runnable onBack;
  private final Runnable onSaved;
  private final Label feedback;
  private final ClinicTaskRunner taskRunner;
  private final Map<DayOfWeek, DayEditor> dayEditors = new EnumMap<>(DayOfWeek.class);
  private final BorderPane root;
  private VBox daysContainer;
  private Button saveButton;
  private Button backButton;
  private Button cancelButton;
  private DayOfWeek firstDayOfWeek = DayOfWeek.SUNDAY;
  private boolean settingsLoaded;
  private long operationGeneration;
  private boolean disposed;

  /**
   * Creates a Calendar settings page for one authenticated Doctor.\n *
   *
   * @param services application services used to load and save settings
   * @param session authenticated Doctor session
   * @param onBack callback used to return to the Calendar
   * @param onSaved callback invoked after settings are saved
   * @param feedback label used for user-facing operation messages
   * @throws NullPointerException if an argument is {@code null}
   */
  public DoctorCalendarSettingsView(
      ClinicServices services, Session session, Runnable onBack, Runnable onSaved, Label feedback) {
    this(services, session, onBack, onSaved, feedback, ClinicTaskRunner.immediate());
  }

  DoctorCalendarSettingsView(
      ClinicServices services,
      Session session,
      Runnable onBack,
      Runnable onSaved,
      Label feedback,
      ClinicTaskRunner taskRunner) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.onBack = Objects.requireNonNull(onBack, "onBack");
    this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    root = buildRoot();
    reload();
  }

  /**
   * Returns the settings page node.
   *
   * @return root node for the Calendar settings page
   */
  public Parent view() {
    return root;
  }

  /** Prevents later database callbacks from mutating this settings page. */
  public void dispose() {
    disposed = true;
    operationGeneration++;
  }

  /** Reloads persisted settings into the editable draft. */
  public void reload() {
    if (disposed) {
      return;
    }
    operationGeneration++;
    long generation = operationGeneration;
    if (daysContainer != null) {
      daysContainer.setDisable(true);
    }
    taskRunner.submit(
        () -> services.calendarService().getSettings(session),
        settings -> {
          if (disposed || generation != operationGeneration) {
            return;
          }
          populate(settings);
          settingsLoaded = true;
          if (daysContainer != null) {
            daysContainer.setDisable(false);
          }
          if (saveButton != null) {
            saveButton.setDisable(false);
          }
        },
        failure -> {
          if (disposed || generation != operationGeneration) {
            return;
          }
          settingsLoaded = false;
          if (daysContainer != null) {
            daysContainer.setDisable(false);
          }
          if (saveButton != null) {
            saveButton.setDisable(true);
          }
          feedback.setText(userMessage(failure, "Calendar settings are temporarily unavailable"));
        });
  }

  private BorderPane buildRoot() {
    backButton = UiComponents.secondaryButton("Back to Calendar", "doctor-calendar-settings-back");
    backButton.setAccessibleText("Return to Calendar");
    backButton.setOnAction(event -> onBack.run());
    HBox toolbar = new HBox(12, backButton, UiComponents.pageTitle("Calendar settings"));
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("calendar-settings-toolbar");

    VBox days = new VBox(10);
    days.setId("doctor-calendar-settings-days");
    days.getStyleClass().add("calendar-settings-days");
    days.setDisable(true);
    daysContainer = days;
    for (DayOfWeek day : DayOfWeek.values()) {
      DayEditor editor = new DayEditor(day);
      dayEditors.put(day, editor);
      days.getChildren().add(editor.view());
    }
    VBox workingHours =
        UiComponents.card(
            "doctor-calendar-settings-working-hours",
            UiComponents.sectionHeading("Work hours"),
            UiComponents.supportingText(
                "Add another interval to create a break, such as a lunch break."),
            timezoneRow(),
            days);

    saveButton = UiComponents.primaryButton("Save settings", "doctor-calendar-settings-save");
    saveButton.setAccessibleText("Save Calendar settings");
    saveButton.setDisable(true);
    saveButton.setOnAction(event -> save());
    cancelButton = UiComponents.secondaryButton("Cancel", "doctor-calendar-settings-cancel");
    cancelButton.setAccessibleText("Cancel Calendar setting edits");
    cancelButton.setOnAction(event -> onBack.run());
    HBox actions = UiComponents.actionBar(saveButton, cancelButton);

    VBox content = new VBox(16, workingHours, actions);
    content.setPadding(new Insets(0, 4, 24, 4));
    ScrollPane scroll = new ScrollPane(content);
    scroll.setId("doctor-calendar-settings-scroll");
    scroll.setFitToWidth(true);

    BorderPane page = new BorderPane();
    page.setId("doctor-calendar-settings-page");
    page.getStyleClass().add("calendar-settings-page");
    page.setPadding(new Insets(4, 0, 0, 0));
    page.setTop(toolbar);
    page.setCenter(scroll);
    return page;
  }

  private Node timezoneRow() {
    Label timezone = new Label("Timezone: Asia/Singapore (UTC+08:00)");
    timezone.setId("doctor-calendar-settings-timezone");
    timezone.getStyleClass().add("supporting-text");
    return timezone;
  }

  private void populate(DoctorCalendarSettings settings) {
    firstDayOfWeek = settings.firstDayOfWeek();
    for (DayOfWeek day : DayOfWeek.values()) {
      dayEditors.get(day).populate(settings.intervals(day));
    }
  }

  private void save() {
    if (!settingsLoaded || (saveButton != null && saveButton.isDisable())) {
      if (!settingsLoaded) {
        feedback.setText("Calendar settings are not loaded");
      }
      return;
    }
    try {
      Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
      for (DayOfWeek day : DayOfWeek.values()) {
        intervals.put(day, dayEditors.get(day).readIntervals());
      }
      DoctorCalendarSettings settings =
          new DoctorCalendarSettings(session.accountId(), firstDayOfWeek, intervals);
      operationGeneration++;
      long generation = operationGeneration;
      setSaving(true);
      taskRunner.submit(
          () -> {
            services.calendarService().saveSettings(session, settings);
            return null;
          },
          ignored -> {
            if (disposed || generation != operationGeneration) {
              return;
            }
            setSaving(false);
            feedback.setText("Calendar settings saved");
            onSaved.run();
          },
          failure -> {
            if (!disposed && generation == operationGeneration) {
              setSaving(false);
              feedback.setText(userMessage(failure, "Calendar settings could not be saved"));
            }
          });
    } catch (ValidationException | IllegalArgumentException exception) {
      feedback.setText(userMessage(exception, "Calendar settings could not be saved"));
    }
  }

  private void setSaving(boolean saving) {
    if (daysContainer != null) {
      daysContainer.setDisable(saving);
    }
    if (saveButton != null) {
      saveButton.setDisable(saving);
    }
    if (cancelButton != null) {
      cancelButton.setDisable(saving);
    }
    if (backButton != null) {
      backButton.setDisable(saving);
    }
  }

  private static List<String> timeOptions(int startMinute, int endMinute) {
    List<String> values = new ArrayList<>();
    for (int minute = startMinute; minute <= endMinute; minute += 30) {
      values.add(formatMinute(minute));
    }
    return List.copyOf(values);
  }

  private static String formatMinute(int minute) {
    if (minute == WorkingInterval.MINUTES_PER_DAY) {
      return "24:00";
    }
    return "%02d:%02d".formatted(minute / 60, minute % 60);
  }

  private static String id(DayOfWeek day, String suffix) {
    String dayName = day.name().toLowerCase(Locale.ROOT);
    return suffix.isEmpty()
        ? SETTINGS_ID_PREFIX + dayName
        : SETTINGS_ID_PREFIX + dayName + "-" + suffix;
  }

  private static String userMessage(Throwable exception, String fallback) {
    return exception.getMessage() == null ? fallback : exception.getMessage();
  }

  private final class DayEditor {
    private final DayOfWeek day;
    private final CheckBox enabled = new CheckBox();
    private final VBox intervals = new VBox(6);
    private final Button add = UiComponents.secondaryButton("Add interval", null);
    private final VBox container = new VBox(8);

    private DayEditor(DayOfWeek day) {
      this.day = day;
      enabled.setId(id(day, "enabled"));
      enabled.setText(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      enabled.setAccessibleText("Enable " + enabled.getText() + " working hours");
      enabled
          .selectedProperty()
          .addListener((observable, previous, selected) -> updateEnabled(selected));
      add.setId(id(day, "add"));
      add.setAccessibleText("Add a working interval for " + enabled.getText());
      add.setOnAction(event -> addInterval(null));
      intervals.setId(id(day, "intervals"));
      intervals.getStyleClass().add("calendar-interval-list");
      HBox header = new HBox(12, enabled, add);
      header.setAlignment(Pos.CENTER_LEFT);
      container.setId(id(day, ""));
      container.getStyleClass().add("calendar-day-setting");
      container.getChildren().addAll(header, intervals);
    }

    private Node view() {
      return container;
    }

    private void populate(List<WorkingInterval> values) {
      intervals.getChildren().clear();
      for (WorkingInterval interval : values) {
        addInterval(interval);
      }
      enabled.setSelected(!values.isEmpty());
      updateEnabled(enabled.isSelected());
    }

    private void addInterval(WorkingInterval value) {
      IntervalEditor editor = new IntervalEditor(day, intervals.getChildren().size(), value);
      intervals.getChildren().add(editor.view());
      updateEnabled(enabled.isSelected());
    }

    private List<WorkingInterval> readIntervals() {
      if (!enabled.isSelected()) {
        return List.of();
      }
      if (intervals.getChildren().isEmpty()) {
        throw new ValidationException(
            day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " needs at least one working interval or must be disabled");
      }
      List<WorkingInterval> values = new ArrayList<>();
      for (Node child : intervals.getChildren()) {
        IntervalEditor editor = (IntervalEditor) child.getProperties().get("calendar-editor");
        values.add(editor.read());
      }
      return List.copyOf(values);
    }

    private void updateEnabled(boolean selected) {
      intervals.setDisable(!selected);
      add.setDisable(!selected);
    }
  }

  private final class IntervalEditor {
    private final ComboBox<String> start = UiComponents.compactSelector();
    private final ComboBox<String> end = UiComponents.compactSelector();
    private final Button removeButton = UiComponents.dangerButton("Remove", null);
    private final HBox row = new HBox(8);

    private IntervalEditor(DayOfWeek day, int index, WorkingInterval value) {
      start.setItems(FXCollections.observableArrayList(START_OPTIONS));
      end.setItems(FXCollections.observableArrayList(END_OPTIONS));
      start.setId(id(day, "start-" + index));
      end.setId(id(day, "end-" + index));
      removeButton.setId(id(day, "remove-" + index));
      start.setAccessibleText(
          "Working interval start for " + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      end.setAccessibleText(
          "Working interval end for " + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      removeButton.setAccessibleText(
          "Remove working interval for " + day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      start.setValue(value == null ? "12:00" : formatMinute(value.startMinute()));
      end.setValue(value == null ? "13:00" : formatMinute(value.endMinute()));
      removeButton.setOnAction(event -> remove());
      row.getStyleClass().add("calendar-interval-editor");
      row.getChildren().addAll(new Label("From"), start, new Label("to"), end, removeButton);
      row.setAlignment(Pos.CENTER_LEFT);
      row.getProperties().put("calendar-editor", this);
      HBox.setHgrow(start, Priority.NEVER);
      HBox.setHgrow(end, Priority.NEVER);
    }

    private Node view() {
      return row;
    }

    private WorkingInterval read() {
      if (start.getValue() == null || end.getValue() == null) {
        throw new ValidationException("Every working interval needs a start and end time");
      }
      try {
        int startMinute = parseMinute(start.getValue());
        int endMinute = parseMinute(end.getValue());
        return new WorkingInterval(startMinute, endMinute);
      } catch (IllegalArgumentException exception) {
        throw new ValidationException("Working interval end must be after its start", exception);
      }
    }

    private void remove() {
      for (DayEditor editor : dayEditors.values()) {
        if (editor.intervals.getChildren().remove(row)) {
          editor.updateEnabled(editor.enabled.isSelected());
          return;
        }
      }
    }
  }

  private static int parseMinute(String value) {
    String[] parts = value.split(":");
    return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
  }
}
