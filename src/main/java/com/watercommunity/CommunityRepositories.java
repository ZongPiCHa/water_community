package com.watercommunity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface CommunityUserRepository extends JpaRepository<CommunityUser, String> {
    List<CommunityUser> findAllByOrderByIdAsc();
}

interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
    List<ImageAsset> findAllByOrderBySortOrderAsc();
}

interface ProjectRepository extends JpaRepository<Project, String> {
    @EntityGraph(attributePaths = {"user", "imageAsset"})
    List<Project> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "imageAsset"})
    List<Project> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByName(String name);

    long countByUserId(String userId);
}

interface ProjectLikeRepository extends JpaRepository<ProjectLike, String> {
    long countByProjectId(String projectId);

    long countByProjectUserId(String userId);

    boolean existsByProjectIdAndUserId(String projectId, String userId);

    void deleteByProjectIdAndUserId(String projectId, String userId);
}

interface ProjectCommentRepository extends JpaRepository<ProjectComment, String> {
    long countByProjectId(String projectId);

    List<ProjectComment> findTop2ByProjectIdOrderByCreatedAtDesc(String projectId);
}

interface UserFollowRepository extends JpaRepository<UserFollow, String> {
    long countByFollowingId(String userId);

    long countByFollowerId(String userId);

    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(String followerId, String followingId);
}

interface ProjectShareRepository extends JpaRepository<ProjectShare, String> {
    long countByProjectId(String projectId);
}
