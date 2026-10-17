package com.watercommunity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "community_users")
class CommunityUser {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @Column(nullable = false, unique = true, length = 16)
    private String nickname;

    @Column(name = "avatar_prompt", nullable = false, length = 700)
    private String avatarPrompt;

    @Column(nullable = false, length = 120)
    private String bio;

    @Column(name = "accent_color", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String accentColor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Simulated users exist for load testing but are not offered in the user switcher.
    @Column(nullable = false)
    private boolean selectable;

    protected CommunityUser() {
    }

    String getId() {
        return id;
    }

    String getNickname() {
        return nickname;
    }

    String getAvatarPrompt() {
        return avatarPrompt;
    }

    String getBio() {
        return bio;
    }

    String getAccentColor() {
        return accentColor;
    }

    boolean isSelectable() {
        return selectable;
    }

    String getAvatarUrl() {
        return selectable
                ? "/generated/avatars/optimized/avatar-" + id + ".jpg"
                : "/generated/avatars/default-avatar.svg";
    }
}

@Entity
@Table(name = "image_assets")
class ImageAsset {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @Column(nullable = false, length = 32)
    private String title;

    @Column(name = "image_prompt", nullable = false, length = 700)
    private String imagePrompt;

    @Column(name = "alt_text", nullable = false, length = 120)
    private String altText;

    @Column(name = "accent_color", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String accentColor;

    @Column(name = "sort_order", nullable = false, unique = true)
    private Integer sortOrder;

    protected ImageAsset() {
    }

    String getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getImagePrompt() {
        return imagePrompt;
    }

    String getAltText() {
        return altText;
    }

    String getAccentColor() {
        return accentColor;
    }

    Integer getSortOrder() {
        return sortOrder;
    }
}

@Entity
@Table(name = "projects")
class Project {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_asset_id", nullable = false, columnDefinition = "CHAR(16)")
    private ImageAsset imageAsset;

    @Column(nullable = false, unique = true, length = 24)
    private String name;

    @Column(nullable = false, length = 180)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Project() {
    }

    Project(CommunityUser user, ImageAsset imageAsset, String name, String description) {
        this.id = CommunityIds.next(CommunityIds.PROJECT);
        this.user = user;
        this.imageAsset = imageAsset;
        this.name = name;
        this.description = description;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    String getId() {
        return id;
    }

    CommunityUser getUser() {
        return user;
    }

    ImageAsset getImageAsset() {
        return imageAsset;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

@Entity
@Table(name = "project_likes")
class ProjectLike {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, columnDefinition = "CHAR(16)")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProjectLike() {
    }

    ProjectLike(Project project, CommunityUser user) {
        this.id = CommunityIds.next(CommunityIds.LIKE);
        this.project = project;
        this.user = user;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}

@Entity
@Table(name = "project_comments")
class ProjectComment {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, columnDefinition = "CHAR(16)")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser user;

    @Column(nullable = false, length = 240)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProjectComment() {
    }

    ProjectComment(Project project, CommunityUser user, String content) {
        this.id = CommunityIds.next(CommunityIds.COMMENT);
        this.project = project;
        this.user = user;
        this.content = content;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    String getId() {
        return id;
    }

    CommunityUser getUser() {
        return user;
    }

    String getContent() {
        return content;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

@Entity
@Table(name = "user_follows")
class UserFollow {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser follower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser following;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserFollow() {
    }

    UserFollow(CommunityUser follower, CommunityUser following) {
        this.id = CommunityIds.next(CommunityIds.FOLLOW);
        this.follower = follower;
        this.following = following;
        this.createdAt = Instant.now();
    }
}

@Entity
@Table(name = "project_shares")
class ProjectShare {
    @Id
    @Column(length = CommunityIds.LENGTH, columnDefinition = "CHAR(16)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, columnDefinition = "CHAR(16)")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "CHAR(16)")
    private CommunityUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectShare() {
    }

    ProjectShare(Project project, CommunityUser user) {
        this.id = CommunityIds.next(CommunityIds.SHARE);
        this.project = project;
        this.user = user;
        this.createdAt = Instant.now();
    }

    String getId() {
        return id;
    }
}
