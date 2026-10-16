const state = {
    users: [],
    assets: [],
    projects: [],
    profile: null,
    currentUserId: localStorage.getItem("water.currentUserId") || null,
    selectedAssetId: null,
    sort: "latest"
};

const elements = {
    usersGrid: document.querySelector("#users-grid"),
    projectFeed: document.querySelector("#project-feed"),
    profileContent: document.querySelector("#profile-content"),
    assetGrid: document.querySelector("#asset-grid"),
    selectedPreview: document.querySelector("#selected-preview"),
    selectionLabel: document.querySelector("#asset-selection-label"),
    publishDialog: document.querySelector("#publish-dialog"),
    publishForm: document.querySelector("#publish-form"),
    publishSubmit: document.querySelector("#publish-submit"),
    toastRegion: document.querySelector("#toast-region")
};

function avatarUrl(user) {
    const userId = user.userId || user.id;
    return `/generated/avatars/optimized/avatar-${userId}.jpg`;
}

function assetUrl(asset) {
    return `/generated/landscapes/optimized/landscape-${asset.id}.jpg`;
}

const SESSION_IDLE_MS = 30 * 60 * 1000;
let lastSpanId = null;

// water-box groups actions by session; rotate the id after 30 minutes without API calls.
function trackingSessionId() {
    const now = Date.now();
    let sessionId = localStorage.getItem("water.sessionId");
    const lastActive = Number(localStorage.getItem("water.sessionActiveAt")) || 0;
    if (!sessionId || now - lastActive > SESSION_IDLE_MS) {
        sessionId = window.crypto?.randomUUID?.() || `${now.toString(36)}-${Math.random().toString(36).slice(2)}`;
        localStorage.setItem("water.sessionId", sessionId);
    }
    localStorage.setItem("water.sessionActiveAt", String(now));
    return sessionId;
}

function escapeHtml(value = "") {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function currentUser() {
    return state.users.find((user) => user.id === state.currentUserId) || null;
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.currentUserId) {
        headers.set("X-User-Id", state.currentUserId);
    }
    headers.set("X-Session-Id", trackingSessionId());
    if (lastSpanId) {
        headers.set("X-Span-Id", lastSpanId);
    }
    if (options.body) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(path, {...options, headers});
    lastSpanId = response.headers.get("X-Span-Id") || lastSpanId;
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
        throw new Error(payload?.message || `请求失败 (${response.status})`);
    }
    return payload;
}

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons({attrs: {"aria-hidden": "true"}});
    }
}

function attachImageFallbacks(scope = document) {
    scope.querySelectorAll("img[data-fallback]").forEach((image) => {
        if (image.dataset.fallbackReady) return;
        image.dataset.fallbackReady = "true";
        image.addEventListener("error", () => {
            const bounds = image.getBoundingClientRect();
            const styles = window.getComputedStyle(image);
            const fallback = document.createElement("span");
            fallback.className = `${image.className} image-fallback`.trim();
            fallback.setAttribute("aria-hidden", "true");
            fallback.style.display = "block";
            fallback.style.flex = "0 0 auto";
            fallback.style.width = bounds.width ? `${bounds.width}px` : styles.width;
            fallback.style.height = bounds.height ? `${bounds.height}px` : styles.height;
            fallback.style.maxWidth = "100%";
            fallback.style.borderRadius = styles.borderRadius;
            fallback.style.background = image.dataset.fallback || "#d9dad5";
            image.replaceWith(fallback);
        });
    });
}

function toast(message, error = false) {
    const item = document.createElement("div");
    item.className = `toast${error ? " is-error" : ""}`;
    item.textContent = message;
    elements.toastRegion.append(item);
    window.setTimeout(() => item.remove(), 2800);
}

function showLoading(target, label = "正在载入内容…") {
    target.innerHTML = `<div class="loading-state"><span>${escapeHtml(label)}</span></div>`;
}

function updateCurrentUserControl() {
    const user = currentUser();
    document.querySelectorAll(".current-user").forEach((button) => {
        button.innerHTML = user
            ? `<img src="${avatarUrl(user)}" alt="${escapeHtml(user.nickname)}的头像" data-fallback="${user.accentColor}">
               <span>${escapeHtml(user.nickname)}</span>`
            : `<span class="current-user-empty">选择用户</span>`;
    });
    attachImageFallbacks();
}

