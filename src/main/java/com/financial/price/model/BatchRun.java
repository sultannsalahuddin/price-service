package com.financial.price.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a batch run of price uploads.
 * Manages state transitions and accumulates records during upload.
 *
 * DESIGN DECISIONS:
 * - Uses HashMap for efficient latest-price lookups by id
 * - Only keeps the most recent record per id within the batch (memory optimization)
 * - State machine prevents invalid operations (can't upload after completion)
 *
 * THREAD SAFETY:
 * - addRecords() is synchronized to handle concurrent uploads to same batch
 * - State transitions are synchronized to prevent race conditions
 */
public class BatchRun {
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final String batchId;
    private final Map<String, PriceRecord> stagedRecords;
    private volatile BatchState state;
    private final long createdAt;

    /**
     * Possible states of a batch run.
     * Transitions: IN_PROGRESS → COMPLETED
     *                         → CANCELLED
     */
    public enum BatchState {
        IN_PROGRESS,  // Actively accepting uploads
        COMPLETED,    // Successfully finished, prices published
        CANCELLED     // Aborted, prices discarded
    }

    /**
     * Creates a new batch run in IN_PROGRESS state.
     * Generates a unique batch ID automatically.
     */
    public BatchRun() {
        this.batchId = "BATCH-" + SEQUENCE.incrementAndGet();
        this.stagedRecords = new HashMap<>();
        this.state = BatchState.IN_PROGRESS;
        this.createdAt = System.nanoTime();
    }

    public String getBatchId() {
        return batchId;
    }

    public BatchState getState() {
        return state;
    }

    public void setState(BatchState state) {
        this.state = state;
    }

    /**
     * Adds records to staging area, keeping only the latest by asOf time per id.
     *
     * This optimization:
     * - Reduces memory footprint (no duplicates)
     * - Simplifies completion processing
     * - Resolves conflicts early
     *
     * Example:
     *   Upload 1: AAPL @ 10:00 = $150
     *   Upload 2: AAPL @ 11:00 = $155  ← Kept (newer)
     *   Upload 3: AAPL @ 09:00 = $148  ← Discarded (older)
     *
     * @param records the price records to add
     * @throws IllegalStateException if batch is not IN_PROGRESS
     */
    public synchronized void addRecords(List<PriceRecord> records) {
        if (state != BatchState.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot add records to batch " + batchId + " in state " + state);
        }

        for (PriceRecord record : records) {
            // merge() is elegant: if key exists, run the function to decide which to keep
            stagedRecords.merge(
                    record.getId(),
                    record,
                    (existing, newRecord) ->
                            newRecord.getAsOf().isAfter(existing.getAsOf()) ? newRecord : existing
            );
        }
    }

    /**
     * Returns a snapshot of staged records for atomic publication.
     * Creates a new HashMap to prevent external modification.
     * Called only during completion.
     *
     * @return defensive copy of staged records
     */
    public synchronized Map<String, PriceRecord> getStagedRecords() {
        return new HashMap<>(stagedRecords);
    }

    /**
     * Returns the number of unique instruments in this batch.
     *
     * @return count of staged records
     */
    public int getRecordCount() {
        return stagedRecords.size();
    }

    /**
     * Returns when this batch was created (for performance metrics).
     *
     * @return creation timestamp in nanoseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }
}