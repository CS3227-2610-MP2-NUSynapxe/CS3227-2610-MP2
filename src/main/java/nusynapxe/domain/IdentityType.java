package nusynapxe.domain;

/** Identity document categories accepted for local and foreign patients. */
public enum IdentityType {
  /** Singapore National Registration Identity Card. */
  NRIC,
  /** Singapore Foreign Identification Number. */
  FIN,
  /** Passport issued by a country or territory. */
  PASSPORT,
  /** Another documented identifier. */
  OTHER
}
