package com.mirattic.flow.user.repository;

import com.mirattic.flow.user.entity.Withdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, String> {

    /** 행이 없으면 경계 0(= 제한 없음)으로 만든다. 잠글 행이 늘 있게 (UserService.lockUid). */
    @Modifying
    @Query(value = "insert ignore into withdrawals (mirattic_uid_hash, withdrawn_at) values (:hash, 0)", nativeQuery = true)
    void ensureRow(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Withdrawal w where w.miratticUidHash = :hash")
    Withdrawal lock(@Param("hash") String hash);
}
