package nusynapxe.ui;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import nusynapxe.domain.Account;
import nusynapxe.domain.Receipt;

/** Owns receipt filters, receipt history loading, and the selected receipt preview. */
final class ReceptionistReceiptPanel {
  private static final String ALL_DOCTORS = "All Doctors";
  private static final String PATIENT_NAME_PROMPT = "Name, NRIC/FIN, phone, or email";
  private final ReceptionistDataLoader dataLoader;
  private final Label workspaceFeedback;
  private final DatePicker receiptDate;
  private final SearchSuggestionField<Account> receiptDoctor;
  private final TextField receiptPatient;
  private final TableView<Receipt> receiptHistoryList;
  private final Label receiptPreview;
  private final VBox contentView;

  ReceptionistReceiptPanel(ReceptionistDataLoader dataLoader, Label workspaceFeedback) {
    this.dataLoader = dataLoader;
    this.workspaceFeedback = workspaceFeedback;
    receiptDate = UiComponents.compactDatePicker();
    receiptDate.setId("reception-receipt-date");
    receiptDoctor = doctorSelector("reception-receipt-doctor", ALL_DOCTORS);
    receiptPatient = field("reception-receipt-patient", PATIENT_NAME_PROMPT);
    receiptHistoryList = ReceptionistWorkspace.receiptTable("reception-receipt-history-list");
    Button receiptSearch = button("Search receipts", "reception-receipt-search");
    receiptPreview = new Label();
    receiptPreview.setId("reception-receipt-preview");
    receiptSearch.setOnAction(event -> refresh());
    receiptHistoryList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected != null) {
                showReceipt(selected);
              }
            });
    contentView = buildContent(receiptSearch);
  }

  /** Returns the Receipts subtab content. */
  VBox content() {
    return contentView;
  }

  /** Starts an asynchronous receipt-history refresh with the current filters. */
  void refresh() {
    dataLoader.refreshReceiptHistory(
        receiptHistoryList,
        receiptPreview,
        receiptPatient.getText(),
        receiptDoctor.getValue() == null ? null : receiptDoctor.getValue().id(),
        receiptDate.getValue(),
        workspaceFeedback);
  }

  /** Displays a newly issued receipt in the preview area. */
  void showReceipt(Receipt receipt) {
    receiptPreview.setText(ReceptionistCheckoutView.formatReceipt(receipt));
  }

  /** Refreshes the Doctor suggestions in the receipt filter. */
  void refreshDoctors() {
    dataLoader.refreshDoctors(receiptDoctor, workspaceFeedback, false);
  }

  private VBox buildContent(Button receiptSearch) {
    GridPane filters = filterGrid("reception-receipt-filter-grid");
    filters.getStyleClass().add("spacious-filter-grid");
    addUniformColumns(filters, 3, 180, 240, 240);
    filters.add(UiComponents.fieldGroup("Patient", receiptPatient), 0, 0);
    filters.add(UiComponents.fieldGroup("Doctor", receiptDoctor), 1, 0);
    filters.add(UiComponents.fieldGroup("Date", receiptDate), 2, 0);
    filters.add(receiptSearch, 3, 0);
    alignFilterAction(receiptSearch);
    VBox receipts =
        UiComponents.card(
            "reception-receipts-card",
            UiComponents.sectionHeading("Issued receipts"),
            filters,
            receiptHistoryList,
            receiptPreview);
    VBox receiptContent = new VBox(receipts);
    receiptContent.getStyleClass().add("subtab-content");
    receiptContent.setPadding(new Insets(22, 0, 0, 0));
    receiptContent.setId("reception-receipts-tab");
    return receiptContent;
  }

  private static SearchSuggestionField<Account> doctorSelector(String id, String prompt) {
    return new SearchSuggestionField<>(
        id,
        prompt,
        Account::displayName,
        account -> account.displayName() + " " + account.username());
  }

  private static TextField field(String id, String prompt) {
    TextField field = new TextField();
    field.setId(id);
    field.setPromptText(prompt);
    return field;
  }

  private static Button button(String label, String id) {
    Button button = new Button(label);
    button.setId(id);
    return button;
  }

  private static GridPane filterGrid(String id) {
    GridPane grid = new GridPane();
    grid.setId(id);
    grid.setHgap(12);
    grid.setVgap(10);
    return grid;
  }

  private static void addUniformColumns(GridPane grid, int count, double... widths) {
    for (int index = 0; index < count; index++) {
      ColumnConstraints column = new ColumnConstraints();
      column.setMinWidth(widths[Math.min(index, widths.length - 1)]);
      column.setHgrow(Priority.SOMETIMES);
      grid.getColumnConstraints().add(column);
    }
  }

  private static void alignFilterAction(Button button) {
    GridPane.setValignment(button, VPos.BOTTOM);
    button.setMinHeight(38);
    button.setPrefHeight(38);
  }
}
