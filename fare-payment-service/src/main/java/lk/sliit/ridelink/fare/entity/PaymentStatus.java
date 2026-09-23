package lk.sliit.ridelink.fare.entity;

/**
 * PENDING  - ride completed, final fare calculated, not paid yet
 * PAID     - simulated payment succeeded; a receipt is available (final state)
 * FAILED   - last simulated attempt was declined; the passenger may retry
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED
}
