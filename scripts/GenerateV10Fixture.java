import java.nio.file.Files;
import java.nio.file.Path;
import org.stickynotefx.model.Note;
import org.stickynotefx.persistence.DatabaseProvider;
import org.stickynotefx.persistence.NoteRepository;

class GenerateV10Fixture {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("Usage: create|verify database-path");
        Path file = Path.of(arguments[1]);
        if (arguments[0].equals("create")) {
            if (Files.exists(file)) throw new IllegalArgumentException("Refusing to overwrite " + file);
            try (var database = new DatabaseProvider(file)) {
                var repository = new NoteRepository(database);
                repository.create(new Note(0, "v1.0 reminder\nOriginal content", -100, 80, 360, 280,
                        "#B8E0D2", 1800000000000L, "legacy-theme"));
                repository.create(Note.blank(200, 160).withContent("v1.0 plain note"));
            }
        } else if (arguments[0].equals("verify")) {
            if (!Files.exists(file)) throw new IllegalArgumentException("Missing database " + file);
            try (var database = new DatabaseProvider(file)) {
                var repository = new NoteRepository(database);
                var notes = repository.findAll();
                if (notes.size() != 2 || !notes.getFirst().content().equals("Updated by staged features")
                        || !"legacy-theme".equals(notes.getFirst().theme())
                        || notes.getFirst().reminderTimestamp() != 1800000000000L) {
                    throw new AssertionError("v1.0 could not read the updated fixture");
                }
                repository.update(notes.getFirst().withContent("Written again by v1.0"));
                if (!repository.findAll().getFirst().content().equals("Written again by v1.0")) {
                    throw new AssertionError("v1.0 write failed after staged-feature round trip");
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown operation " + arguments[0]);
        }
    }
}