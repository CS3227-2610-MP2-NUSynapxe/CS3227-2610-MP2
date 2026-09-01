package nusynapxe.domain;

/** Administrative sex values recorded for a patient. */
public enum Sex {
  /** Female. */
  FEMALE("Female"),
  /** Male. */
  MALE("Male");

  private final String displayName;

  Sex(String displayName) {
    this.displayName = displayName;
  }

  /** Returns the user-facing label while persistence continues to use {@link #name()}. */
  @Override
  public String toString() {
    return displayName;
  }
}
