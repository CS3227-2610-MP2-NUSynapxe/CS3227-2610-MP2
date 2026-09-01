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
    String phoneCountryCode,
    String phoneNumber,
    String email,
    String address,
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
      String address) {
    this(
        id,
        null,
        null,
        null,
        firstName,
        lastName,
        dateOfBirth,
        null,
        null,
        phone,
        email,
        address,
        null,
        null,
        true);
  }

  /** Returns the complete telephone number for display and compatibility. */
  public String phone() {
    return (phoneCountryCode == null ? "" : phoneCountryCode)
        + (phoneNumber == null ? "" : phoneNumber);
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
