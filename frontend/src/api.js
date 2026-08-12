const API_URL = (process.env.REACT_APP_API_URL || "").replace(/\/$/, "");

function authHeader() {
  const token = localStorage.getItem("smh_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(path, options = {}) {
  try {
    const response = await fetch(`${API_URL}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...authHeader(),
        ...(options.headers || {}),
      },
    });

    if (response.status === 204) {
      return null;
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = { message: text };
      }
    }

    if (!response.ok) {
      const message =
        data?.message || data?.error || `Request failed (${response.status})`;
      throw new Error(message);
    }

    return data;
  } catch (err) {
    if (err instanceof TypeError) {
      throw new Error(
        "Cannot reach the API. Start the backend with ./mvnw spring-boot:run in the backend folder, then try again."
      );
    }
    throw err;
  }
}

export const api = {
  health: () => request("/api/health"),
  signup: (body) =>
    request("/api/auth/signup", { method: "POST", body: JSON.stringify(body) }),
  login: ({ login, email, password }) =>
    request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ login: login || email, password }),
    }),
  me: () => request("/api/auth/me"),
  logout: () => request("/api/auth/logout", { method: "POST" }),
  listAccounts: () => request("/api/social-accounts"),
  connectAccount: (body) =>
    request("/api/social-accounts/connect", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  disconnectAccount: (id) =>
    request(`/api/social-accounts/${id}`, { method: "DELETE" }),
  listPosts: () => request("/api/posts"),
  listScheduledPosts: () => request("/api/posts/scheduled"),
  getScheduledPost: (id) => request(`/api/posts/scheduled/${id}`),
  listTeamMembers: () => request("/api/team/members"),
  teamPublishStats: () => request("/api/team/publish-stats"),
  createTeamMember: (body) =>
    request("/api/team/members", { method: "POST", body: JSON.stringify(body) }),
  deleteTeamMember: (id) =>
    request(`/api/team/members/${id}`, { method: "DELETE" }),
  listPendingApprovals: () => request("/api/approvals/pending"),
  listMyApprovals: () => request("/api/approvals/mine"),
  approveRequest: (id) =>
    request(`/api/approvals/${id}/approve`, { method: "POST", body: "{}" }),
  rejectRequest: (id, note) =>
    request(`/api/approvals/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ note: note || "" }),
    }),
  submitForApproval: async ({ content, platforms, imageFile, scheduledAt }) => {
    const form = new FormData();
    form.append("content", content || "");
    form.append("platforms", (platforms || []).join(","));
    if (scheduledAt) {
      form.append("scheduledAt", scheduledAt);
    }
    if (imageFile) {
      form.append("image", imageFile, imageFile.name);
    }

    try {
      const response = await fetch(`${API_URL}/api/approvals`, {
        method: "POST",
        headers: {
          ...authHeader(),
        },
        body: form,
      });

      const text = await response.text();
      let data = null;
      if (text) {
        try {
          data = JSON.parse(text);
        } catch {
          data = { message: text };
        }
      }

      if (!response.ok) {
        const message =
          data?.message || data?.error || `Request failed (${response.status})`;
        throw new Error(message);
      }

      return data;
    } catch (err) {
      if (err instanceof TypeError) {
        throw new Error(
          "Cannot reach the API. Start the backend with ./mvnw spring-boot:run in the backend folder, then try again."
        );
      }
      throw err;
    }
  },
  publishPost: async ({ content, platforms, imageFile, scheduledAt }) => {
    const form = new FormData();
    form.append("content", content || "");
    form.append("platforms", (platforms || []).join(","));
    if (scheduledAt) {
      form.append("scheduledAt", scheduledAt);
    }
    if (imageFile) {
      form.append("image", imageFile, imageFile.name);
    }

    try {
      const response = await fetch(`${API_URL}/api/posts`, {
        method: "POST",
        headers: {
          ...authHeader(),
        },
        body: form,
      });

      const text = await response.text();
      let data = null;
      if (text) {
        try {
          data = JSON.parse(text);
        } catch {
          data = { message: text };
        }
      }

      if (!response.ok) {
        const message =
          data?.message || data?.error || `Request failed (${response.status})`;
        throw new Error(message);
      }

      return data;
    } catch (err) {
      if (err instanceof TypeError) {
        throw new Error(
          "Cannot reach the API. Start the backend with ./mvnw spring-boot:run in the backend folder, then try again."
        );
      }
      throw err;
    }
  },
};

export function saveSession({ token, user }) {
  localStorage.setItem("smh_token", token);
  localStorage.setItem("smh_user", JSON.stringify(user));
}

export function getStoredToken() {
  return localStorage.getItem("smh_token");
}

export function clearSession() {
  localStorage.removeItem("smh_token");
  localStorage.removeItem("smh_user");
  localStorage.removeItem("relay_token");
  localStorage.removeItem("relay_user");
}

export function getStoredUser() {
  const raw = localStorage.getItem("smh_user");
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function isAdmin() {
  const user = getStoredUser();
  return user?.role === "ADMIN";
}

export function isMember() {
  const user = getStoredUser();
  return user?.role === "MEMBER";
}

export function isLoggedIn() {
  return Boolean(localStorage.getItem("smh_token"));
}

const SCHEDULED_WATCH_KEY = "smh_scheduled_watch";

export function addScheduledWatch(id) {
  if (!id) return;
  const list = getScheduledWatchIds();
  if (!list.includes(id)) {
    list.push(id);
    localStorage.setItem(SCHEDULED_WATCH_KEY, JSON.stringify(list));
  }
}

export function removeScheduledWatch(id) {
  const list = getScheduledWatchIds().filter((item) => item !== id);
  localStorage.setItem(SCHEDULED_WATCH_KEY, JSON.stringify(list));
}

export function getScheduledWatchIds() {
  const raw = localStorage.getItem(SCHEDULED_WATCH_KEY);
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch {
    return [];
  }
}
