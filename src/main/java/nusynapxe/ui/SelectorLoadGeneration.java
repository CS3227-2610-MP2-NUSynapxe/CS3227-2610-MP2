package nusynapxe.ui;

import java.util.IdentityHashMap;
import java.util.Map;

/** Tracks asynchronous freshness independently for each JavaFX selector target. */
final class SelectorLoadGeneration {
  private final Map<Object, Long> generations = new IdentityHashMap<>();

  long next(Object target) {
    long generation = generations.getOrDefault(target, 0L) + 1;
    generations.put(target, generation);
    return generation;
  }

  boolean isCurrent(Object target, long generation) {
    return generations.getOrDefault(target, 0L) == generation;
  }
}
