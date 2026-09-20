package nusynapxe;

/** Non-JavaFX entry point for executable JAR launches. */
public final class NUSynapxeLauncher {
  private NUSynapxeLauncher() {
    throw new AssertionError("Utility class");
  }

  /**
   * Delegates executable JAR launches to the JavaFX application.
   *
   * @param arguments command-line arguments forwarded to JavaFX
   */
  public static void main(String[] arguments) {
    NUSynapxeApp.main(arguments);
  }
}
