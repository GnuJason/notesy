package org.stickynotefx.reminder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DesktopNotifier {
    public void send(String content) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command(content))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new IOException("Desktop notification timed out");
            }
            if (process.exitValue() != 0) throw new IOException("Desktop notification was rejected");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    static List<String> command(String content) {
        String excerpt = content.replaceAll("\\s+", " ").strip();
        if (excerpt.isEmpty()) excerpt = "Your note is due.";
        if (excerpt.codePointCount(0, excerpt.length()) > 160) {
            excerpt = excerpt.substring(0, excerpt.offsetByCodePoints(0, 160)) + "...";
        }
        excerpt = excerpt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return List.of("notify-send", "--app-name=Notesy", "--icon=notesy",
            "--", "Notesy Reminder", excerpt);
    }
}