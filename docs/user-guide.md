# User Guide

## Start NUSynapxe

From the repository root, use:

```powershell
.\gradlew.bat run
```

The first launch creates a local SQLite database. The initial window displays
the application name and the path used for storage; later feature work will
grow this window into the NUSynapxe desktop experience.

## Local data

By default, the database is stored at:

```text
Windows: %USERPROFILE%\.nusynapxe\nusynapxe.db
macOS/Linux: ~/.nusynapxe/nusynapxe.db
```

The database is local to the current computer. Do not commit the database file
to version control. Close the application before copying or removing it.