function showView(viewName) {
    if (viewName === "profile" && !state.currentUserId) {
        toast("请先选择一个用户", true);
        viewName = "users";
    }

    document.querySelectorAll(".view").forEach((view) => {
        view.classList.toggle("is-active", view.id === `view-${viewName}`);
    });
    document.querySelectorAll(".tab").forEach((tab) => {
        tab.classList.toggle("is-active", tab.dataset.view === viewName);
    });

    if (viewName === "community") {
        loadProjects();
    }
    if (viewName === "profile") {
        loadProfile();
    }
    window.scrollTo({top: 0, behavior: "smooth"});
}

function renderUsers() {
    elements.usersGrid.innerHTML = state.users.map((user, index) => `
        <button class="user-card${user.id === state.currentUserId ? " is-current" : ""}"
                type="button"
                data-select-user="${user.id}"
                aria-label="选择用户${escapeHtml(user.nickname)}">
            <img src="${avatarUrl(user)}"
                 alt="${escapeHtml(user.nickname)}的头像"
                 data-fallback="${user.accentColor}">
            <span class="user-card-row">
                <h3>${escapeHtml(user.nickname)}</h3>
                <span class="user-card-index">${String(index + 1).padStart(2, "0")}</span>
            </span>
            <p>${escapeHtml(user.bio)}</p>
            <span class="user-meta">
                <span>${user.projectCount} 作品</span>
                <span>${user.followerCount} 关注者</span>
            </span>
        </button>
    `).join("");
    attachImageFallbacks(elements.usersGrid);
}

function renderAssets() {
    elements.assetGrid.innerHTML = state.assets.map((asset) => `
        <button class="asset-option${asset.id === state.selectedAssetId ? " is-selected" : ""}"
                type="button"
                data-select-asset="${asset.id}"
                aria-label="选择${escapeHtml(asset.title)}">
            <img src="${assetUrl(asset)}"
                 alt="${escapeHtml(asset.altText)}"
                 loading="lazy"
                 data-fallback="${asset.accentColor}">
            <span>${asset.id.slice(-2)} · ${escapeHtml(asset.title)}</span>
        </button>
    `).join("");
    attachImageFallbacks(elements.assetGrid);
}

function relativeTime(value) {
    const elapsed = Math.max(0, Date.now() - new Date(value).getTime());
    const minutes = Math.floor(elapsed / 60000);
    if (minutes < 1) return "刚刚";
    if (minutes < 60) return `${minutes} 分钟前`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} 小时前`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days} 天前`;
    return new Intl.DateTimeFormat("zh-CN", {month: "short", day: "numeric"}).format(new Date(value));
}

function projectCard(project, index) {
    const isOwn = project.author.id === state.currentUserId;
    const followButton = state.currentUserId && !isOwn
        ? `<button class="follow-button${project.author.followedByMe ? " is-following" : ""}"
                   type="button"
                   data-follow-user="${project.author.id}"
                   data-project-id="${project.id}">
               ${project.author.followedByMe ? "已关注" : "关注"}
           </button>`
        : "";
    const comments = project.recentComments.map((comment) => `
        <div class="comment-row">
            <img src="${avatarUrl(comment)}"
                 alt=""
                 data-fallback="${project.author.accentColor}">
            <p><strong>${escapeHtml(comment.nickname)}</strong>${escapeHtml(comment.content)}</p>
            ${state.currentUserId && (isOwn || comment.userId === state.currentUserId)
                ? `<button class="comment-delete" type="button"
                           data-delete-comment="${comment.id}"
                           data-project-id="${project.id}"
                           aria-label="删除评论" title="删除评论">删除</button>`
                : ""}
        </div>
    `).join("");

    return `
        <article class="project-card" data-project-card="${project.id}">
            <div class="project-image-wrap" style="background:${project.asset.accentColor}">
                <img class="project-image"
                     src="${assetUrl(project.asset)}"
                     alt="${escapeHtml(project.asset.altText)}"
                     data-fallback="${project.asset.accentColor}">
                <span class="project-number">${String(index + 1).padStart(2, "0")}</span>
            </div>
            <div class="project-body">
                <div class="author-row">
                    <div class="author-main">
                        <img class="avatar"
                             src="${avatarUrl(project.author)}"
                             alt="${escapeHtml(project.author.nickname)}的头像"
                             loading="lazy"
                             data-fallback="${project.author.accentColor}">
                        <span class="author-name">${escapeHtml(project.author.nickname)}</span>
                    </div>
                    ${followButton}
                </div>
                <h2 class="project-title">${escapeHtml(project.name)}</h2>
                <p class="project-description">${escapeHtml(project.description || "一页没有附注的灵感。")}</p>
                <time class="project-time" datetime="${project.createdAt}">${relativeTime(project.createdAt)}</time>
                <div class="project-actions">
                    <button class="action-button${project.likedByMe ? " is-active" : ""}"
                            type="button"
                            data-like-project="${project.id}"
                            aria-label="${project.likedByMe ? "取消点赞" : "点赞"}">
                        <i data-lucide="heart"></i>
                        <span>${project.likeCount}</span>
                    </button>
                    <button class="action-button" type="button" data-focus-comment="${project.id}" aria-label="评论">
                        <i data-lucide="message-circle"></i>
                        <span>${project.commentCount}</span>
                    </button>
                    <button class="action-button" type="button" data-share-project="${project.id}" aria-label="分享">
                        <i data-lucide="send"></i>
                        <span>${project.shareCount}</span>
                    </button>
                </div>
                ${comments ? `<div class="comment-list">${comments}</div>` : ""}
                <form class="comment-form" data-comment-form="${project.id}">
                    <input type="text" name="content" maxlength="240" autocomplete="off" placeholder="写下评论" aria-label="评论内容">
                    <button type="submit" aria-label="发送评论" title="发送评论">
                        <i data-lucide="arrow-up"></i>
                    </button>
                </form>
            </div>
        </article>
    `;
}

