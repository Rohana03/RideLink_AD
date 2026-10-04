package lk.sliit.ridelink.fare.service;

import lk.sliit.ridelink.fare.dto.PaymentRequest;
import lk.sliit.ridelink.fare.dto.PaymentResponse;
import lk.sliit.ridelink.fare.dto.ReceiptResponse;
import lk.sliit.ridelink.fare.dto.RideCompletedEvent;
import lk.sliit.ridelink.fare.entity.PaymentStatus;

import java.util.List;

public interface PaymentService {

    /**
     * Calculates the final fare and opens a PENDING payment. Idempotent: a repeated
     * event for the same ride returns the existing payment unchanged.
     */
    PaymentResponse recordCompletedRide(RideCompletedEvent event);

    PaymentResponse pay(String rideId, String passengerId, PaymentRequest request);

    /** Visible to the ride's passenger, the ride's driver, or an admin. */
    PaymentResponse getPayment(String rideId, String callerId, boolean callerIsAdmin);

    List<PaymentResponse> getPaymentsForPassenger(String passengerId);

    /** Status is optional; null means all payments. */
    List<PaymentResponse> listPayments(PaymentStatus status);

    /** Available only once the payment is PAID. Same visibility as {@link #getPayment}. */
    ReceiptResponse getReceipt(String rideId, String callerId, boolean callerIsAdmin);
}
