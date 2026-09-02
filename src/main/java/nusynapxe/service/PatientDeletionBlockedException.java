package nusynapxe.service;

import java.util.Objects;
import nusynapxe.domain.PatientDeletionBlockers;

/** Indicates that related patient data safely prevents physical deletion. */
public final class PatientDeletionBlockedException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final PatientDeletionBlockers deletionBlockers;

  /** Creates a blocked-deletion outcome containing only relationship counts. */
  public PatientDeletionBlockedException(PatientDeletionBlockers blockers) {
    super("Patient cannot be deleted because related records exist");
    this.deletionBlockers = Objects.requireNonNull(blockers, "blockers");
  }

  /** Returns the safe relationship counts for user-facing feedback. */
  public PatientDeletionBlockers blockers() {
    return deletionBlockers;
  }
}
