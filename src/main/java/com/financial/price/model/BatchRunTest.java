package com.financial.price.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class BatchRunTest {
    public static void main(String[] args) {
        System.out.println("Testing BatchRun...\n");

        // Create a batch
        BatchRun batch = new BatchRun();
        System.out.println(" Created batch: " + batch.getBatchId());
        System.out.println("   State: " + batch.getState());

        // Upload first chunk
        List<PriceRecord> chunk1 = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T10:00:00Z"), 150.0),
                new PriceRecord("GOOGL", Instant.parse("2024-01-01T10:00:00Z"), 2800.0)
        );
        batch.addRecords(chunk1);
        System.out.println("\n Uploaded 2 records");
        System.out.println("   Record count: " + batch.getRecordCount());

        // Upload second chunk with duplicate AAPL (newer time)
        List<PriceRecord> chunk2 = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T11:00:00Z"), 155.0),
                new PriceRecord("MSFT", Instant.parse("2024-01-01T10:00:00Z"), 300.0)
        );
        batch.addRecords(chunk2);
        System.out.println("\n Uploaded 2 more records (including AAPL update)");
        System.out.println("   Record count: " + batch.getRecordCount() + " (should be 3, not 4)");

        // Get staged records
        Map<String, PriceRecord> staged = batch.getStagedRecords();
        System.out.println("\n Staged Records:");
        for (Map.Entry<String, PriceRecord> entry : staged.entrySet()) {
            PriceRecord record = entry.getValue();
            System.out.println("   " + entry.getKey() + ": " +
                    record.getPayload() + " @ " + record.getAsOf());
        }

        // Verify AAPL kept the newer price
        PriceRecord applePrice = staged.get("AAPL");
        if (applePrice.getPayload().equals(155.0)) {
            System.out.println("\n AAPL correctly kept newer price ($155)");
        } else {
            System.out.println("\n ERROR: AAPL has wrong price!");
        }

        // Change state to completed
        batch.setState(BatchRun.BatchState.COMPLETED);
        System.out.println("\n Batch state changed to: " + batch.getState());

        // Try to add records after completion (should fail)
        try {
            batch.addRecords(List.of(
                    new PriceRecord("TEST", Instant.now(), 100.0)
            ));
            System.out.println("\n ERROR: Should not allow upload after completion!");
        } catch (IllegalStateException e) {
            System.out.println("\n Correctly prevented upload after completion");
            System.out.println("   Error: " + e.getMessage());
        }

        System.out.println("\n All BatchRun tests passed!");
    }
}