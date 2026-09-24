package nusynapxe.ui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import nusynapxe.domain.AppointmentListRow;
import nusynapxe.domain.AppointmentStatus;

/** Shared appointment-list projection controls for Receptionist appointment panes. */
final class ReceptionistAppointmentView {
  private static final String DATE_LABEL = "Date";
  private static final String DOCTOR_LABEL = "Doctor";

  private ReceptionistAppointmentView() {
    throw new AssertionError("Utility class");
  }

  static TableView<AppointmentListRow> appointmentTable(String id) {
    TableView<AppointmentListRow> table = new TableView<>();
    table.setId(id);
    table.setPrefHeight(360);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(
        UiComponents.emptyState(id + "-empty", "No appointments match these filters."));
    TableColumn<AppointmentListRow, String> date =
        textColumn(DATE_LABEL, row -> row.appointment().startsAt().toLocalDate().toString());
    TableColumn<AppointmentListRow, String> time =
        textColumn(
            "Time",
            row ->
                row.appointment().startsAt().toLocalTime()
                    + " – "
                    + row.appointment().endsAt().toLocalTime());
    TableColumn<AppointmentListRow, String> patient =
        textColumn("Patient", AppointmentListRow::patientDisplayName);
    TableColumn<AppointmentListRow, String> doctor =
        textColumn(DOCTOR_LABEL, AppointmentListRow::doctorDisplayName);
    table.getColumns().add(date);
    table.getColumns().add(time);
    table.getColumns().add(patient);
    table.getColumns().add(doctor);
    table.getColumns().add(statusColumn("Status"));
    return table;
  }

  private static TableColumn<AppointmentListRow, AppointmentStatus> statusColumn(String title) {
    TableColumn<AppointmentListRow, AppointmentStatus> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyObjectWrapper<>(data.getValue().appointment().status()));
    column.setCellFactory(
        tableColumn ->
            new TableCell<>() {
              @Override
              protected void updateItem(AppointmentStatus item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.statusBadge(item.name()));
              }
            });
    return column;
  }

  private static <T> TableColumn<T, String> textColumn(
      String title, java.util.function.Function<T, String> valueProvider) {
    TableColumn<T, String> column = new TableColumn<>(title);
    column.setCellValueFactory(
        data -> new ReadOnlyStringWrapper(valueProvider.apply(data.getValue())));
    return column;
  }
}
