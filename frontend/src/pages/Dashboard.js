import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { api, getStoredUser } from "../api";

export default function Dashboard() {
  const user = getStoredUser();
  const [accounts, setAccounts] = useState([]);
  const [posts, setPosts] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [acc, postList] = await Promise.all([
          api.listAccounts(),
          api.listPosts(),
        ]);
        if (!cancelled) {
          setAccounts(acc);
          setPosts(postList);
        }
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const connected = accounts.filter((a) => a.connected).length;
  const successCount = posts.reduce(
    (n, p) => n + p.platforms.filter((x) => x.status === "success").length,
    0
  );
  const totalAttempts = posts.reduce((n, p) => n + p.platforms.length, 0);
  const successRate =
    totalAttempts === 0 ? "—" : `${Math.round((successCount / totalAttempts) * 100)}%`;

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Overview</p>
      <h1 className="page__title">
        {user?.name ? `Hi, ${user.name.split(" ")[0]}` : "Command center"}
      </h1>
      <p className="page__lead">
        Connected to Spring Boot API. Compose a post or review what you published.
      </p>

      {error && <p className="form-error">{error}</p>}
      {loading ? (
        <p className="page__lead">Loading…</p>
      ) : (
        <>
          <div className="grid-3 fade-rise fade-rise-delay-1">
            <div className="panel stat">
              <p className="stat__label">Connected accounts</p>
              <p className="stat__value">{connected}</p>
            </div>
            <div className="panel stat">
              <p className="stat__label">Posts published</p>
              <p className="stat__value">{posts.length}</p>
            </div>
            <div className="panel stat">
              <p className="stat__label">Publish success</p>
              <p className="stat__value">{successRate}</p>
            </div>
          </div>

          <div className="dashboard__row fade-rise fade-rise-delay-2">
            <div className="panel">
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: "0.75rem",
                }}
              >
                <h2 style={{ fontSize: "1.15rem" }}>Recent posts</h2>
                <Link to="/app/history" className="btn btn--ghost btn--sm">
                  View all
                </Link>
              </div>
              {posts.length === 0 ? (
                <p className="list-item__meta">No posts yet. Compose your first one.</p>
              ) : (
                posts.slice(0, 3).map((post) => (
                  <div key={post.id} className="list-item">
                    <div>
                      <p className="list-item__title">{post.content}</p>
                      <p className="list-item__meta">
                        {new Date(post.createdAt).toLocaleString()}
                      </p>
                    </div>
                    <div className="list-item__platforms">
                      {post.platforms.map((p) => (
                        <PlatformBadge
                          key={p.platform}
                          platformId={p.platform}
                          size="sm"
                        />
                      ))}
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="panel">
              <h2 style={{ fontSize: "1.15rem", marginBottom: "0.85rem" }}>
                Quick actions
              </h2>
              <div style={{ display: "flex", flexDirection: "column", gap: "0.65rem" }}>
                <Link to="/app/compose" className="btn btn--primary">
                  Compose new post
                </Link>
                <Link to="/app/accounts" className="btn btn--ghost">
                  Manage connected accounts
                </Link>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