function renderProjects() {
    if (!state.projects.length) {
        elements.projectFeed.innerHTML = `
            <div class="empty-state">
                <div><h2>还没有作品</h2><p>第一张画面，正等着被发布。</p></div>
            </div>`;
        return;
    }
    elements.projectFeed.innerHTML = state.projects.map(projectCard).join("");
    refreshIcons();
    attachImageFallbacks(elements.projectFeed);
}

async function loadProjects() {
    showLoading(elements.projectFeed);
    try {
        state.projects = await api(`/api/projects?sort=${state.sort}`);
        renderProjects();
    } catch (error) {
        elements.projectFeed.innerHTML = `<div class="empty-state"><div><h2>暂时无法载入</h2><p>${escapeHtml(error.message)}</p></div></div>`;
    }
}

function renderProfile() {
    const profile = state.profile;
    if (!profile) return;
    const projects = profile.projects.length
        ? `<div class="profile-grid">${profile.projects.map((project) => `
                <article class="profile-project">
                    <div style="background:${project.asset.accentColor}">
                        <img src="${assetUrl(project.asset)}"
                             alt="${escapeHtml(project.asset.altText)}"
                             data-fallback="${project.asset.accentColor}">
                    </div>
                    <div class="profile-project-row">
                        <h3>${escapeHtml(project.name)}</h3>
                        <span>${project.likeCount} 赞 · ${project.commentCount} 评论</span>
                    </div>
                </article>
            `).join("")}</div>`
        : `<div class="empty-state"><div><h2>作品集还是空的</h2><p>从一张喜欢的画面开始。</p></div></div>`;

    elements.profileContent.innerHTML = `
        <div class="profile-shell">
            <div class="profile-intro">
                <img class="profile-avatar"
                     src="${avatarUrl(profile.user)}"
                     alt="${escapeHtml(profile.user.nickname)}的头像"
                     data-fallback="${profile.user.accentColor}">
                <div class="profile-copy">
                    <p class="eyebrow">MY COLLECTION</p>
                    <h1 id="profile-title">${escapeHtml(profile.user.nickname)}</h1>
                    <p>${escapeHtml(profile.user.bio)}</p>
                </div>
            </div>
            <div class="profile-stats">
                <div class="stat"><strong>${profile.user.projectCount}</strong><span>发布作品</span></div>
                <div class="stat"><strong>${profile.receivedLikeCount}</strong><span>收到点赞</span></div>
                <div class="stat"><strong>${profile.totalCommentCount}</strong><span>作品评论</span></div>
                <div class="stat"><strong>${profile.user.followerCount}</strong><span>关注者</span></div>
            </div>
            <section class="profile-projects">
                <div class="profile-section-title">
                    <h2>我的作品</h2>
                    <span>${profile.projects.length} 件</span>
                </div>
                ${projects}
            </section>
        </div>`;
    attachImageFallbacks(elements.profileContent);
}

