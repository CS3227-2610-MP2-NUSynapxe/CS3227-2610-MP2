package nusynapxe.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import nusynapxe.domain.CalendarWeek;
import nusynapxe.service.CalendarService;

/** Builds the custom month/year Calendar week picker. */
final class CalendarWeekPicker {
  private final Popup popup = new Popup();
  private final Consumer<LocalDate> onSelected;
  private YearMonth displayedMonth;
  private int displayedYear;
  private CalendarWeek selectedWeek;
  private DayOfWeek firstDayOfWeek;

  CalendarWeekPicker(Consumer<LocalDate> onSelected) {
    this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
    popup.setAutoHide(true);
    popup.setAutoFix(true);
    popup.setHideOnEscape(true);
  }

  void show(Node owner, CalendarWeek week) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(week, "week");
    selectedWeek = week;
    firstDayOfWeek = week.firstDayOfWeek();
    displayedMonth = YearMonth.from(week.start());
    displayedYear = displayedMonth.getYear();
    popup.getContent().setAll(buildContent());
    Point2D screenPoint = owner.localToScreen(0, owner.getLayoutBounds().getHeight());
    if (screenPoint == null) {
      return;
    }
    popup.show(owner, screenPoint.getX(), screenPoint.getY() + 6);
  }

  void hide() {
    popup.hide();
  }

  private VBox buildContent() {
    BorderPane monthPane = buildMonthPane();
    VBox yearPane = buildYearPane();
    HBox columns = new HBox(20, monthPane, yearPane);
    columns.setAlignment(Pos.TOP_LEFT);
    VBox content = new VBox(14, columns);
    content.setId("doctor-calendar-week-picker-popup");
    content.getStyleClass().add("calendar-week-picker");
    content.setPadding(new Insets(18));
    HBox.setHgrow(monthPane, Priority.ALWAYS);
    HBox footer = new HBox(8, todayButton(), closeButton());
    footer.setAlignment(Pos.CENTER_RIGHT);
    content.getChildren().add(footer);
    return content;
  }

  private BorderPane buildMonthPane() {
    BorderPane pane = new BorderPane();
    pane.setId("doctor-calendar-picker-month-pane");
    Button previous = iconButton("‹", "doctor-calendar-picker-previous-month", "Previous month");
    Button next = iconButton("›", "doctor-calendar-picker-next-month", "Next month");
    previous.setOnAction(event -> shiftMonth(-1));
    next.setOnAction(event -> shiftMonth(1));
    Label monthLabel = new Label(monthName(displayedMonth));
    monthLabel.setId("doctor-calendar-picker-month-label");
    monthLabel.getStyleClass().add("calendar-picker-heading");
    HBox header = new HBox(8, previous, monthLabel, next);
    header.setAlignment(Pos.CENTER);
    HBox.setHgrow(monthLabel, Priority.ALWAYS);
    monthLabel.setMaxWidth(Double.MAX_VALUE);
    monthLabel.setAlignment(Pos.CENTER);
    pane.setTop(header);
    pane.setCenter(buildMonthGrid());
    return pane;
  }

  private GridPane buildMonthGrid() {
    GridPane grid = new GridPane();
    grid.setId("doctor-calendar-picker-month-grid");
    grid.getStyleClass().add("calendar-picker-month-grid");
    grid.setHgap(2);
    grid.setVgap(2);
    ColumnConstraints weekColumn = new ColumnConstraints(44);
    weekColumn.setHalignment(HPos.CENTER);
    grid.getColumnConstraints().add(weekColumn);
    for (int index = 0; index < 7; index++) {
      ColumnConstraints dayColumn = new ColumnConstraints(42);
      dayColumn.setHalignment(HPos.CENTER);
      grid.getColumnConstraints().add(dayColumn);
    }
    Label weekHeader = new Label("Wk");
    weekHeader.getStyleClass().add("calendar-picker-week-number");
    grid.add(weekHeader, 0, 0);
    for (int index = 0; index < 7; index++) {
      DayOfWeek day = firstDayOfWeek.plus(index);
      Label header = new Label(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2));
      header.getStyleClass().add("calendar-picker-day-heading");
      grid.add(header, index + 1, 0);
    }

    LocalDate rowStart = CalendarWeek.containing(displayedMonth.atDay(1), firstDayOfWeek).start();
    LocalDate monthEnd = displayedMonth.atEndOfMonth();
    int row = 1;
    while (!rowStart.isAfter(monthEnd)) {
      LocalDate selectedRowStart = rowStart;
      CalendarWeek rowWeek = new CalendarWeek(selectedRowStart, firstDayOfWeek);
      Button weekButton = new Button("W" + rowWeek.weekNumber());
      weekButton.setId("doctor-calendar-picker-week-" + selectedRowStart);
      weekButton.setAccessibleText("Select week " + rowWeek.weekNumber());
      weekButton.getStyleClass().add("calendar-picker-week-number");
      if (rowWeek.start().equals(selectedWeek.start())) {
        weekButton.getStyleClass().add("calendar-picker-selected-week");
      }
      weekButton.setOnAction(event -> select(selectedRowStart));
      grid.add(weekButton, 0, row);
      for (int index = 0; index < 7; index++) {
        LocalDate date = rowStart.plusDays(index);
        Button dateButton = new Button(Integer.toString(date.getDayOfMonth()));
        dateButton.setId("doctor-calendar-picker-date-" + date);
        dateButton.setAccessibleText("Select " + date);
        dateButton.getStyleClass().add("calendar-picker-date");
        if (YearMonth.from(date).equals(displayedMonth)) {
          dateButton.getStyleClass().add("calendar-picker-current-month");
        } else {
          dateButton.getStyleClass().add("calendar-picker-other-month");
        }
        if (rowWeek.start().equals(selectedWeek.start())) {
          dateButton.getStyleClass().add("calendar-picker-selected-week");
        }
        if (date.equals(selectedWeek.start())) {
          dateButton.getStyleClass().add("calendar-picker-selected-date");
        }
        dateButton.setOnAction(event -> select(date));
        grid.add(dateButton, index + 1, row);
      }
      rowStart = rowStart.plusDays(7);
      row++;
    }
    return grid;
  }

  private VBox buildYearPane() {
    Label yearLabel = new Label(Integer.toString(displayedYear));
    yearLabel.setId("doctor-calendar-picker-year-label");
    yearLabel.getStyleClass().add("calendar-picker-heading");
    Button previous = iconButton("‹", "doctor-calendar-picker-previous-year", "Previous year");
    Button next = iconButton("›", "doctor-calendar-picker-next-year", "Next year");
    previous.setOnAction(event -> shiftYear(-1));
    next.setOnAction(event -> shiftYear(1));
    HBox header = new HBox(8, previous, yearLabel, next);
    header.setAlignment(Pos.CENTER);
    HBox.setHgrow(yearLabel, Priority.ALWAYS);
    yearLabel.setMaxWidth(Double.MAX_VALUE);
    yearLabel.setAlignment(Pos.CENTER);

    GridPane months = new GridPane();
    months.setId("doctor-calendar-picker-year-grid");
    months.getStyleClass().add("calendar-picker-year-grid");
    months.setHgap(6);
    months.setVgap(6);
    for (int index = 0; index < 12; index++) {
      Month month = Month.of(index + 1);
      Button monthButton = new Button(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
      monthButton.setId("doctor-calendar-picker-month-" + month.getValue());
      monthButton.setAccessibleText(
          "Select " + month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      monthButton.getStyleClass().add("calendar-picker-month");
      if (displayedMonth.getYear() == displayedYear && displayedMonth.getMonth() == month) {
        monthButton.getStyleClass().add("calendar-picker-selected-month");
      }
      monthButton.setOnAction(event -> selectMonth(month));
      months.add(monthButton, index % 3, index / 3);
    }
    VBox pane = new VBox(12, header, months);
    pane.setId("doctor-calendar-picker-year-pane");
    pane.setPrefWidth(220);
    return pane;
  }

  private Button todayButton() {
    Button today = new Button("Today");
    today.setId("doctor-calendar-picker-today");
    today.setAccessibleText("Select the current week");
    today.getStyleClass().add("secondary-action");
    today.setOnAction(
        event -> select(LocalDate.now(java.time.Clock.system(CalendarService.CLINIC_ZONE))));
    return today;
  }

  private Button closeButton() {
    Button close = new Button("Close");
    close.setId("doctor-calendar-picker-close");
    close.setAccessibleText("Close week picker");
    close.getStyleClass().add("secondary-action");
    close.setOnAction(event -> hide());
    return close;
  }

  private void select(LocalDate date) {
    onSelected.accept(date);
    hide();
  }

  private void selectMonth(Month month) {
    displayedMonth = YearMonth.of(displayedYear, month);
    popup.getContent().setAll(buildContent());
  }

  private void shiftMonth(int amount) {
    displayedMonth = displayedMonth.plusMonths(amount);
    displayedYear = displayedMonth.getYear();
    popup.getContent().setAll(buildContent());
  }

  private void shiftYear(int amount) {
    displayedYear += amount;
    displayedMonth = YearMonth.of(displayedYear, displayedMonth.getMonth());
    popup.getContent().setAll(buildContent());
  }

  private static String monthName(YearMonth month) {
    return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
  }

  private static Button iconButton(String text, String id, String accessibleText) {
    Button button = new Button(text);
    button.setId(id);
    button.setAccessibleText(accessibleText);
    button.getStyleClass().add("calendar-picker-icon-button");
    return button;
  }
}
