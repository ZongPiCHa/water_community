-- Business ids become fixed-length 16 character strings: one type prefix plus 15 characters.
-- Existing numeric ids are preserved as zero-padded values, for example user 1 -> u000000000000001.

CREATE TABLE v4_old_community_users AS SELECT * FROM community_users;
CREATE TABLE v4_old_image_assets AS SELECT * FROM image_assets;
CREATE TABLE v4_old_projects AS SELECT * FROM projects;
CREATE TABLE v4_old_project_likes AS SELECT * FROM project_likes;
CREATE TABLE v4_old_project_comments AS SELECT * FROM project_comments;
CREATE TABLE v4_old_user_follows AS SELECT * FROM user_follows;
CREATE TABLE v4_old_project_shares AS SELECT * FROM project_shares;

DROP TABLE project_shares;
DROP TABLE user_follows;
DROP TABLE project_comments;
DROP TABLE project_likes;
DROP TABLE projects;
DROP TABLE image_assets;
DROP TABLE community_users;

CREATE TABLE community_users (
    id CHAR(16) NOT NULL,
    nickname VARCHAR(16) NOT NULL,
    avatar_prompt VARCHAR(700) NOT NULL,
    bio VARCHAR(120) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_community_users_nickname UNIQUE (nickname)
);

CREATE TABLE image_assets (
    id CHAR(16) NOT NULL,
    title VARCHAR(32) NOT NULL,
    image_prompt VARCHAR(700) NOT NULL,
    alt_text VARCHAR(120) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_image_assets_sort_order UNIQUE (sort_order)
);

CREATE TABLE projects (
    id CHAR(16) NOT NULL,
    user_id CHAR(16) NOT NULL,
    image_asset_id CHAR(16) NOT NULL,
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
    id CHAR(16) NOT NULL,
    project_id CHAR(16) NOT NULL,
    user_id CHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_project_likes_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_likes_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_likes_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_likes_user ON project_likes (user_id);

CREATE TABLE project_comments (
    id CHAR(16) NOT NULL,
    project_id CHAR(16) NOT NULL,
    user_id CHAR(16) NOT NULL,
    content VARCHAR(240) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_project_comments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_comments_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_comments_project_created ON project_comments (project_id, created_at);

CREATE TABLE user_follows (
    id CHAR(16) NOT NULL,
    follower_id CHAR(16) NOT NULL,
    following_id CHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_follows_pair UNIQUE (follower_id, following_id),
    CONSTRAINT fk_user_follows_follower FOREIGN KEY (follower_id) REFERENCES community_users (id),
    CONSTRAINT fk_user_follows_following FOREIGN KEY (following_id) REFERENCES community_users (id),
    CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> following_id)
);

CREATE INDEX idx_user_follows_following ON user_follows (following_id);

CREATE TABLE project_shares (
    id CHAR(16) NOT NULL,
    project_id CHAR(16) NOT NULL,
    user_id CHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_project_shares_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_shares_user FOREIGN KEY (user_id) REFERENCES community_users (id)
);

CREATE INDEX idx_project_shares_project ON project_shares (project_id);

INSERT INTO community_users (id, nickname, avatar_prompt, bio, accent_color, created_at)
SELECT CONCAT('u', LPAD(id, 15, '0')), nickname, avatar_prompt, bio, accent_color, created_at
FROM v4_old_community_users;

INSERT INTO image_assets (id, title, image_prompt, alt_text, accent_color, sort_order)
SELECT CONCAT('a', LPAD(id, 15, '0')), title, image_prompt, alt_text, accent_color, sort_order
FROM v4_old_image_assets;

INSERT INTO projects (id, user_id, image_asset_id, name, description, created_at)
SELECT CONCAT('p', LPAD(id, 15, '0')), CONCAT('u', LPAD(user_id, 15, '0')),
       CONCAT('a', LPAD(image_asset_id, 15, '0')), name, description, created_at
FROM v4_old_projects;

INSERT INTO project_likes (id, project_id, user_id, created_at)
SELECT CONCAT('l', LPAD(id, 15, '0')), CONCAT('p', LPAD(project_id, 15, '0')),
       CONCAT('u', LPAD(user_id, 15, '0')), created_at
FROM v4_old_project_likes;

INSERT INTO project_comments (id, project_id, user_id, content, created_at)
SELECT CONCAT('c', LPAD(id, 15, '0')), CONCAT('p', LPAD(project_id, 15, '0')),
       CONCAT('u', LPAD(user_id, 15, '0')), content, created_at
FROM v4_old_project_comments;

INSERT INTO user_follows (id, follower_id, following_id, created_at)
SELECT CONCAT('f', LPAD(id, 15, '0')), CONCAT('u', LPAD(follower_id, 15, '0')),
       CONCAT('u', LPAD(following_id, 15, '0')), created_at
FROM v4_old_user_follows;

INSERT INTO project_shares (id, project_id, user_id, created_at)
SELECT CONCAT('s', LPAD(id, 15, '0')), CONCAT('p', LPAD(project_id, 15, '0')),
       CONCAT('u', LPAD(user_id, 15, '0')), created_at
FROM v4_old_project_shares;

DROP TABLE v4_old_project_shares;
DROP TABLE v4_old_user_follows;
DROP TABLE v4_old_project_comments;
DROP TABLE v4_old_project_likes;
DROP TABLE v4_old_projects;
DROP TABLE v4_old_image_assets;
DROP TABLE v4_old_community_users;
