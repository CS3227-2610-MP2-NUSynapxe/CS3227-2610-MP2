package nusynapxe.ui;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/** A text search field with keyboard- and mouse-selectable suggestions underneath. */
final class SearchSuggestionField<T> extends VBox {
  private static final int MAX_VISIBLE_SUGGESTIONS = 5;
  private static final double SUGGESTION_ROW_HEIGHT = 36;

  private final TextField editor = new TextField();
  private final ListView<T> suggestions = new ListView<>();
  private final Popup suggestionPopup = new Popup();
  private final ObservableList<T> sourceItems = FXCollections.observableArrayList();
  private final ObjectProperty<T> value = new SimpleObjectProperty<>();
  private final Function<T, String> displayText;
  private final Function<T, String> searchText;
  private boolean updatingEditor;
  private boolean choosingWithMouse;

  SearchSuggestionField(
      String id, String prompt, Function<T, String> displayText, Function<T, String> searchText) {
    this.displayText = Objects.requireNonNull(displayText, "displayText");
    this.searchText = Objects.requireNonNull(searchText, "searchText");
    setSpacing(2);
    getStyleClass().add("search-suggestion-field");
    editor.setId(Objects.requireNonNull(id, "id"));
    editor.setPromptText(Objects.requireNonNull(prompt, "prompt"));
    suggestions.setId(id + "-suggestions");
    suggestions.getStyleClass().add("search-suggestions");
    suggestions.setFixedCellSize(SUGGESTION_ROW_HEIGHT);
    suggestions.setMaxHeight(MAX_VISIBLE_SUGGESTIONS * SUGGESTION_ROW_HEIGHT + 2);
    suggestions.setVisible(false);
    suggestions.setManaged(false);
    suggestions.setCellFactory(
        ignored ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(
                    empty || item == null
                        ? null
                        : SearchSuggestionField.this.displayText.apply(item));
              }
            });
    suggestionPopup.setAutoHide(true);
    suggestionPopup.setHideOnEscape(true);
    suggestionPopup.getContent().add(suggestions);
    getChildren().add(editor);

    editor
        .textProperty()
        .addListener(
            (observable, previous, current) -> {
              if (!updatingEditor) {
                T selected = value.get();
                if (selected != null && !displayText.apply(selected).equals(current)) {
                  value.set(null);
                }
                filterSuggestions(current);
                showSuggestions();
              }
            });
    editor
        .focusedProperty()
        .addListener(
            (observable, previous, focused) -> {
              if (focused) {
                filterSuggestions(editor.getText());
                showSuggestions();
              } else {
                Platform.runLater(
                    () -> {
                      if (!choosingWithMouse) {
                        hideSuggestions();
                      }
                    });
              }
            });
    editor.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.DOWN) {
            showSuggestions();
            if (suggestions.getSelectionModel().isEmpty()) {
              suggestions.getSelectionModel().selectFirst();
            } else {
              suggestions.getSelectionModel().selectNext();
            }
            event.consume();
          } else if (event.getCode() == KeyCode.UP) {
            showSuggestions();
            suggestions.getSelectionModel().selectPrevious();
            event.consume();
          } else if (event.getCode() == KeyCode.ENTER && suggestionPopup.isShowing()) {
            choose(suggestions.getSelectionModel().getSelectedItem());
            event.consume();
          } else if (event.getCode() == KeyCode.ESCAPE) {
            hideSuggestions();
            event.consume();
          }
        });
    suggestions.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            choose(suggestions.getSelectionModel().getSelectedItem());
            event.consume();
          } else if (event.getCode() == KeyCode.ESCAPE) {
            hideSuggestions();
            editor.requestFocus();
            event.consume();
          }
        });
    suggestions.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> choosingWithMouse = true);
    suggestions.setOnMouseReleased(
        event -> {
          choose(suggestions.getSelectionModel().getSelectedItem());
          choosingWithMouse = false;
        });
  }

  T getValue() {
    return value.get();
  }

  ObjectProperty<T> valueProperty() {
    return value;
  }

  void setItems(Collection<T> items) {
    sourceItems.setAll(items);
    filterSuggestions(editor.getText());
  }

  ObservableList<T> getItems() {
    return sourceItems;
  }

  ListView<T> suggestionList() {
    return suggestions;
  }

  void select(T item) {
    choose(item);
  }

  @SuppressWarnings("PMD.UnusedAssignment")
  void clearSelection() {
    updatingEditor = true;
    try {
      value.set(null);
      editor.clear();
    } finally {
      updatingEditor = false;
    }
    filterSuggestions("");
    hideSuggestions();
  }

  @SuppressWarnings("PMD.UnusedAssignment")
  private void choose(T item) {
    if (item == null) {
      return;
    }
    updatingEditor = true;
    try {
      value.set(item);
      editor.setText(displayText.apply(item));
      editor.positionCaret(editor.getText().length());
    } finally {
      updatingEditor = false;
    }
    hideSuggestions();
    editor.requestFocus();
  }

  private void filterSuggestions(String query) {
    T selected = value.get();
    if (selected != null && Objects.equals(query, displayText.apply(selected))) {
      query = "";
    }
    String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    suggestions.setItems(
        FXCollections.observableArrayList(
            sourceItems.stream()
                .filter(
                    item ->
                        normalized.isEmpty()
                            || searchText.apply(item).toLowerCase(Locale.ROOT).contains(normalized))
                .limit(50)
                .toList()));
    if (!suggestions.getItems().isEmpty()) {
      suggestions.getSelectionModel().selectFirst();
    }
  }

  private void showSuggestions() {
    boolean show = editor.isFocused() && !suggestions.getItems().isEmpty();
    if (!show || editor.getScene() == null) {
      hideSuggestions();
      return;
    }
    javafx.geometry.Bounds editorBounds = editor.localToScreen(editor.getBoundsInLocal());
    if (editorBounds == null) {
      return;
    }
    double rows = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.getItems().size());
    suggestions.setPrefWidth(Math.max(editor.getWidth(), 320));
    suggestions.setPrefHeight(rows * SUGGESTION_ROW_HEIGHT + 2);
    suggestions.setVisible(true);
    suggestions.setManaged(true);
    if (!suggestionPopup.isShowing()) {
      suggestionPopup.show(editor, editorBounds.getMinX(), editorBounds.getMaxY() + 2);
    }
  }

  private void hideSuggestions() {
    suggestionPopup.hide();
    suggestions.setVisible(false);
    suggestions.setManaged(false);
  }
}
