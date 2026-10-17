-- Scale the community to 10,000 users and 20,000 projects for load simulation.
-- Simulated users use the default avatar and are not offered in the user switcher.

ALTER TABLE community_users ADD COLUMN selectable BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE v5_digits (d INT NOT NULL);
INSERT INTO v5_digits (d) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TABLE v5_numbers (n INT NOT NULL, PRIMARY KEY (n));
INSERT INTO v5_numbers (n)
SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + f.d * 10000 + 1
FROM v5_digits a
CROSS JOIN v5_digits b
CROSS JOIN v5_digits c
CROSS JOIN v5_digits e
CROSS JOIN v5_digits f;

CREATE TABLE v5_counts (users_now INT NOT NULL, projects_now INT NOT NULL, assets_now INT NOT NULL);
INSERT INTO v5_counts (users_now, projects_now, assets_now)
SELECT (SELECT COUNT(*) FROM community_users),
       (SELECT COUNT(*) FROM projects),
       (SELECT COUNT(*) FROM image_assets);

-- Names end with a zero-padded serial, so they never collide with each other or hand-written names.
INSERT INTO community_users (id, nickname, avatar_prompt, bio, accent_color, created_at, selectable)
SELECT CONCAT('u', LPAD(100000 + n.n, 15, '0')),
       CONCAT(SUBSTRING('云星月风山海川岛森野光影墨青白赤橙夏秋冬', 1 + MOD(n.n, 20), 1),
              SUBSTRING('晨暮雨雪雾舟桥花木石泉鹿鹤鲸松竹禾岚汐砚', 1 + MOD(n.n * 7, 20), 1),
              LPAD(n.n, 5, '0')),
       'Default community avatar',
       '社区成员',
       SUBSTRING('#c8d6cf#d9c7b8#c9cfe0#e0d4b8#d6c3d1#bfd3d9#d8d2c4#cbd8bd', 1 + MOD(n.n, 8) * 7, 7),
       TIMESTAMPADD(MINUTE, -n.n * 25, CURRENT_TIMESTAMP(3)),
       FALSE
FROM v5_numbers n
CROSS JOIN v5_counts c
WHERE n.n <= 10000 - c.users_now;

CREATE TABLE v5_user_numbers (n INT NOT NULL, user_id CHAR(16) NOT NULL, PRIMARY KEY (n));
INSERT INTO v5_user_numbers (n, user_id)
SELECT n.n, CONCAT('u', LPAD(100000 + n.n, 15, '0'))
FROM v5_numbers n
CROSS JOIN v5_counts c
WHERE n.n <= 10000 - c.users_now;

-- Projects reuse the existing landscape assets in turn and spread over roughly the past 180 days.
INSERT INTO projects (id, user_id, image_asset_id, name, description, created_at)
SELECT CONCAT('p', LPAD(100000 + n.n, 15, '0')),
       u.user_id,
       CONCAT('a', LPAD(1 + MOD(n.n - 1, c.assets_now), 15, '0')),
       CONCAT(SUBSTRING('雾橙潮纸夜青夏赤玻蓝雨云花石飞风光影山海', 1 + MOD(n.n, 20), 1),
              SUBSTRING('岛椅汐境站苔窗丘影瓶巷梯市室鸟塔野林川谷', 1 + MOD(n.n * 3, 20), 1),
              '·',
              LPAD(n.n, 5, '0')),
       '模拟社区作品',
       TIMESTAMPADD(MINUTE, -(180 + n.n * 13), CURRENT_TIMESTAMP(3))
FROM v5_numbers n
CROSS JOIN v5_counts c
JOIN v5_user_numbers u ON u.n = 1 + MOD(n.n - 1, 10000 - c.users_now)
WHERE n.n <= 20000 - c.projects_now;

DROP TABLE v5_user_numbers;
DROP TABLE v5_counts;
DROP TABLE v5_numbers;
DROP TABLE v5_digits;
