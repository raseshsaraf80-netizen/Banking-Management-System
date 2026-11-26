// Author: Sahil Raj
// Concept: File I/O using FileOutputStream and try-with-resources (plain stream logger)
package com.bank.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple stream-based transaction and audit logger writing to logs/bank.log.
 */
public class BankLogger {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File logFile;

    public BankLogger() {
        this("logs/bank.log");
    }

    public BankLogger(String logPath) {
        this.logFile = new File(logPath);
    }

    public synchronized void log(String message) {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String line = "[" + LocalDateTime.now().format(TS_FMT) + "] " + message;
        // try-with-resources closes FileOutputStream and BufferedWriter automatically
        try (FileOutputStream fos = new FileOutputStream(logFile, true);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException ex) {
            System.err.println("BankLogger write failed: " + ex.getMessage());
        }
    }

    public synchronized void seedDefaultLogsIfEmpty() {
        if (logFile.exists() && logFile.length() > 0) {
            return;
        }
        log("SYSTEM STARTUP: Meridian Trust Bank database initialized (SQLite + Plain JDBC)");
        log("SEED: Admin and 3 sample customers loaded (Aarav, Meera, Kabir)");
        log("OPEN_DEPOSIT | Account SB100001 | +15000.00 | Balance 15000.00");
        log("OPEN_DEPOSIT | Account CA100002 | +5000.00 | Balance 5000.00");
        log("OPEN_DEPOSIT | Account SB100003 | +25000.00 | Balance 25000.00");
        log("OPEN_DEPOSIT | Account CA100004 | +12000.00 | Balance 12000.00");
        log("SCHEDULER: Monthly 3.5% savings interest background task started");
    }

    public File getLogFile() {
        return logFile;
    }

    /**
     * Reads up to the last {@code maxLines} audit log entries for the Admin console.
     */
    public synchronized List<String> readRecentLines(int maxLines) {
        if (!logFile.exists()) {
            return List.of();
        }
        LinkedList<String> recent = new LinkedList<>();
        try (FileInputStream fis = new FileInputStream(logFile);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                recent.addFirst(line);
                while (recent.size() > maxLines) {
                    recent.removeLast();
                }
            }
        } catch (IOException ex) {
            System.err.println("BankLogger read failed: " + ex.getMessage());
        }
        return new ArrayList<>(recent);
    }
}

