package nusynapxe.ui;

import javafx.scene.control.TextInputControl;

/** Small presentation boundary for Doctor patient-form editability. */
final class DoctorPatientFormView {
  private DoctorPatientFormView() {
    throw new AssertionError("Utility class");
  }

  static void setEditable(TextInputControl control, boolean editable) {
    control.setEditable(editable);
    if (editable) {
      control.getStyleClass().remove("read-only-field");
    } else if (!control.getStyleClass().contains("read-only-field")) {
      control.getStyleClass().add("read-only-field");
    }
  }
}
