# Historical v1.0 Database

`notes-v1.0.db` is a real SQLite database created by the original Notesy model
and persistence implementation from foundation commit
`af4482d8ab45afca4534670a18646c19302a9526`, not by current schema-creation code.
It contains synthetic notes, not user data. It is GPL-3.0-or-later project test data.

SHA-256: `1b00e6bb2c59ad3c90ec85b99ce6a43492dbf366d2bafcdff26fad54599e14f5`

To regenerate, extract `src/main/java/org/stickynotefx/model` and `persistence`
from that commit with `git archive`, compile those historical sources with JDK 21,
and run `scripts/GenerateV10Fixture.java create <new-file>` with those classes and
system sqlite-jdbc on the classpath. The generator refuses to replace an existing
file. No JavaFX or current application classes are used to create the fixture.

`NoteRepositoryTest` copies this fixture, reads and updates notes, writes the
visual sidecar, checks unchanged table SQL and SQLite integrity, then exports the
closed database under `target/compatibility/notes-v1.0-updated.db`.
Run `scripts/GenerateV10Fixture.java verify` against that exported database using
the same historical classes to verify v1.0 can still read and write the result.