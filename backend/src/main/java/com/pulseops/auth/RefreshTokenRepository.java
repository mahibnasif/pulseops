package com.pulseops.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select token
			from RefreshToken token
			join fetch token.user
			where token.tokenHash = :tokenHash
			""")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken token
			set token.revokedAt = :revokedAt
			where token.familyId = :familyId
			  and token.revokedAt is null
			""")
	int revokeActiveFamily(
			@Param("familyId") UUID familyId,
			@Param("revokedAt") Instant revokedAt);
}
