package com.financial.price.service;

import com.financial.price.model.PriceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for InMemoryPriceService.
 *
 * TEST CATEGORIES:
 * 1. Basic Functionality - Happy path scenarios
 * 2. Latest Price Logic - Timestamp-based resolution
 * 3. Error Handling - Invalid operations
 * 4. Concurrency - Multi-threaded scenarios
 * 5. Edge Cases - Boundary conditions
 *
 * These tests verify:
 * - Functional correctness
 * - Thread safety
 * - State machine validity
 * - Performance characteristics
 * - Error resilience
 */
class InMemoryPriceServiceTest {

    private PriceService service;

    /**
     * Creates a fresh service instance before each test.
     * Ensures test isolation (no shared state between tests).
     */
    @BeforeEach
    void setUp() {
        service = new InMemoryPriceService();
    }

    // ==================================================================================
    // BASIC FUNCTIONALITY TESTS
    // ==================================================================================

    @Test
    @DisplayName("Should start batch run and return valid batch ID")
    void testStartBatchRun() {
        String batchId = service.startBatchRun();

        assertNotNull(batchId, "Batch ID should not be null");
        assertFalse(batchId.isEmpty(), "Batch ID should not be empty");
        assertTrue(batchId.startsWith("BATCH-"), "Batch ID should have correct prefix");
    }

    @Test
    @DisplayName("Should upload chunk and complete batch successfully")
    void testUploadAndCompleteBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        List<PriceRecord> records = List.of(
                new PriceRecord("AAPL", Instant.parse("2024-01-01T10:00:00Z"), 150.0),
                new PriceRecord("GOOGL", Instant.parse("2024-01-01T10:00:00Z"), 2800.0)
        );

        // Act
        service.uploadChunk(batchId, records);
        service.completeBatchRun(batchId);

