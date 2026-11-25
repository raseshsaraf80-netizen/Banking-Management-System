// Author: Sahil Raj
// Concept: JUnit 5 tests for File I/O (StatementService export/import and BankLogger)
package com.bank;

import com.bank.model.Transaction;
import com.bank.model.TransactionType;
import com.bank.service.StatementService;
import com.bank.util.BankLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportTxtCsvAndImportCsvRoundTrip() throws Exception {
        StatementService service = new StatementService(tempDir.resolve("statements").toString());
        List<Transaction> txns = List.of(
                new Transaction(1L, "SB100001", TransactionType.DEPOSIT,
                        new BigDecimal("15000.00"), new BigDecimal("15000.00"),
                        LocalDateTime.of(2026, 11, 20, 10, 30), "Opening deposit"),
                new Transaction(2L, "SB100001", TransactionType.WITHDRAWAL,
                        new BigDecimal("500.00"), new BigDecimal("14500.00"),
                        LocalDateTime.of(2026, 11, 21, 14, 15), "ATM \"Main\" Branch")
        );

        File txtFile = service.exportToTxt("SB100001", txns);
        File csvFile = service.exportToCsv("SB100001", txns);

        assertTrue(txtFile.exists());
        assertTrue(csvFile.exists());
        assertTrue(Files.readString(txtFile.toPath()).contains("SRM TRUST BANK"));

        List<Transaction> imported = service.importFromCsv(csvFile);
        assertEquals(2, imported.size());
        assertEquals(new BigDecimal("14500.00"), imported.get(1).getBalanceAfter());
        assertEquals("ATM \"Main\" Branch", imported.get(1).getDescription());
    }

    @Test
    void bankLoggerAppendsLinesToLogFile() throws Exception {
        Path logPath = tempDir.resolve("logs/bank.log");
        BankLogger logger = new BankLogger(logPath.toString());
        logger.log("TEST_EVENT SB100001 +100.00");
        logger.log("TEST_EVENT SB100001 -50.00");

        List<String> lines = Files.readAllLines(logPath);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("TEST_EVENT"));
    }
}

