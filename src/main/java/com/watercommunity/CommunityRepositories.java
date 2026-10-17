package com.watercommunity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface CommunityUserRepository extends JpaRepository<CommunityUser, String> {
    List<CommunityUser> findBySelectableTrueOrderByIdAsc();

    @Query("select u.id, u.nickname from CommunityUser u order by u.id")
    List<Object[]> findAllIdsWithNickname();
}

interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
    List<ImageAsset> findAllByOrderBySortOrderAsc();
}

interface ProjectRepository extends JpaRepository<Project, String> {
    @EntityGraph(attributePaths = {"user", "imageAsset"})
    List<Project> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select p from Project p left join ProjectLike l on l.project = p
            group by p
            order by count(l) desc, p.createdAt desc
            """)
    List<Project> findPopular(Pageable pageable);

    @Query("select p.id, p.user.id from Project p order by p.id")
    List<Object[]> findAllIdsWithAuthor();

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

    @Query("select l.user.id, l.project.id from ProjectLike l")
    List<Object[]> findAllUserProjectPairs();
}

interface ProjectCommentRepository extends JpaRepository<ProjectComment, String> {
    long countByProjectId(String projectId);

    List<ProjectComment> findTop2ByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<ProjectComment> findByIdAndProjectId(String id, String projectId);
}

interface UserFollowRepository extends JpaRepository<UserFollow, String> {
    long countByFollowingId(String userId);

    long countByFollowerId(String userId);

    boolean existsByFollowerIdAndFollowingId(String followerId, String followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(String followerId, String followingId);

    @Query("select f.follower.id, f.following.id from UserFollow f")
    List<Object[]> findAllFollowerFollowingPairs();
}

interface ProjectShareRepository extends JpaRepository<ProjectShare, String> {
    long countByProjectId(String projectId);
}
