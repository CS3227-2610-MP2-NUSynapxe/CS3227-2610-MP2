package nusynapxe.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import nusynapxe.domain.Patient;

/** Builds patient table rows and reusable read-only patient detail presentation. */
final class PatientDirectoryTableView {
  private static final String EMAIL_LABEL = "Email";

  private PatientDirectoryTableView() {
    throw new AssertionError("Utility class");
  }

  static TableView<Patient> create(String prefix, Consumer<Patient> onView) {
    TableView<Patient> table = new TableView<>();
    table.setId(prefix + "-patient-table");
    table.getStyleClass().add("patient-table");
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setFixedCellSize(52);
    table.setPrefHeight(96);
    table.setMinHeight(96);
    table.setMaxHeight(Double.MAX_VALUE);
    table.setPlaceholder(
        UiComponents.emptyState(prefix + "-patient-empty", "No patients match this search."));
    TableColumn<Patient, String> status =
        textColumn("Status", patient -> patient.active() ? "Active" : "Inactive");
    status.setCellFactory(
        column ->
            new TableCell<>() {
              @Override
              protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : UiComponents.statusBadge(value));
              }
            });
    TableColumn<Patient, Patient> actions = actionColumn(prefix, onView);
    actions.setMinWidth(108);
    actions.setPrefWidth(108);
    actions.setMaxWidth(108);
    table
        .getColumns()
        .addAll(
            List.of(
                textColumn("Name", PatientDirectoryTableView::fullName),
                textColumn("Date of birth", patient -> valueOrEmpty(patient.dateOfBirth())),
                textColumn("Phone", PatientDirectoryTableView::displayPhone),
                textColumn(EMAIL_LABEL, patient -> valueOrEmpty(patient.email())),
                status,
                actions));
    return table;
  }

  static GridPane details(Patient patient) {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("patient-details-grid");
    grid.setHgap(18);
    grid.setVgap(12);
    addDetail(grid, 0, "Name", fullName(patient));
    addDetail(
        grid,
        1,
        "Identity document",
        valueOrEmpty(patient.identityType()) + " " + valueOrEmpty(patient.identityNumber()));
    addDetail(grid, 2, "Issuing country", valueOrEmpty(patient.issuingCountry()));
    addDetail(grid, 3, "Date of birth", valueOrEmpty(patient.dateOfBirth()));
    addDetail(grid, 4, "Sex", valueOrEmpty(patient.sex()));
    addDetail(grid, 5, "Phone", displayPhone(patient));
    addDetail(grid, 6, EMAIL_LABEL, valueOrEmpty(patient.email()));
    addDetail(grid, 7, "Address", valueOrEmpty(patient.address()));
    addDetail(grid, 8, "Height", measurement(patient.heightCm(), "cm"));
    addDetail(grid, 9, "Weight", measurement(patient.weightKg(), "kg"));
    addDetail(grid, 10, "Status", patient.active() ? "Active" : "Inactive");
    return grid;
  }

  static String fullName(Patient patient) {
    return (valueOrEmpty(patient.firstName()) + " " + valueOrEmpty(patient.lastName())).trim();
  }

  static String displayPhone(Patient patient) {
    String countryCode = valueOrEmpty(patient.phoneCountryCode());
    String number = valueOrEmpty(patient.phoneNumber());
    if (countryCode.isBlank()) {
      return number;
    }
    if (number.isBlank()) {
      return "+" + countryCode;
    }
    return "+" + countryCode + " " + number;
  }

  static String valueOrEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  private static TableColumn<Patient, String> textColumn(
      String title, Function<Patient, String> valueProvider) {
    TableColumn<Patient, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
    return column;
  }

  private static TableColumn<Patient, Patient> actionColumn(
      String prefix, Consumer<Patient> onView) {
    TableColumn<Patient, Patient> actions = new TableColumn<>("Actions");
    actions.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
    actions.setCellFactory(
        column ->
            new TableCell<>() {
              private final Button view = button("View", prefix + "-patient-view");

              {
                setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                view.getStyleClass().add("table-row-action");
                view.setOnAction(
                    event -> {
                      Patient patient = getItem();
                      if (patient != null) {
                        onView.accept(patient);
                      }
                    });
              }

              @Override
              protected void updateItem(Patient patient, boolean empty) {
                super.updateItem(patient, empty);
                if (empty || patient == null) {
                  setText(null);
                  setGraphic(null);
                } else {
                  view.setId(prefix + "-patient-view-" + patient.id());
                  setGraphic(view);
                }
              }
            });
    return actions;
  }

  private static void addDetail(GridPane grid, int row, String heading, String value) {
    Label headingLabel = new Label(heading);
    headingLabel.getStyleClass().add("field-label");
    Label valueLabel = new Label(value.isBlank() ? "—" : value.trim());
    valueLabel.setWrapText(true);
    grid.addRow(row, headingLabel, valueLabel);
  }

  private static String measurement(Double value, String unit) {
    return value == null ? "" : value + " " + unit;
  }

  private static Button button(String label, String id) {
    Button button = new Button(label);
    button.setId(id);
    return button;
  }
}
