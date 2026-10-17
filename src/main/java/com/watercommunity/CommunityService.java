package com.watercommunity;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
class CommunityService {
    private static final char[] TITLE_CHARACTERS =
            "云星月风山海川岛森野光影墨青白赤橙夏秋冬晨暮雨雪雾舟桥花木石泉鹿鹤鲸松竹禾岚汐".toCharArray();

    private final CommunityUserRepository userRepository;
    private final ImageAssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final ProjectLikeRepository likeRepository;
    private final ProjectCommentRepository commentRepository;
    private final UserFollowRepository followRepository;
    private final ProjectShareRepository shareRepository;
    private final WaterBoxTracker tracker;

    CommunityService(
            CommunityUserRepository userRepository,
            ImageAssetRepository assetRepository,
            ProjectRepository projectRepository,
            ProjectLikeRepository likeRepository,
            ProjectCommentRepository commentRepository,
            UserFollowRepository followRepository,
            ProjectShareRepository shareRepository,
            WaterBoxTracker tracker
    ) {
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.projectRepository = projectRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.followRepository = followRepository;
        this.shareRepository = shareRepository;
        this.tracker = tracker;
    }

    @Transactional(readOnly = true)
    List<UserResponse> users(String viewerId) {
        return userRepository.findBySelectableTrueOrderByIdAsc().stream()
                .map(user -> toUserResponse(user, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    List<AssetResponse> assets() {
        return assetRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toAssetResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    List<ProjectResponse> projects(String viewerId, String sort, int page, int size) {
        if (!"latest".equalsIgnoreCase(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "热度排序请使用 /api/project-rankings");
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        List<Project> projects = projectRepository.findAllByOrderByCreatedAtDescIdAsc(pageRequest);
        return projects.stream()
                .map(project -> toProjectResponse(project, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    SimulationCatalog simulationCatalog() {
        return new SimulationCatalog(
                pairs(userRepository.findAllIdsWithNickname()),
                pairs(projectRepository.findAllIdsWithAuthor()),
                pairs(likeRepository.findAllUserProjectPairs()),
                pairs(followRepository.findAllFollowerFollowingPairs())
        );
    }

    @Transactional(readOnly = true)
    void view(String userId, String projectId) {
        CommunityUser user = requireUser(userId);
        Project project = requireProject(projectId);
        tracker.track("front", "view_page", user, Map.of(
                "project_id", projectId,
                "author_user_id", project.getUser().getId()
        ));
    }

    private static List<List<String>> pairs(List<Object[]> rows) {
        return rows.stream()
                .map(row -> List.of((String) row[0], (String) row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    ProfileResponse profile(String userId) {
        CommunityUser user = requireUser(userId);
        List<ProjectResponse> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(project -> toProjectResponse(project, userId))
                .toList();
        long comments = projects.stream().mapToLong(ProjectResponse::commentCount).sum();
        long shares = projects.stream().mapToLong(ProjectResponse::shareCount).sum();
        return new ProfileResponse(
                toUserResponse(user, userId),
                likeRepository.countByProjectUserId(userId),
                comments,
                shares,
                projects
        );
    }

    @Transactional
    ProjectResponse publish(String userId, PublishProjectRequest request) {
        CommunityUser user = requireUser(userId);
        ImageAsset asset = assetRepository.findById(request.imageAssetId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "所选图片不存在"));

        String requestedName = request.name() == null ? "" : request.name().strip();
        String name = requestedName.isEmpty() ? generateUniqueName() : requestedName;
        if (projectRepository.existsByName(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "这个作品名称已经被使用，请换一个");
        }
        String description = request.description() == null ? "" : request.description().strip();

        Project project = projectRepository.save(new Project(user, asset, name, description));
        return toProjectResponse(project, userId);
    }

    @Transactional
    ActionResponse toggleLike(String userId, String projectId) {
        CommunityUser user = requireUser(userId);
        Project project = requireProject(projectId);
        boolean liked = likeRepository.existsByProjectIdAndUserId(projectId, userId);
        if (liked) {
            likeRepository.deleteByProjectIdAndUserId(projectId, userId);
        } else {
            likeRepository.save(new ProjectLike(project, user));
        }
        long count = likeRepository.countByProjectId(projectId);
        tracker.track(liked ? "unlike" : "like", user, Map.of(
                "project_id", projectId,
                "author_user_id", project.getUser().getId()
        ));
        return new ActionResponse(!liked, count, liked ? "已取消点赞" : "已点赞");
    }

    @Transactional
    CommentResponse comment(String userId, String projectId, AddCommentRequest request) {
        CommunityUser user = requireUser(userId);
        Project project = requireProject(projectId);
        ProjectComment comment = commentRepository.save(
                new ProjectComment(project, user, request.content().strip())
        );
        tracker.track("comment", user, Map.of(
                "project_id", projectId,
                "comment_id", comment.getId(),
                "author_user_id", project.getUser().getId()
        ));
        return toCommentResponse(comment);
    }

    @Transactional
    void deleteComment(String userId, String projectId, String commentId) {
        CommunityUser user = requireUser(userId);
        Project project = requireProject(projectId);
        ProjectComment comment = commentRepository.findByIdAndProjectId(commentId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在"));
        String authorId = project.getUser().getId();
        if (!userId.equals(comment.getUser().getId()) && !userId.equals(authorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能删除自己的评论或自己作品下的评论");
        }
        commentRepository.delete(comment);
        tracker.track("delete_comment", user, Map.of(
                "project_id", projectId,
                "comment_id", commentId,
                "author_user_id", authorId
        ));
    }

    @Transactional
    ActionResponse toggleFollow(String userId, String targetUserId, String projectId) {
        if (userId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能关注自己");
        }
        CommunityUser user = requireUser(userId);
        CommunityUser target = requireUser(targetUserId);
        Project project = requireProject(projectId);
        if (!targetUserId.equals(project.getUser().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能在作者的作品下关注作者");
        }
        var existing = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId);
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
        } else {
            followRepository.save(new UserFollow(user, target));
        }
        long count = followRepository.countByFollowingId(targetUserId);
        // The water-box action list has no unfollow, so only new follows are reported.
        if (existing.isEmpty()) {
            tracker.track("follow", user, Map.of(
                    "project_id", projectId,
                    "target_user_id", targetUserId
            ));
        }
        return new ActionResponse(existing.isEmpty(), count, existing.isPresent() ? "已取消关注" : "已关注");
    }

    @Transactional
    ActionResponse share(String userId, String projectId) {
        CommunityUser user = requireUser(userId);
        Project project = requireProject(projectId);
        shareRepository.save(new ProjectShare(project, user));
        tracker.track("share", user, Map.of(
                "project_id", projectId,
                "author_user_id", project.getUser().getId()
        ));
        return new ActionResponse(true, shareRepository.countByProjectId(projectId), "分享已记录");
    }

    private ProjectResponse toProjectResponse(Project project, String viewerId) {
        CommunityUser author = project.getUser();
        boolean followed = viewerId != null
                && !viewerId.equals(author.getId())
                && followRepository.existsByFollowerIdAndFollowingId(viewerId, author.getId());
        List<CommentResponse> comments = commentRepository
                .findTop2ByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream()
                .map(this::toCommentResponse)
                .toList();

        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                new AuthorResponse(
                        author.getId(),
                        author.getNickname(),
                        author.getAvatarPrompt(),
                        author.getAvatarUrl(),
                        author.getAccentColor(),
                        followed
                ),
                toAssetResponse(project.getImageAsset()),
                likeRepository.countByProjectId(project.getId()),
                commentRepository.countByProjectId(project.getId()),
                shareRepository.countByProjectId(project.getId()),
                viewerId != null && likeRepository.existsByProjectIdAndUserId(project.getId(), viewerId),
                comments
        );
    }

    private UserResponse toUserResponse(CommunityUser user, String viewerId) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarPrompt(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getAccentColor(),
                projectRepository.countByUserId(user.getId()),
                followRepository.countByFollowingId(user.getId()),
                followRepository.countByFollowerId(user.getId()),
                viewerId != null
                        && !viewerId.equals(user.getId())
                        && followRepository.existsByFollowerIdAndFollowingId(viewerId, user.getId())
        );
    }

    private AssetResponse toAssetResponse(ImageAsset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getTitle(),
                asset.getImagePrompt(),
                asset.getAltText(),
                asset.getAccentColor()
        );
    }

    private CommentResponse toCommentResponse(ProjectComment comment) {
        CommunityUser author = comment.getUser();
        return new CommentResponse(
                comment.getId(),
                author.getId(),
                author.getNickname(),
                author.getAvatarPrompt(),
                author.getAvatarUrl(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    private CommunityUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在，请重新选择用户"));
    }

    private Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "作品不存在"));
    }

    private String generateUniqueName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 200; attempt++) {
            int first = random.nextInt(TITLE_CHARACTERS.length);
            int second;
            int third;
            do {
                second = random.nextInt(TITLE_CHARACTERS.length);
            } while (second == first);
            do {
                third = random.nextInt(TITLE_CHARACTERS.length);
            } while (third == first || third == second);

            String candidate = new String(new char[]{
                    TITLE_CHARACTERS[first],
                    TITLE_CHARACTERS[second],
                    TITLE_CHARACTERS[third]
            });
            if (!projectRepository.existsByName(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "暂时无法生成可用名称，请手动输入");
    }
}

class ApiException extends RuntimeException {
    private final HttpStatus status;

    ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus getStatus() {
        return status;
    }
}
