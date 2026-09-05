package nusynapxe.ui;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One ISO issuing-country choice displayed by patient forms.
 *
 * @param code two-letter ISO 3166 country code
 * @param name English country display name
 */
public record CountryOption(String code, String name) {
  private static final String SINGAPORE_CODE = "SG";
  private static final List<CountryOption> ALL_COUNTRIES = createCountries();

  /**
   * Returns every ISO 3166 country with Singapore first and the remainder by English name.
   *
   * @return a mutable list containing the immutable country options
   */
  public static List<CountryOption> allCountries() {
    return new ArrayList<>(ALL_COUNTRIES);
  }

  /**
   * Finds an option using a case-insensitive two-letter country code.
   *
   * @param requestedCode country code to normalize and find
   * @return the matching option, or empty when the input is null or unknown
   */
  public static Optional<CountryOption> fromCode(String requestedCode) {
    if (requestedCode == null) {
      return Optional.empty();
    }
    String normalized = requestedCode.trim().toUpperCase(Locale.ROOT);
    return ALL_COUNTRIES.stream().filter(country -> country.code.equals(normalized)).findFirst();
  }

  /**
   * Returns the international calling code suggested for this ISO country.
   *
   * @return digits-only calling code, or an empty string when no code is available
   */
  public String callingCode() {
    int value = PhoneNumberUtil.getInstance().getCountryCodeForRegion(code);
    return value == 0 ? "" : Integer.toString(value);
  }

  /**
   * Returns the English country name shown in the dropdown.
   *
   * @return English country name
   */
  @Override
  public String toString() {
    return name;
  }

  private static List<CountryOption> createCountries() {
    List<CountryOption> countries = new ArrayList<>();
    for (String code : Locale.getISOCountries()) {
      Locale locale = new Locale.Builder().setRegion(code).build();
      countries.add(new CountryOption(code, locale.getDisplayCountry(Locale.ENGLISH)));
    }
    countries.sort(
        Comparator.comparing((CountryOption country) -> !SINGAPORE_CODE.equals(country.code))
            .thenComparing(CountryOption::name));
    return List.copyOf(countries);
  }
}
