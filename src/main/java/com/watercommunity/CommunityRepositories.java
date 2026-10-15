package com.watercommunity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CommunityUserRepository extends JpaRepository<CommunityUser, Long> {
    List<CommunityUser> findAllByOrderByIdAsc();
}

interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {
    List<ImageAsset> findAllByOrderBySortOrderAsc();
}

interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = {"user", "imageAsset"})
    List<Project> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "imageAsset"})
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByName(String name);

    long countByUserId(Long userId);
}

interface ProjectLikeRepository extends JpaRepository<ProjectLike, Long> {
    long countByProjectId(Long projectId);

    long countByProjectUserId(Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    void deleteByProjectIdAndUserId(Long projectId, Long userId);
}

interface ProjectCommentRepository extends JpaRepository<ProjectComment, Long> {
    long countByProjectId(Long projectId);

    List<ProjectComment> findTop2ByProjectIdOrderByCreatedAtDesc(Long projectId);
}

interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    long countByFollowingId(Long userId);

    long countByFollowerId(Long userId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);
}

interface ProjectShareRepository extends JpaRepository<ProjectShare, Long> {
    long countByProjectId(Long projectId);
}
