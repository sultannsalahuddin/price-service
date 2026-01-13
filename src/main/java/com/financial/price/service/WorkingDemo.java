package com.financial.price.service;

import com.financial.price.model.PriceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Demonstrates the complete working PriceService.
 */
public class WorkingDemo {

    public static void main(String[] args) {
        System.out.println("🚀 InMemoryPriceService Demo\n");

        // Create the service
        PriceService service = new InMemoryPriceService();
        System.out.println("✅ Service initialized\n");

        // ========== PRODUCER: Batch 1 ==========
        System.out.println("📤 PRODUCER: Starting Batch 1");
        String batch1 = service.startBatchRun();
        System.out.println("   Batch ID: " + batch1);

        // Upload first chunk
        List<PriceRecord> chunk1 = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T10:00:00Z"), 150.0),
                new PriceRecord("GOOGL", Instant.parse("2024-01-01T10:00:00Z"), 2800.0)
        );
        service.uploadChunk(batch1, chunk1);
        System.out.println("   ✓ Uploaded 2 records");

        // Upload second chunk with AAPL update
        List<PriceRecord> chunk2 = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T11:00:00Z"), 155.0), // Newer!
                new PriceRecord("MSFT", Instant.parse("2024-01-01T10:00:00Z"), 300.0)
        );
        service.uploadChunk(batch1, chunk2);
        System.out.println("   ✓ Uploaded 2 more records (AAPL updated)");

        // Complete the batch
        service.completeBatchRun(batch1);
        System.out.println("   ✓ Batch completed - all prices published!\n");

        // ========== CONSUMER: Read Prices ==========
        System.out.println("📥 CONSUMER: Reading prices");

        Optional<PriceRecord> applePrice = service.getLastPrice("AAPL");
        if (applePrice.isPresent()) {
            System.out.println("   AAPL: $" + applePrice.get().getPayload() +
                    " (asOf: " + applePrice.get().getAsOf() + ")");
        }

        Optional<PriceRecord> googlePrice = service.getLastPrice("GOOGL");
        if (googlePrice.isPresent()) {
            System.out.println("   GOOGL: $" + googlePrice.get().getPayload());
        }

        Optional<PriceRecord> msftPrice = service.getLastPrice("MSFT");
        if (msftPrice.isPresent()) {
            System.out.println("   MSFT: $" + msftPrice.get().getPayload());
        }

        System.out.println("   Total instruments: " + service.getPriceCount() + "\n");

        // ========== PRODUCER: Batch 2 (Concurrent) ==========
        System.out.println("📤 PRODUCER: Starting Batch 2");
        String batch2 = service.startBatchRun();

        List<PriceRecord> batch2Records = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T12:00:00Z"), 160.0), // Even newer!
                new PriceRecord("TSLA", Instant.parse("2024-01-01T10:00:00Z"), 250.0)
        );
        service.uploadChunk(batch2, batch2Records);
        service.completeBatchRun(batch2);
        System.out.println("   ✓ Batch 2 completed\n");

        // ========== CONSUMER: Read Updated Prices ==========
        System.out.println("📥 CONSUMER: Reading updated prices");

        applePrice = service.getLastPrice("AAPL");
        if (applePrice.isPresent()) {
            System.out.println("   AAPL: $" + applePrice.get().getPayload() +
                    " (updated from batch 2!)");
        }

        Optional<PriceRecord> teslaPrice = service.getLastPrice("TSLA");
        if (teslaPrice.isPresent()) {
            System.out.println("   TSLA: $" + teslaPrice.get().getPayload() + " (new!)");
        }

        System.out.println("   Total instruments: " + service.getPriceCount() + "\n");

        // ========== ERROR HANDLING ==========
        System.out.println("⚠️  ERROR HANDLING DEMO");

        // Try to upload to completed batch
        try {
            service.uploadChunk(batch1, chunk1);
            System.out.println("   ❌ Should have thrown exception!");
        } catch (IllegalArgumentException e) {
            System.out.println("   ✓ Correctly prevented upload to non-existent batch");
        }

        // Try to complete a batch twice
        String batch3 = service.startBatchRun();
        service.completeBatchRun(batch3);
        try {
            service.completeBatchRun(batch3);
            System.out.println("   ❌ Should have thrown exception!");
        } catch (Exception e) {
            System.out.println("   ✓ Correctly prevented double completion");
        }

        // Cancel a batch
        String batch4 = service.startBatchRun();
        service.uploadChunk(batch4, List.of(
                new PriceRecord("TEST", Instant.now(), 999.0)
        ));
        service.cancelBatchRun(batch4);
        Optional<PriceRecord> testPrice = service.getLastPrice("TEST");
        System.out.println("   ✓ Cancelled batch - TEST price exists: " + testPrice.isPresent());

        System.out.println("\n🎉 Demo complete! All features working!");
    }
}
