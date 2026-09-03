package nusynapxe.ui;

import java.sql.SQLException;
import java.time.LocalDate;
import nusynapxe.domain.CalendarScheduleCursor;
import nusynapxe.domain.CalendarSchedulePage;

/** Loads one bounded schedule page for the Calendar list. */
@FunctionalInterface
interface CalendarSchedulePageLoader {

  /** Loads a page for an anchor and optional keyset cursor. */
  CalendarSchedulePage load(LocalDate anchor, CalendarScheduleCursor cursor, int pageSize)
      throws SQLException;
}
