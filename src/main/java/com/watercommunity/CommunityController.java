package com.watercommunity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api")
class CommunityController {
    private final CommunityService communityService;

    CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/users")
    List<UserResponse> users(
            @RequestHeader(value = "X-User-Id", required = false) String viewerId
    ) {
        return communityService.users(viewerId);
    }

    @GetMapping("/assets")
    List<AssetResponse> assets() {
        return communityService.assets();
    }

    @GetMapping("/projects")
    List<ProjectResponse> projects(
            @RequestHeader(value = "X-User-Id", required = false) String viewerId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return communityService.projects(viewerId, sort, page, size);
    }

    // Lightweight id lists so the traffic simulator can act on real users and projects.
    @GetMapping("/simulation/catalog")
    SimulationCatalog simulationCatalog() {
        return communityService.simulationCatalog();
    }

    @PostMapping("/projects/{projectId}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void view(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String projectId
    ) {
        communityService.view(userId, projectId);
    }

    @GetMapping("/me")
    ProfileResponse profile(@RequestHeader("X-User-Id") String userId) {
        return communityService.profile(userId);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    ProjectResponse publish(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody PublishProjectRequest request
    ) {
        return communityService.publish(userId, request);
    }

    @PostMapping("/projects/{projectId}/likes")
    ActionResponse toggleLike(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String projectId
    ) {
        return communityService.toggleLike(userId, projectId);
    }

    @PostMapping("/projects/{projectId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse comment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String projectId,
            @Valid @RequestBody AddCommentRequest request
    ) {
        return communityService.comment(userId, projectId, request);
    }

    @DeleteMapping("/projects/{projectId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteComment(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String projectId,
            @PathVariable String commentId
    ) {
        communityService.deleteComment(userId, projectId, commentId);
    }

    @PostMapping("/projects/{projectId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    ActionResponse share(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String projectId
    ) {
        return communityService.share(userId, projectId);
    }

    @PostMapping("/users/{targetUserId}/follow")
    ActionResponse toggleFollow(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String targetUserId,
            @RequestParam String projectId
    ) {
        return communityService.toggleFollow(userId, targetUserId, projectId);
    }
}