async function loadProfile() {
    if (!state.currentUserId) return;
    showLoading(elements.profileContent, "正在整理作品集…");
    try {
        state.profile = await api("/api/me");
        renderProfile();
    } catch (error) {
        toast(error.message, true);
        showView("users");
    }
}

function requireUser() {
    if (state.currentUserId) return true;
    toast("请先选择一个用户", true);
    showView("users");
    return false;
}

function openPublishDialog() {
    if (!requireUser()) return;
    state.selectedAssetId = null;
    elements.publishForm.reset();
    elements.selectionLabel.textContent = "尚未选择";
    elements.selectedPreview.innerHTML = "<span>从左侧选择一张图片</span>";
    renderAssets();
    elements.publishDialog.showModal();
    refreshIcons();
}

function selectAsset(assetId) {
    state.selectedAssetId = assetId;
    const asset = state.assets.find((item) => item.id === assetId);
    if (!asset) return;
    elements.assetGrid.querySelectorAll(".asset-option").forEach((option) => {
        option.classList.toggle("is-selected", option.dataset.selectAsset === assetId);
    });
    elements.selectionLabel.textContent = `已选择 · ${asset.title}`;
    elements.selectedPreview.innerHTML = `
        <img src="${assetUrl(asset)}"
             alt="${escapeHtml(asset.altText)}"
             data-fallback="${asset.accentColor}">`;
    attachImageFallbacks(elements.selectedPreview);
}

async function selectUser(userId) {
    state.currentUserId = userId;
    localStorage.setItem("water.currentUserId", userId);
    updateCurrentUserControl();
    renderUsers();
    toast(`已切换为 ${currentUser()?.nickname || "新用户"}`);
    showView("community");
}

async function toggleLike(projectId, button) {
    if (!requireUser()) return;
    button.disabled = true;
    try {
        const result = await api(`/api/projects/${projectId}/likes`, {method: "POST"});
        button.classList.toggle("is-active", result.active);
        button.querySelector("span").textContent = result.count;
        button.setAttribute("aria-label", result.active ? "取消点赞" : "点赞");
        const project = state.projects.find((item) => item.id === projectId);
        if (project) {
            project.likedByMe = result.active;
            project.likeCount = result.count;
        }
    } catch (error) {
        toast(error.message, true);
    } finally {
        button.disabled = false;
    }
}

async function toggleFollow(userId, projectId) {
    if (!requireUser()) return;
    try {
        const result = await api(`/api/users/${userId}/follow?projectId=${encodeURIComponent(projectId)}`, {method: "POST"});
        state.projects.forEach((project) => {
            if (project.author.id === userId) project.author.followedByMe = result.active;
        });
        document.querySelectorAll(`[data-follow-user="${userId}"]`).forEach((button) => {
            button.classList.toggle("is-following", result.active);
            button.textContent = result.active ? "已关注" : "关注";
        });
        toast(result.message);
    } catch (error) {
        toast(error.message, true);
    }
}

async function shareProject(projectId, button) {
    if (!requireUser()) return;
    try {
        const result = await api(`/api/projects/${projectId}/shares`, {method: "POST"});
        button.querySelector("span").textContent = result.count;
        const url = `${location.origin}${location.pathname}#project-${projectId}`;
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(url);
            toast("作品链接已复制");
        } else {
            toast("分享已记录");
        }
    } catch (error) {
        toast(error.message, true);
    }
}

async function deleteComment(projectId, commentId) {
    if (!requireUser()) return;
    try {
        await api(`/api/projects/${projectId}/comments/${commentId}`, {method: "DELETE"});
        await loadProjects();
        toast("评论已删除");
    } catch (error) {
        toast(error.message, true);
    }
}

async function submitComment(form) {
    if (!requireUser()) return;
    const input = form.elements.content;
    const content = input.value.trim();
    if (!content) return;
    const projectId = form.dataset.commentForm;
    const button = form.querySelector("button");
    button.disabled = true;
    try {
        await api(`/api/projects/${projectId}/comments`, {
            method: "POST",
            body: JSON.stringify({content})
        });
        input.value = "";
        await loadProjects();
        toast("评论已发布");
    } catch (error) {
        toast(error.message, true);
    } finally {
        button.disabled = false;
    }
}

