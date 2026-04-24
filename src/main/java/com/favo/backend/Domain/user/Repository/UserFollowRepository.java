package com.favo.backend.Domain.user.Repository;

import com.favo.backend.Domain.user.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    List<UserFollow> findByFollower_IdAndIsActiveTrueOrderByCreatedAtDesc(Long followerId, Pageable pageable);

    Optional<UserFollow> findByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);

    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.followee.id = :followeeId AND uf.isActive = true " +
           "AND uf.follower.isActive = true AND uf.followee.isActive = true")
    long countActiveFollowers(@Param("followeeId") Long followeeId);

    @Query("SELECT COUNT(uf) FROM UserFollow uf " +
           "WHERE uf.follower.id = :followerId AND uf.isActive = true " +
           "AND uf.follower.isActive = true AND uf.followee.isActive = true")
    long countActiveFollowing(@Param("followerId") Long followerId);

    @Query("SELECT uf FROM UserFollow uf WHERE uf.followee.id = :userId AND uf.isActive = true " +
           "AND uf.follower.isActive = true AND uf.followee.isActive = true ORDER BY uf.createdAt DESC")
    Page<UserFollow> findFollowersPage(@Param("userId") Long followeeId, Pageable pageable);

    @Query("SELECT uf FROM UserFollow uf WHERE uf.follower.id = :userId AND uf.isActive = true " +
           "AND uf.follower.isActive = true AND uf.followee.isActive = true ORDER BY uf.createdAt DESC")
    Page<UserFollow> findFollowingPage(@Param("userId") Long followerId, Pageable pageable);

    @Query("SELECT uf.followee.id FROM UserFollow uf WHERE uf.follower.id = :followerId AND uf.isActive = true")
    List<Long> findActiveFolloweeIds(@Param("followerId") Long followerId);

    @Query("SELECT uf.followee.id AS followeeId, COUNT(uf) AS followerCount " +
           "FROM UserFollow uf " +
           "WHERE uf.followee.id IN :followeeIds AND uf.isActive = true " +
           "AND uf.follower.isActive = true AND uf.followee.isActive = true " +
           "GROUP BY uf.followee.id")
    List<FolloweeFollowerCountProjection> countActiveFollowersByFolloweeIds(@Param("followeeIds") List<Long> followeeIds);

    boolean existsByFollower_IdAndFollowee_IdAndIsActiveTrue(Long followerId, Long followeeId);
}
