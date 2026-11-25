// Author: Sahil Raj
// Concept: File I/O with FileOutputStream, FileInputStream, BufferedWriter, BufferedReader
package com.bank.service;

import com.bank.exception.BankException;
import com.bank.model.Transaction;
import com.bank.model.TransactionType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports account statements to TXT and CSV files and reads CSV statements back via streams.
 */
public class StatementService {

    private final File statementsDir;

    public StatementService() {
        this("statements");
    }

    public StatementService(String directoryPath) {
        this.statementsDir = new File(directoryPath);
    }

    public File exportToTxt(String accountNumber, List<Transaction> transactions) throws BankException {
        ensureDirectory();
        File target = new File(statementsDir, accountNumber + "_" + LocalDate.now() + ".txt");
        try (FileOutputStream fos = new FileOutputStream(target);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write("============================================================");
            writer.newLine();
            writer.write("MERIDIAN TRUST BANK - ACCOUNT STATEMENT (" + accountNumber + ")");
            writer.newLine();
            writer.write("Generated on: " + LocalDateTime.now());
            writer.newLine();
            writer.write("============================================================");
            writer.newLine();
            writer.write(String.format("%-6s %-22s %-14s %-12s %-12s %s",
                    "ID", "Timestamp", "Type", "Amount", "Balance", "Description"));
            writer.newLine();

            for (Transaction t : transactions) {
                writer.write(String.format("%-6d %-22s %-14s %-12s %-12s %s",
                        t.getTxnId(),
                        t.getTimestamp().toString(),
                        t.getType().name(),
                        t.getAmount().toPlainString(),
                        t.getBalanceAfter().toPlainString(),
                        safeText(t.getDescription())));
                writer.newLine();
            }
            return target;
        } catch (IOException ex) {
            throw new BankException("Failed to export TXT statement: " + ex.getMessage(), ex);
        }
    }

    public File exportToCsv(String accountNumber, List<Transaction> transactions) throws BankException {
        ensureDirectory();
        File target = new File(statementsDir, accountNumber + "_" + LocalDate.now() + ".csv");
        try (FileOutputStream fos = new FileOutputStream(target);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write("txn_id,account_number,type,amount,balance_after,timestamp,description");
            writer.newLine();
            for (Transaction t : transactions) {
                String desc = safeText(t.getDescription()).replace("\"", "\"\"");
                writer.write(String.format("%d,%s,%s,%s,%s,%s,\"%s\"",
                        t.getTxnId(),
                        t.getAccountNumber(),
                        t.getType().name(),
                        t.getAmount().toPlainString(),
                        t.getBalanceAfter().toPlainString(),
                        t.getTimestamp().toString(),
                        desc));
                writer.newLine();
            }
            return target;
        } catch (IOException ex) {
            throw new BankException("Failed to export CSV statement: " + ex.getMessage(), ex);
        }
    }

    /**
     * Reads back a CSV statement file using FileInputStream inside try-with-resources.
     */
    public List<Transaction> importFromCsv(File csvFile) throws BankException {
        List<Transaction> result = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(csvFile);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return result;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = parseCsvLine(line);
                if (cols.length >= 7) {
                    result.add(new Transaction(
                            Long.parseLong(cols[0]),
                            cols[1],
                            TransactionType.valueOf(cols[2]),
                            new BigDecimal(cols[3]),
                            new BigDecimal(cols[4]),
                            LocalDateTime.parse(cols[5]),
                            cols[6]
                    ));
                }
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            throw new BankException("Failed to read CSV statement: " + ex.getMessage(), ex);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private void ensureDirectory() {
        if (!statementsDir.exists()) {
            statementsDir.mkdirs();
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}

