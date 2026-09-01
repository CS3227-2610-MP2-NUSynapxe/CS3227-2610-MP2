package nusynapxe.domain;

/** Administrative patient information that may be used by reception staff. */
public record Patient(
    long id,
    String firstName,
    String lastName,
    String dateOfBirth,
    String phone,
    String email,
    String address,
    String billingInformation) {
  // Administrative fields only; clinical data has a separate projection.
}
