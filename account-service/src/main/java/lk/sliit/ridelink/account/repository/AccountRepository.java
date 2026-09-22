package lk.sliit.ridelink.account.repository;

import lk.sliit.ridelink.account.entity.Account;
import lk.sliit.ridelink.account.entity.AccountStatus;
import lk.sliit.ridelink.account.entity.Role;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends MongoRepository<Account, String> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Account> findByRole(Role role);

    List<Account> findByStatus(AccountStatus status);

    List<Account> findByRoleAndStatus(Role role, AccountStatus status);
}
