package nusynapxe.ui;

import java.sql.SQLException;
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
import javafx.util.StringConverter;
import nusynapxe.domain.DoctorCalendarSettings;
import nusynapxe.domain.Session;
import nusynapxe.domain.WorkingInterval;
import nusynapxe.service.AuthorizationException;
import nusynapxe.service.ClinicServices;
import nusynapxe.service.ValidationException;

/** Builds the Doctor-owned Calendar display-preferences page. */
public final class DoctorCalendarSettingsView {
  private static final String SETTINGS_ID_PREFIX = "doctor-calendar-settings-";
  private static final List<String> START_OPTIONS = timeOptions(0, 1410);
  private static final List<String> END_OPTIONS = timeOptions(30, WorkingInterval.MINUTES_PER_DAY);

  private final ClinicServices services;
  private final Session session;
  private final Runnable onBack;
  private final Runnable onSaved;
  private final Label feedback;
  private final ComboBox<DayOfWeek> firstDay = new ComboBox<>();
  private final Map<DayOfWeek, DayEditor> dayEditors = new EnumMap<>(DayOfWeek.class);
  private final BorderPane root;

  /** Creates a Calendar settings page for one authenticated Doctor. */
  public DoctorCalendarSettingsView(
      ClinicServices services, Session session, Runnable onBack, Runnable onSaved, Label feedback) {
    this.services = Objects.requireNonNull(services, "services");
    this.session = Objects.requireNonNull(session, "session");
    this.onBack = Objects.requireNonNull(onBack, "onBack");
    this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
    this.feedback = Objects.requireNonNull(feedback, "feedback");
    root = buildRoot();
    reload();
  }

  /** Returns the settings page node. */
  public Parent view() {
    return root;
  }

  /** Reloads persisted settings into the editable draft. */
  public void reload() {
    try {
      populate(services.calendarService().getSettings(session));
    } catch (SQLException | AuthorizationException | ValidationException exception) {
      feedback.setText(userMessage(exception, "Calendar settings are temporarily unavailable"));
    }
  }

  private BorderPane buildRoot() {
    Button back = UiComponents.secondaryButton("Back to Calendar", "doctor-calendar-settings-back");
    back.setAccessibleText("Return to Calendar");
    back.setOnAction(event -> onBack.run());
    HBox toolbar = new HBox(12, back, UiComponents.pageTitle("Calendar settings"));
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("calendar-settings-toolbar");

    firstDay.setId("doctor-calendar-settings-first-day");
    firstDay.setAccessibleText("First day of the week");
    firstDay.setItems(FXCollections.observableArrayList(DayOfWeek.values()));
    firstDay.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(DayOfWeek day) {
            return day == null ? "" : day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
          }

          @Override
          public DayOfWeek fromString(String value) {
            return value == null || value.isBlank()
                ? null
                : DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
          }
        });
    firstDay.getStyleClass().add("compact-selector");

    VBox preferences =
        UiComponents.card(
            "doctor-calendar-settings-preferences",
            UiComponents.sectionHeading("Calendar preferences"),
            UiComponents.supportingText(
                "Working hours control grey shading only. They do not block appointments."),
            UiComponents.fieldGroup("Show the first day of the week as", firstDay),
            timezoneRow());

    VBox days = new VBox(10);
    days.setId("doctor-calendar-settings-days");
    days.getStyleClass().add("calendar-settings-days");
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
            days);

    Button save = UiComponents.primaryButton("Save settings", "doctor-calendar-settings-save");
    save.setAccessibleText("Save Calendar settings");
    save.setOnAction(event -> save());
    Button cancel = UiComponents.secondaryButton("Cancel", "doctor-calendar-settings-cancel");
    cancel.setAccessibleText("Cancel Calendar setting edits");
    cancel.setOnAction(event -> onBack.run());
    HBox actions = UiComponents.actionBar(save, cancel);

    VBox content = new VBox(16, preferences, workingHours, actions);
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
    firstDay.setValue(settings.firstDayOfWeek());
    for (DayOfWeek day : DayOfWeek.values()) {
      dayEditors.get(day).populate(settings.intervals(day));
    }
  }

  private void save() {
    try {
      if (firstDay.getValue() == null) {
        throw new ValidationException("Choose the first day of the week");
      }
      Map<DayOfWeek, List<WorkingInterval>> intervals = new EnumMap<>(DayOfWeek.class);
      for (DayOfWeek day : DayOfWeek.values()) {
        intervals.put(day, dayEditors.get(day).readIntervals());
      }
      DoctorCalendarSettings settings =
          new DoctorCalendarSettings(session.accountId(), firstDay.getValue(), intervals);
      services.calendarService().saveSettings(session, settings);
      feedback.setText("Calendar settings saved");
      onSaved.run();
    } catch (SQLException
        | AuthorizationException
        | ValidationException
        | IllegalArgumentException exception) {
      feedback.setText(userMessage(exception, "Calendar settings could not be saved"));
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

  private static String userMessage(Exception exception, String fallback) {
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
    private final ComboBox<String> start = new ComboBox<>();
    private final ComboBox<String> end = new ComboBox<>();
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
      start.getStyleClass().add("compact-selector");
      end.getStyleClass().add("compact-selector");
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
