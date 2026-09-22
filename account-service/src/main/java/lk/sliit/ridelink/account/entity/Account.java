package lk.sliit.ridelink.account.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    /** Stable account ID; other services store it as userId (e.g. Driver.userId). */
    @Id
    private String id;

    /** Optimistic lock: a concurrent save of a stale copy fails instead of overwriting. */
    @Version
    private Long version;

    /** Stored trimmed and lower-case so the unique index is case-insensitive. */
    @Indexed(unique = true)
    private String email;

    /** BCrypt hash; the plain password is never stored or returned. */
    private String passwordHash;

    private String fullName;

    private String phoneNumber;

    private Role role;

    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    private LocalDateTime lastLoginAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
