package nusynapxe.domain;

/** Administrative patient information that may be used by reception staff. */
public record Patient(
    long id,
    IdentityType identityType,
    String identityNumber,
    String issuingCountry,
    String firstName,
    String lastName,
    String dateOfBirth,
    Sex sex,
    String phone,
    String email,
    String address,
    String billingInformation,
    Double heightCm,
    Double weightKg,
    boolean active) {

  /**
   * Creates a legacy-compatible administrative patient value.
   *
   * <p>Only migration and lower-level repository fixtures should use this constructor. New
   * Receptionist registrations require identity and sex through the service layer.
   */
  public Patient(
      long id,
      String firstName,
      String lastName,
      String dateOfBirth,
      String phone,
      String email,
      String address,
      String billingInformation) {
    this(
        id,
        null,
        null,
        null,
        firstName,
        lastName,
        dateOfBirth,
        null,
        phone,
        email,
        address,
        billingInformation,
        null,
        null,
        true);
  }

  /** Returns the human-readable form of the generated Patient ID. */
  public String displayedId() {
    return "P%06d".formatted(id);
  }

  /** Returns a non-sensitive directory label that does not expose identity-document numbers. */
  @Override
  public String toString() {
    String suffix = active ? "" : " (inactive)";
    return displayedId() + " - " + firstName + " " + lastName + suffix;
  }
}
