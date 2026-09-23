package nusynapxe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.LocalDate;
import nusynapxe.domain.IdentityType;
import nusynapxe.domain.Patient;
import nusynapxe.persistence.SqliteDatabase;
import org.junit.jupiter.api.Test;

/** Verifies identity normalization, duplicate protection, and patient validation rules. */
final class PatientServiceValidationTest extends PatientServiceTestSupport {
  @Test
  void acceptsFlexibleDocumentsAndRejectsNormalizedDuplicatesAtomically() throws SQLException {
    try (SqliteDatabase database = openDatabase("identity.db")) {
      Fixture fixture = fixture(database);
      Patient first =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.PASSPORT, " oddformat ", " gb ", "+112345"));
      Patient second =
          fixture.service.register(
              fixture.receptionistSession,
              patient(0, IdentityType.OTHER, "document two", "zz", "9"));

      ValidationException duplicateCreate =
          assertThrows(
              ValidationException.class,
              () ->
                  fixture.service.register(
                      fixture.receptionistSession,
                      patient(0, IdentityType.PASSPORT, "ODDFORMAT", "GB", "+1123")));
      ValidationException duplicateUpdate =
          assertThrows(
              ValidationException.class,
              () ->
                  fixture.service.updateAdministrative(
                      fixture.receptionistSession, withIdentity(second, first)));

      assertEquals(PatientService.DUPLICATE_IDENTITY_MESSAGE, duplicateCreate.getMessage());
      assertEquals(PatientService.DUPLICATE_IDENTITY_MESSAGE, duplicateUpdate.getMessage());
      assertEquals(
          second, fixture.service.getAdministrative(fixture.receptionistSession, second.id()));
      assertEquals(2, fixture.service.searchAdministrative(fixture.receptionistSession, "").size());
    }
  }

  @Test
  void validatesRequiredIdentityPhoneDateAndMeasurements() throws SQLException {
    try (SqliteDatabase database = openDatabase("validation.db")) {
      Fixture fixture = fixture(database);

      assertInvalid(fixture, withIdentityType(validPatient(), null), "Identity type is required");
      assertInvalid(
          fixture, withIdentityNumber(validPatient(), " "), "Identity number is required");
      assertInvalid(fixture, withCountry(validPatient(), ""), "Issuing country is required");
      assertInvalid(
          fixture,
          withCountry(withIdentity(IdentityType.NRIC, "S1234567D"), "GB"),
          "Issuing country must be Singapore for NRIC and FIN");
      assertInvalid(
          fixture,
          withCountry(withIdentity(IdentityType.FIN, "G1234567A"), "GB"),
          "Issuing country must be Singapore for NRIC and FIN");
      assertInvalid(fixture, withSex(validPatient(), null), "Sex is required");
      assertInvalid(fixture, withDate(validPatient(), "01/09/1990"), "Date of birth must use");
      assertInvalid(
          fixture,
          withDate(validPatient(), LocalDate.now(SINGAPORE_ZONE).plusDays(1).toString()),
          "cannot be in the future");
      assertInvalid(fixture, withEmail(validPatient(), "invalid"), "Email must contain @");
      assertInvalid(fixture, withAddress(validPatient(), " "), "Address is required");
      assertInvalid(
          fixture, withIdentity(IdentityType.NRIC, "S123A"), "NRIC must start with S or T");
      assertInvalid(
          fixture, withIdentity(IdentityType.FIN, "A1234567Z"), "FIN must start with F, G or M");
      assertInvalid(fixture, withPhoneCountryCode(validPatient(), "+"), "Phone country code");
      assertInvalid(fixture, withPhoneCountryCode(validPatient(), "+65"), "digits only");
      assertInvalid(fixture, withPhone(validPatient(), "12-34"), "Phone number");
      assertInvalid(fixture, withPhone(validPatient(), "1+234"), "Phone number");
      assertInvalid(fixture, withIdentityNumber(validPatient(), "AB-123"), "Passport number must");
      assertInvalid(fixture, withHeight(validPatient(), 170.5), "whole number");
      assertInvalid(fixture, withWeight(validPatient(), 70.45), "at most 1 decimal");
      assertInvalid(fixture, withHeight(validPatient(), 0.0), "Height must be a positive number");
      assertInvalid(fixture, withWeight(validPatient(), -1.0), "Weight must be a positive number");

      assertEquals(
          "+441234567890",
          fixture
              .service
              .register(fixture.receptionistSession, withPhone(validPatient(), "1234567890"))
              .phone());
    }
  }
}
