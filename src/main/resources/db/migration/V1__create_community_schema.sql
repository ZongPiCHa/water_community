CREATE TABLE community_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(16) NOT NULL,
    avatar_prompt VARCHAR(700) NOT NULL,
    bio VARCHAR(120) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_community_users_nickname UNIQUE (nickname)
);

CREATE TABLE image_assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(32) NOT NULL,
    image_prompt VARCHAR(700) NOT NULL,
    alt_text VARCHAR(120) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_image_assets_sort_order UNIQUE (sort_order)
);

CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    image_asset_id BIGINT NOT NULL,
    name VARCHAR(24) NOT NULL,
    description VARCHAR(180) NOT NULL DEFAULT '',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_projects_name UNIQUE (name),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES community_users (id),
    CONSTRAINT fk_projects_image_asset FOREIGN KEY (image_asset_id) REFERENCES image_assets (id)
);

CREATE INDEX idx_projects_created_at ON projects (created_at);
CREATE INDEX idx_projects_user_created ON projects (user_id, created_at);

CREATE TABLE project_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_project_likes_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_likes_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_likes_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_likes_user ON project_likes (user_id);

CREATE TABLE project_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(240) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_project_comments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_comments_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_comments_project_created ON project_comments (project_id, created_at);

CREATE TABLE user_follows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_follows_pair UNIQUE (follower_id, following_id),
    CONSTRAINT fk_user_follows_follower FOREIGN KEY (follower_id) REFERENCES community_users (id),
    CONSTRAINT fk_user_follows_following FOREIGN KEY (following_id) REFERENCES community_users (id),
    CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> following_id)
);

CREATE INDEX idx_user_follows_following ON user_follows (following_id);

CREATE TABLE project_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_project_shares_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_shares_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_shares_project ON project_shares (project_id);