async function submitProject(event) {
    event.preventDefault();
    if (!state.selectedAssetId) {
        toast("请先选择一张图片", true);
        return;
    }
    elements.publishSubmit.disabled = true;
    const name = document.querySelector("#project-name").value;
    const description = document.querySelector("#project-description").value;
    try {
        const project = await api("/api/projects", {
            method: "POST",
            body: JSON.stringify({imageAssetId: state.selectedAssetId, name, description})
        });
        elements.publishDialog.close();
        toast(`《${project.name}》已发布`);
        state.sort = "latest";
        document.querySelectorAll("[data-sort]").forEach((button) => {
            button.classList.toggle("is-active", button.dataset.sort === "latest");
        });
        showView("community");
    } catch (error) {
        toast(error.message, true);
    } finally {
        elements.publishSubmit.disabled = false;
    }
}

document.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-view]");
    if (tab) {
        showView(tab.dataset.view);
        return;
    }

    if (event.target.closest("[data-action='publish']")) {
        openPublishDialog();
        return;
    }

    if (event.target.closest("[data-action='choose-user']")) {
        showView("users");
        document.querySelector("#users-section").scrollIntoView({behavior: "smooth"});
        return;
    }

    const userCard = event.target.closest("[data-select-user]");
    if (userCard) {
        selectUser(userCard.dataset.selectUser);
        return;
    }

    const sortButton = event.target.closest("[data-sort]");
    if (sortButton) {
        state.sort = sortButton.dataset.sort;
        document.querySelectorAll("[data-sort]").forEach((button) => {
            button.classList.toggle("is-active", button === sortButton);
        });
        loadProjects();
        return;
    }

    const likeButton = event.target.closest("[data-like-project]");
    if (likeButton) {
        toggleLike(likeButton.dataset.likeProject, likeButton);
        return;
    }

    const followButton = event.target.closest("[data-follow-user]");
    if (followButton) {
        toggleFollow(followButton.dataset.followUser, followButton.dataset.projectId);
        return;
    }

    const shareButton = event.target.closest("[data-share-project]");
    if (shareButton) {
        shareProject(shareButton.dataset.shareProject, shareButton);
        return;
    }

    const deleteCommentButton = event.target.closest("[data-delete-comment]");
    if (deleteCommentButton) {
        deleteComment(deleteCommentButton.dataset.projectId, deleteCommentButton.dataset.deleteComment);
        return;
    }

    const commentButton = event.target.closest("[data-focus-comment]");
    if (commentButton) {
        const input = document.querySelector(`[data-comment-form="${commentButton.dataset.focusComment}"] input`);
        input?.focus();
        return;
    }

    const assetOption = event.target.closest("[data-select-asset]");
    if (assetOption) {
        selectAsset(assetOption.dataset.selectAsset);
        return;
    }

    if (event.target.closest("[data-close-dialog]")) {
        elements.publishDialog.close();
        return;
    }

    if (event.target.closest("[data-scroll-users]")) {
        document.querySelector("#users-section").scrollIntoView({behavior: "smooth"});
    }
});

document.addEventListener("submit", (event) => {
    const commentForm = event.target.closest("[data-comment-form]");
    if (commentForm) {
        event.preventDefault();
        submitComment(commentForm);
    }
});

elements.publishForm.addEventListener("submit", submitProject);

elements.publishDialog.addEventListener("click", (event) => {
    if (event.target === elements.publishDialog) {
        elements.publishDialog.close();
    }
});

async function init() {
    document.querySelector("#hero").style.backgroundImage = 'url("/generated/hero-optimized.jpg")';
    showLoading(elements.usersGrid, "正在准备人物…");
    try {
        [state.users, state.assets] = await Promise.all([
            api("/api/users"),
            api("/api/assets")
        ]);
        if (state.currentUserId && !currentUser()) {
            state.currentUserId = null;
            localStorage.removeItem("water.currentUserId");
        }
        renderUsers();
        renderAssets();
        updateCurrentUserControl();
        refreshIcons();
    } catch (error) {
        elements.usersGrid.innerHTML = `<div class="empty-state"><div><h2>连接没有准备好</h2><p>${escapeHtml(error.message)}</p></div></div>`;
        toast(error.message, true);
    }
}

init();