        // Assert
        Optional<PriceRecord> applePrice = service.getLastPrice("AAPL");
        assertTrue(applePrice.isPresent(), "AAPL price should exist");
        assertEquals(150.0, applePrice.get().getPayload(), "AAPL price should match");
        assertEquals(2, service.getPriceCount(), "Should have 2 instruments");
    }

    @Test
    @DisplayName("Should make all batch prices available atomically on completion")
    void testAtomicPublication() {
        // Arrange
        String batchId = service.startBatchRun();
        List<PriceRecord> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            records.add(new PriceRecord("INST-" + i, Instant.now(), 100.0 + i));
        }

        service.uploadChunk(batchId, records);

        // Assert: Before completion, no prices should be available
        assertEquals(0, service.getPriceCount(), "No prices should be visible before completion");

        // Act
        service.completeBatchRun(batchId);

        // Assert: After completion, all prices should be available
        assertEquals(100, service.getPriceCount(), "All 100 prices should be visible after completion");
    }

    @Test
    @DisplayName("Should cancel batch run and discard records")
    void testCancelBatchRun() {
        // Arrange
        String batchId = service.startBatchRun();
        List<PriceRecord> records = List.of(
                new PriceRecord("MSFT", Instant.now(), 300.0)
        );

        // Act
        service.uploadChunk(batchId, records);
        service.cancelBatchRun(batchId);

        // Assert
        Optional<PriceRecord> price = service.getLastPrice("MSFT");
        assertFalse(price.isPresent(), "Cancelled batch records should not be published");
        assertEquals(0, service.getPriceCount(), "Price count should be 0");
    }

    // ==================================================================================
    // LATEST PRICE LOGIC TESTS
    // ==================================================================================

    @Test
    @DisplayName("Should keep only the latest price by asOf time within a batch")
    void testLatestPriceWithinBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        Instant t1 = Instant.parse("2024-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2024-01-01T11:00:00Z");
        Instant t3 = Instant.parse("2024-01-01T09:00:00Z");

        // Act: Upload in mixed order
        service.uploadChunk(batchId, List.of(
                new PriceRecord("AAPL", t1, 150.0)
        ));
        service.uploadChunk(batchId, List.of(
                new PriceRecord("AAPL", t2, 155.0)  // Later time
        ));
        service.uploadChunk(batchId, List.of(
                new PriceRecord("AAPL", t3, 145.0)  // Earlier time
        ));
        service.completeBatchRun(batchId);

        // Assert: Should keep the one with latest timestamp
        Optional<PriceRecord> price = service.getLastPrice("AAPL");
        assertTrue(price.isPresent());
        assertEquals(155.0, price.get().getPayload(), "Should keep price with latest asOf");
        assertEquals(t2, price.get().getAsOf(), "Should have correct timestamp");
    }

    @Test
    @DisplayName("Should keep latest price across multiple completed batches")
    void testLatestPriceAcrossBatches() {
        // Arrange
        Instant t1 = Instant.parse("2024-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2024-01-01T12:00:00Z");
        Instant t3 = Instant.parse("2024-01-01T11:00:00Z");

        // Act: Batch 1
        String batch1 = service.startBatchRun();
        service.uploadChunk(batch1, List.of(
                new PriceRecord("AAPL", t1, 150.0)
        ));
        service.completeBatchRun(batch1);

        // Act: Batch 2 with later time
        String batch2 = service.startBatchRun();
        service.uploadChunk(batch2, List.of(
                new PriceRecord("AAPL", t2, 160.0)
        ));
        service.completeBatchRun(batch2);

        // Act: Batch 3 with earlier time (should be ignored)
        String batch3 = service.startBatchRun();
        service.uploadChunk(batch3, List.of(
                new PriceRecord("AAPL", t3, 155.0)
        ));
        service.completeBatchRun(batch3);

        // Assert: Should keep batch 2's price (latest timestamp)
        Optional<PriceRecord> price = service.getLastPrice("AAPL");
        assertTrue(price.isPresent());
        assertEquals(160.0, price.get().getPayload(), "Should keep latest across batches");
        assertEquals(t2, price.get().getAsOf());
    }

    // ==================================================================================
    // ERROR HANDLING TESTS
    // ==================================================================================

    @Test
    @DisplayName("Should throw exception when uploading to invalid batch")
    void testUploadToInvalidBatch() {
        List<PriceRecord> records = List.of(
                new PriceRecord("AAPL", Instant.now(), 150.0)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadChunk("INVALID-BATCH", records)
        );

        assertTrue(exception.getMessage().contains("Unknown batch id"));
    }

    @Test
    @DisplayName("Should throw exception when completing invalid batch")
    void testCompleteInvalidBatch() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.completeBatchRun("INVALID-BATCH")
        );

        assertTrue(exception.getMessage().contains("Unknown batch id"));
    }

    @Test
    @DisplayName("Should throw exception when uploading to completed batch")
    void testUploadToCompletedBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        service.uploadChunk(batchId, List.of(
                new PriceRecord("AAPL", Instant.now(), 150.0)
        ));
        service.completeBatchRun(batchId);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadChunk(batchId, List.of(
                        new PriceRecord("GOOGL", Instant.now(), 2800.0)
                ))
        );

        assertTrue(exception.getMessage().contains("Unknown batch id"));
    }

    @Test
    @DisplayName("Should throw exception when completing already completed batch")
    void testCompleteAlreadyCompletedBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        service.completeBatchRun(batchId);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> service.completeBatchRun(batchId)
        );
    }

    @Test
    @DisplayName("Should throw exception when cancelling completed batch")
    void testCancelCompletedBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        service.completeBatchRun(batchId);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancelBatchRun(batchId)
        );
    }

    @Test
    @DisplayName("Should throw exception when cancelling already cancelled batch")
    void testCancelAlreadyCancelledBatch() {
        // Arrange
        String batchId = service.startBatchRun();
        service.cancelBatchRun(batchId);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancelBatchRun(batchId)
        );
    }

    @Test
    @DisplayName("Should handle null and empty parameters gracefully")
    void testNullAndEmptyParameters() {
        // Test null batchId
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadChunk(null, List.of()));

        // Test empty batchId
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadChunk("", List.of()));

        // Test null records
        String batchId = service.startBatchRun();
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadChunk(batchId, null));

        // Test null instrument id
        Optional<PriceRecord> price = service.getLastPrice(null);
        assertFalse(price.isPresent());

        // Test empty instrument id
        price = service.getLastPrice("");
        assertFalse(price.isPresent());
    }

    // ==================================================================================
    // CONCURRENCY TESTS
    // ==================================================================================

    @Test
    @DisplayName("Should handle multiple concurrent batch uploads")
    void testConcurrentBatchUploads() throws InterruptedException {
        // Arrange
        int batchCount = 10;
        int recordsPerBatch = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(batchCount);

        // Act: Launch 10 threads, each uploading a batch
        for (int b = 0; b < batchCount; b++) {
            int batchNum = b;
            executor.submit(() -> {
                try {
                    String batchId = service.startBatchRun();
                    List<PriceRecord> records = new ArrayList<>();

                    for (int i = 0; i < recordsPerBatch; i++) {
                        records.add(new PriceRecord(
                                "INST-" + batchNum + "-" + i,
                                Instant.now().plusSeconds(i),
                                100.0 + i
                        ));
                    }

                    service.uploadChunk(batchId, records);
                    service.completeBatchRun(batchId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert: All batches complete successfully
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All batches should complete within 10 seconds");
        executor.shutdown();

        assertEquals(batchCount * recordsPerBatch, service.getPriceCount(),
                "All instruments from all batches should be published");
    }

    @Test
    @DisplayName("Should allow concurrent reads while batches are being uploaded")
    void testConcurrentReadsAndWrites() throws InterruptedException {
        // Arrange: Pre-populate with some prices
        String initBatch = service.startBatchRun();
        service.uploadChunk(initBatch, List.of(
                new PriceRecord("AAPL", Instant.now(), 150.0),
                new PriceRecord("GOOGL", Instant.now(), 2800.0)
        ));
        service.completeBatchRun(initBatch);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger successfulReads = new AtomicInteger(0);

        // Act: 10 reader threads
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        Optional<PriceRecord> price = service.getLastPrice("AAPL");
                        if (price.isPresent()) {
                            successfulReads.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Act: 10 writer threads
        for (int i = 0; i < 10; i++) {
            int writerNum = i;
            executor.submit(() -> {
                try {
                    String batchId = service.startBatchRun();
                    service.uploadChunk(batchId, List.of(
                            new PriceRecord("MSFT-" + writerNum, Instant.now(), 300.0)
                    ));
                    service.completeBatchRun(batchId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Assert
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        assertEquals(1000, successfulReads.get(), "All reads should succeed");
        assertEquals(12, service.getPriceCount(), "Should have 2 initial + 10 new instruments");
    }

    @Test
    @DisplayName("Should handle large batch with multiple chunks")
    void testLargeBatchMultipleChunks() {
        // Arrange
        String batchId = service.startBatchRun();

        // Act: Upload 5 chunks of 1000 records each
        for (int chunk = 0; chunk < 5; chunk++) {
            List<PriceRecord> records = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                int id = chunk * 1000 + i;
                records.add(new PriceRecord(
                        "INST-" + id,
                        Instant.now().plusSeconds(id),
                        100.0 + id
                ));
            }
            service.uploadChunk(batchId, records);
        }

        service.completeBatchRun(batchId);

        // Assert
        assertEquals(5000, service.getPriceCount(), "Should have 5000 instruments");

        // Verify a sample of prices
        Optional<PriceRecord> price = service.getLastPrice("INST-0");
        assertTrue(price.isPresent());
        assertEquals(100.0, price.get().getPayload());

        price = service.getLastPrice("INST-4999");
        assertTrue(price.isPresent());
        assertEquals(5099.0, price.get().getPayload());
    }

    // ==================================================================================
    // EDGE CASES
    // ==================================================================================

    @Test
    @DisplayName("Should handle empty batch completion")
    void testEmptyBatchCompletion() {
        // Act
        String batchId = service.startBatchRun();
        service.completeBatchRun(batchId);

        // Assert
        assertEquals(0, service.getPriceCount(), "Empty batch should not add any prices");
    }

    @Test
    @DisplayName("Should return empty for non-existent instrument")
    void testGetNonExistentPrice() {
        Optional<PriceRecord> price = service.getLastPrice("NON-EXISTENT");
        assertFalse(price.isPresent(), "Non-existent instrument should return empty");
    }

    @Test
    @DisplayName("Should handle flexible payload types")
    void testFlexiblePayload() {
        // Arrange
        String batchId = service.startBatchRun();

        service.uploadChunk(batchId, List.of(
                new PriceRecord("DOUBLE", Instant.now(), 150.0),
                new PriceRecord("STRING", Instant.now(), "150.00 USD"),
                new PriceRecord("MAP", Instant.now(),
                        java.util.Map.of("bid", 149.5, "ask", 150.5))
        ));

        service.completeBatchRun(batchId);

        // Assert
        assertEquals(3, service.getPriceCount());

        Optional<PriceRecord> doublePrice = service.getLastPrice("DOUBLE");
        assertTrue(doublePrice.isPresent());
        assertTrue(doublePrice.get().getPayload() instanceof Double);

        Optional<PriceRecord> stringPrice = service.getLastPrice("STRING");
        assertTrue(stringPrice.isPresent());
        assertTrue(stringPrice.get().getPayload() instanceof String);

        Optional<PriceRecord> mapPrice = service.getLastPrice("MAP");
        assertTrue(mapPrice.isPresent());
        assertTrue(mapPrice.get().getPayload() instanceof java.util.Map);
    }

    @Test
    @DisplayName("Should generate unique batch IDs")
    void testUniqueBatchIds() {
        // Act: Create 100 batches
        List<String> batchIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            batchIds.add(service.startBatchRun());
        }

        // Assert: All IDs should be unique
        long uniqueCount = batchIds.stream().distinct().count();
        assertEquals(100, uniqueCount, "All batch IDs should be unique");
    }

    // ==================================================================================
    // PERFORMANCE BENCHMARK TESTS
    // ==================================================================================

    @Test
    @DisplayName("Performance: Should handle high throughput")
    void testPerformance() {
        // Arrange
        int instrumentCount = 10000;
        long startTime = System.nanoTime();

        String batchId = service.startBatchRun();

        // Act: Upload 10,000 instruments
        List<PriceRecord> records = new ArrayList<>();
        for (int i = 0; i < instrumentCount; i++) {
            records.add(new PriceRecord(
                    "PERF-" + i,
                    Instant.now().plusSeconds(i),
                    100.0 + i
            ));
        }
        service.uploadChunk(batchId, records);
        service.completeBatchRun(batchId);

        long uploadTime = System.nanoTime() - startTime;

        // Act: Read 10,000 prices
        startTime = System.nanoTime();
        for (int i = 0; i < instrumentCount; i++) {
            service.getLastPrice("PERF-" + i);
        }
        long readTime = System.nanoTime() - startTime;

        // Assert & Report
        System.out.println("\n PERFORMANCE METRICS:");
        System.out.println("   Upload + Completion: " + (uploadTime / 1_000_000) + " ms");
        System.out.println("   10,000 Reads: " + (readTime / 1_000_000) + " ms");
        System.out.println("   Avg Read Time: " + (readTime / instrumentCount) + " ns");
        System.out.println("   Throughput: " + (instrumentCount * 1_000_000_000L / readTime) + " reads/sec");

        // Sanity check
        assertTrue(uploadTime < 5_000_000_000L, "Upload should complete in < 5 seconds");
        assertTrue(readTime / instrumentCount < 10000, "Each read should take < 10 microseconds");
    }

}
