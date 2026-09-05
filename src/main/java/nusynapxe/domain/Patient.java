package nusynapxe.domain;

/**
 * Administrative patient information that may be used by reception staff.
 *
 * @param id generated patient identifier
 * @param identityType document category, when present
 * @param identityNumber normalized document number, when present
 * @param issuingCountry ISO country code for the identity document, when present
 * @param firstName patient's given name
 * @param lastName patient's family name
 * @param dateOfBirth ISO local date text
 * @param sex administrative sex value, when present
 * @param phoneCountryCode digits-only international calling code, when present
 * @param phoneNumber digits-only local telephone number
 * @param email email address
 * @param address postal address
 * @param heightCm height in centimetres, when recorded
 * @param weightKg weight in kilograms, when recorded
 * @param active whether the patient may be booked for new appointments
 */
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
   *
   * @param id generated patient identifier
   * @param firstName patient's given name
   * @param lastName patient's family name
   * @param dateOfBirth ISO local date text
   * @param phone legacy complete telephone text
   * @param email email address
   * @param address postal address
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

  /**
   * Returns the complete telephone number for display and compatibility.
   *
   * @return the calling code and local number, or the local number when no calling code is stored
   */
  public String phone() {
    String number = phoneNumber == null ? "" : phoneNumber;
    return phoneCountryCode == null || phoneCountryCode.isBlank()
        ? number
        : "+" + phoneCountryCode + number;
  }

  /**
   * Returns the human-readable form of the generated Patient ID.
   *
   * @return the ID formatted with a {@code P} prefix and six digits
   */
  public String displayedId() {
    return "P%06d".formatted(id);
  }

  /**
   * Returns a non-sensitive directory label that does not expose identity-document numbers.
   *
   * @return formatted patient ID, name, and inactive marker when applicable
   */
  @Override
  public String toString() {
    String suffix = active ? "" : " (inactive)";
    return displayedId() + " - " + firstName + " " + lastName + suffix;
  }
}
