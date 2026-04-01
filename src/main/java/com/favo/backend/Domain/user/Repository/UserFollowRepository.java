package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.UserFollow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    List<UserFollow> findByFollowerIdAndIsActiveTrueOrderByCreatedAtDesc(Long followerId, Pageable pageable);
}
