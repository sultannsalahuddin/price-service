package com.financial.price.model;

import java.time.Instant;

public class QuickTest {
    public static void main(String[] args) {
        // Create a price record
        PriceRecord record = new PriceRecord(
                "AAPL",
                Instant.now(),
                150.50
        );

        // Print it
        System.out.println("Created: " + record);
        System.out.println("ID: " + record.getId());
        System.out.println("Price: " + record.getPayload());

        System.out.println("\nSetup successful!");
    }
}
