package com.financial.price.service;

import com.financial.price.model.PriceRecord;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing financial instrument prices.
 *
 * DESIGN PATTERN: Command Pattern for batch operations with state management.
 *
 * THREAD SAFETY: All methods are thread-safe and can be called concurrently
 * by multiple producers and consumers.
 *
 * USAGE:
 * Producers:
 *   1. startBatchRun() → get batchId
 *   2. uploadChunk(batchId, records) → repeat as needed
 *   3. completeBatchRun(batchId) OR cancelBatchRun(batchId)
 *
 * Consumers:
 *   - getLastPrice(id) → returns latest price from completed batches only
 *
 * @author Your Name
 * @version 1.0.0
 */
public interface PriceService {

    /**
     * Starts a new batch run for uploading prices.
     *
     * This operation:
     * - Creates a new batch in IN_PROGRESS state
     * - Generates a unique batch identifier
     * - Is thread-safe (multiple batches can start concurrently)
     *
     * @return unique batch identifier (e.g., "BATCH-1")
     * @throws IllegalStateException if service is not ready
     */
    String startBatchRun();

    /**
     * Uploads a chunk of price records to an in-progress batch run.
     *
     * DESIGN DECISION: Accepts lists up to recommended chunk size of 1000.
     * Records with duplicate ids within the batch will keep only the
     * latest by asOf time.
     *
     * Behavior:
     * - If an instrument ID appears multiple times in the same batch,
     *   only the record with the most recent asOf timestamp is kept
     * - Records are staged in memory, not yet visible to consumers
     * - Can be called multiple times for the same batch
     *
     * Performance: O(n) where n is the chunk size
     *
     * @param batchId the batch run identifier from startBatchRun()
     * @param records the price records to upload (recommended: max 1000 per call)
     * @throws IllegalArgumentException if batchId is invalid or null
     * @throws IllegalStateException if batch is not in IN_PROGRESS state
     * @throws IllegalArgumentException if records is null
     */
    void uploadChunk(String batchId, List<PriceRecord> records);

    /**
     * Completes a batch run, making all prices available atomically.
     *
     * CRITICAL GUARANTEE: All prices in the batch become visible to consumers
     * at the exact same moment. Consumers never see partial batches.
     *
     * This operation:
     * - Validates batch is in IN_PROGRESS state
     * - Publishes all staged records atomically
     * - For duplicate instrument IDs across batches, keeps the one
     *   with the most recent asOf timestamp
     * - Removes batch from active batches (eligible for garbage collection)
     *
     * Performance: O(m) where m is unique instrument IDs in the batch
     * Uses copy-on-write semantics to ensure readers never see partial updates.
     *
     * @param batchId the batch run identifier
     * @throws IllegalArgumentException if batchId is invalid or null
     * @throws IllegalStateException if batch is not in IN_PROGRESS state
     */
    void completeBatchRun(String batchId);

    /**
     * Cancels a batch run, discarding all uploaded records.
     *
     * This operation:
     * - Validates batch is in IN_PROGRESS state
     * - Discards all staged records (never published to consumers)
     * - Removes batch from active batches (memory reclaimed)
     * - Cannot be called on COMPLETED or already CANCELLED batches
     *
     * Use cases:
     * - Error during batch preparation
     * - Data validation failures
     * - User cancellation
     *
     * @param batchId the batch run identifier
     * @throws IllegalArgumentException if batchId is invalid or null
     * @throws IllegalStateException if batch is already COMPLETED or CANCELLED
     */
    void cancelBatchRun(String batchId);

    /**
     * Retrieves the last price record for a given instrument.
     *
     * IMPORTANT: Returns data from COMPLETED batches only.
     * Records in IN_PROGRESS batches are not visible.
     *
     * "Last" is determined by the asOf timestamp (not upload time).
     * If multiple batches contain the same instrument, the record
     * with the most recent asOf is returned.
     *
     * Performance: O(1) average case lookup using ConcurrentHashMap.
     * This operation is lock-free and never blocks.
     *
     * Thread Safety: Can be called concurrently with all other operations
     * without any performance degradation.
     *
     * @param id the instrument identifier (e.g., "AAPL", "BTC-USD")
     * @return the latest price record wrapped in Optional, or Optional.empty()
     *         if no price exists for this instrument
     */
    Optional<PriceRecord> getLastPrice(String id);

    /**
     * Returns the number of instruments with prices.
     * Useful for monitoring and testing.
     *
     * Only counts instruments from COMPLETED batches.
     *
     * @return count of unique instrument ids with published prices
     */
    int getPriceCount();
}