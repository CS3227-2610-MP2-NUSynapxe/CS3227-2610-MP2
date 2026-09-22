package nusynapxe.ui;

import java.util.concurrent.atomic.AtomicBoolean;

/** Tracks whether callbacks owned by a workspace may still update the UI. */
final class WorkspaceLifecycle {
  private final AtomicBoolean active = new AtomicBoolean(true);

  /** Returns whether the owning workspace is still attached and usable. */
  boolean isActive() {
    return active.get();
  }

  /** Invalidates callbacks that were submitted before the workspace logged out. */
  void invalidate() {
    active.set(false);
  }
}
