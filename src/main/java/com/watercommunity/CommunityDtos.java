package com.watercommunity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

record UserResponse(
        String id,
        String nickname,
        String avatarPrompt,
        String bio,
        String accentColor,
        long projectCount,
        long followerCount,
        long followingCount,
        boolean followedByMe
) {
}

record AuthorResponse(
        String id,
        String nickname,
        String avatarPrompt,
        String accentColor,
        boolean followedByMe
) {
}

record AssetResponse(
        String id,
        String title,
        String imagePrompt,
        String altText,
        String accentColor
) {
}

record CommentResponse(
        String id,
        String userId,
        String nickname,
        String avatarPrompt,
        String content,
        LocalDateTime createdAt
) {
}

record ProjectResponse(
        String id,
        String name,
        String description,
        LocalDateTime createdAt,
        AuthorResponse author,
        AssetResponse asset,
        long likeCount,
        long commentCount,
        long shareCount,
        boolean likedByMe,
        List<CommentResponse> recentComments
) {
}

record ProfileResponse(
        UserResponse user,
        long receivedLikeCount,
        long totalCommentCount,
        long totalShareCount,
        List<ProjectResponse> projects
) {
}

record PublishProjectRequest(
        @NotBlank(message = "请选择一张作品图片")
        String imageAssetId,

        @Size(max = 24, message = "作品名称不能超过 24 个字符")
        String name,

        @Size(max = 180, message = "作品说明不能超过 180 个字符")
        String description
) {
}

record AddCommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 240, message = "评论不能超过 240 个字符")
        String content
) {
}

record ActionResponse(
        boolean active,
        long count,
        String message
) {
}

record ApiError(
        int status,
        String message,
        Instant timestamp
) {
}
