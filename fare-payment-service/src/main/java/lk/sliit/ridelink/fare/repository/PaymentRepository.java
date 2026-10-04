package lk.sliit.ridelink.fare.repository;

import lk.sliit.ridelink.fare.entity.Payment;
import lk.sliit.ridelink.fare.entity.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByRideId(String rideId);

    List<Payment> findByPassengerIdOrderByCreatedAtDesc(String passengerId);

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<Payment> findAllByOrderByCreatedAtDesc();
}
