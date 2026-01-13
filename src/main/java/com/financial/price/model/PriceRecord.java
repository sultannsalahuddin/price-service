package com.financial.price.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable price record for financial instruments.
 * Thread-safe by design - once created, cannot be modified.

 * Design Decision: Immutability eliminates entire classes of concurrency bugs.
 * No synchronization needed because state never changes after construction.
 */
public final class PriceRecord {
    private final String id;
    private final Instant asOf;
    private final Object payload;

    /**
     * Creates a new price record.
     *
     * @param id instrument identifier
     * @param asOf timestamp when this price was determined
     * @param payload the price data (flexible structure)
     * @throws NullPointerException if any parameter is null
     */
    public PriceRecord(String id, Instant asOf, Object payload) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.asOf = Objects.requireNonNull(asOf, "asOf cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload cannot be null");
    }

    public String getId() {
        return id;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PriceRecord that = (PriceRecord) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(asOf, that.asOf) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, asOf, payload);
    }

    @Override
    public String toString() {
        return "PriceRecord{" +
                "id='" + id + '\'' +
                ", asOf=" + asOf +
                ", payload=" + payload +
                '}';
    }
}