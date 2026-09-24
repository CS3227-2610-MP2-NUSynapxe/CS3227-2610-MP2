package nusynapxe.ui;

import java.time.LocalDate;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Builds and responsively lays out the Doctor Calendar toolbar. */
final class DoctorCalendarToolbar {
  private DoctorCalendarToolbar() {
    throw new AssertionError("Utility class");
  }

  /** Creates the Calendar page and wires its toolbar controls to the supplied callbacks. */
  static Controls create(
      Button previous,
      Button next,
      DatePicker scheduleDate,
      DatePicker from,
      DatePicker to,
      VBox fromField,
      VBox toField,
      ComboBox<String> viewMode,
      Runnable goToToday,
      Runnable goToPrevious,
      Runnable goToNext,
      Runnable addAppointment,
      Runnable blockTime,
      Runnable refresh,
      Runnable openSettings,
      Consumer<LocalDate> selectDate,
      Consumer<String> changeMode) {
    Button today = UiComponents.secondaryButton("Today", "doctor-calendar-today");
    today.setAccessibleText("Go to today");
    today.setOnAction(event -> goToToday.run());
    previous.setAccessibleText("Previous");
    previous.setOnAction(event -> goToPrevious.run());
    next.setAccessibleText("Next");
    next.setOnAction(event -> goToNext.run());
    Button addAppointmentButton =
        UiComponents.primaryButton("Add appointment", "doctor-calendar-add-appointment");
    addAppointmentButton.setAccessibleText("Add appointment to my schedule");
    addAppointmentButton.setOnAction(event -> addAppointment.run());
    Button blockTimeButton =
        UiComponents.secondaryButton("Block time", "doctor-calendar-block-time");
    blockTimeButton.setAccessibleText("Block time on my schedule");
    blockTimeButton.setOnAction(event -> blockTime.run());
    Button refreshButton = new Button("↻");
    refreshButton.setId("doctor-calendar-refresh");
    refreshButton.setAccessibleText("Refresh Calendar");
    refreshButton.setTooltip(new Tooltip("Refresh Calendar"));
    refreshButton.getStyleClass().add("calendar-settings-button");
    refreshButton.setOnAction(event -> refresh.run());
    scheduleDate.setAccessibleText("Choose Agenda start date");
    scheduleDate.setOnAction(event -> selectDate.accept(scheduleDate.getValue()));
    from.setOnAction(event -> refresh.run());
    to.setOnAction(event -> refresh.run());
    fromField.setPrefWidth(190);
    toField.setPrefWidth(190);
    viewMode.setId("doctor-calendar-view-mode");
    viewMode.setAccessibleText("Choose Calendar view");
    viewMode.getItems().addAll("Calendar", "Agenda");
    viewMode.setEditable(false);
    viewMode.setValue("Calendar");
    viewMode.setOnAction(event -> changeMode.accept(viewMode.getValue()));
    Button settings = new Button("⚙");
    settings.setId("doctor-calendar-settings");
    settings.setAccessibleText("Open Calendar settings");
    settings.setTooltip(new Tooltip("Calendar settings"));
    settings.getStyleClass().add("calendar-settings-button");
    settings.setOnAction(event -> openSettings.run());

    HBox navigationGroup =
        new HBox(8, today, previous, scheduleDate, next, fromField, toField, viewMode);
    navigationGroup.setAlignment(Pos.BOTTOM_LEFT);
    navigationGroup.getStyleClass().add("calendar-toolbar-group");
    HBox actionGroup = new HBox(8, addAppointmentButton, blockTimeButton);
    actionGroup.setId("doctor-calendar-action-group");
    actionGroup.setAlignment(Pos.BOTTOM_LEFT);
    actionGroup.getStyleClass().add("calendar-toolbar-actions");
    HBox trailingGroup = new HBox(8, refreshButton, settings);
    trailingGroup.setAlignment(Pos.BOTTOM_LEFT);
    trailingGroup.getStyleClass().add("calendar-toolbar-group");
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox mainRow = new HBox(8, navigationGroup, actionGroup, spacer, trailingGroup);
    mainRow.setId("doctor-calendar-toolbar-main");
    mainRow.setAlignment(Pos.BOTTOM_LEFT);
    HBox actionsRow = new HBox(8);
    actionsRow.setId("doctor-calendar-toolbar-actions");
    actionsRow.setAlignment(Pos.BOTTOM_LEFT);
    VBox toolbar = new VBox(8, mainRow);
    toolbar.setId("doctor-calendar-toolbar");
    toolbar.getStyleClass().add("calendar-toolbar");
    Controls controls =
        new Controls(toolbar, navigationGroup, actionGroup, trailingGroup, mainRow, actionsRow);
    toolbar.widthProperty().addListener((observable, oldWidth, newWidth) -> updateLayout(controls));
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
    controls.pageView = page;
    updateLayout(controls);
    return controls;
  }

  /** Reflows the action buttons below the navigation when the toolbar is narrow. */
  static void updateLayout(Controls controls) {
    double availableWidth = controls.toolbar.getWidth();
    double requiredWidth =
        controls.navigationGroup.prefWidth(-1)
            + controls.actionGroup.prefWidth(-1)
            + controls.trailingGroup.prefWidth(-1)
            + controls.mainRow.getSpacing() * 3;
    boolean shouldWrap = availableWidth > 0 && availableWidth + 0.5 < requiredWidth;
    boolean isWrapped = controls.actionsRow.getChildren().contains(controls.actionGroup);
    if (shouldWrap == isWrapped) {
      return;
    }
    if (shouldWrap) {
      controls.mainRow.getChildren().remove(controls.actionGroup);
      controls.actionsRow.getChildren().setAll(controls.actionGroup);
      controls.toolbar.getChildren().setAll(controls.mainRow, controls.actionsRow);
    } else {
      controls.actionsRow.getChildren().clear();
      controls.mainRow.getChildren().add(1, controls.actionGroup);
      controls.toolbar.getChildren().setAll(controls.mainRow);
    }
  }

  static final class Controls {
    private final VBox toolbar;
    private final HBox navigationGroup;
    private final HBox actionGroup;
    private final HBox trailingGroup;
    private final HBox mainRow;
    private final HBox actionsRow;
    private BorderPane pageView;

    private Controls(
        VBox toolbar,
        HBox navigationGroup,
        HBox actionGroup,
        HBox trailingGroup,
        HBox mainRow,
        HBox actionsRow) {
      this.toolbar = toolbar;
      this.navigationGroup = navigationGroup;
      this.actionGroup = actionGroup;
      this.trailingGroup = trailingGroup;
      this.mainRow = mainRow;
      this.actionsRow = actionsRow;
    }

    BorderPane page() {
      return pageView;
    }
  }
}
