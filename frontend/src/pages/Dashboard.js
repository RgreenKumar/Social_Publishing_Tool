import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { api, getStoredUser, isAdmin } from "../api";

export default function Dashboard() {
  const user = getStoredUser();
  const admin = isAdmin();
  const [accounts, setAccounts] = useState([]);
  const [posts, setPosts] = useState([]);
  const [teamStats, setTeamStats] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const requests = [api.listAccounts(), api.listPosts()];
        if (admin) {
          requests.push(api.teamPublishStats());
        }
        const results = await Promise.all(requests);
        if (!cancelled) {
          setAccounts(results[0]);
          setPosts(results[1]);
          if (admin) {
            setTeamStats(results[2] || []);
          }
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
  }, [admin]);

  const connected = accounts.filter((a) => a.connected).length;
  const successCount = posts.reduce(
    (n, p) => n + p.platforms.filter((x) => x.status === "success").length,
    0
  );
  const totalAttempts = posts.reduce((n, p) => n + p.platforms.length, 0);
  const successRate =
    totalAttempts === 0 ? "—" : `${Math.round((successCount / totalAttempts) * 100)}%`;
  const teamPublishedTotal = teamStats.reduce(
    (n, m) => n + (Number(m.publishedCount) || 0),
    0
  );

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

          {admin && (
            <div className="panel fade-rise fade-rise-delay-1" style={{ marginTop: "1rem" }}>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: "0.85rem",
                  gap: "0.75rem",
                  flexWrap: "wrap",
                }}
              >
                <h2 style={{ fontSize: "1.15rem" }}>Team member publishes</h2>
                <span className="list-item__meta">
                  {teamPublishedTotal} total approved
                </span>
              </div>
              {teamStats.length === 0 ? (
                <p className="list-item__meta">
                  No team members yet.{" "}
                  <Link to="/app/team">Add members</Link> to track their publishes.
                </p>
              ) : (
                teamStats.map((member) => (
                  <div key={member.id} className="list-item">
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <p className="list-item__title">{member.name}</p>
                      <p className="list-item__meta">@{member.username}</p>
                    </div>
                    <div style={{ textAlign: "right" }}>
                      <p className="stat__value" style={{ fontSize: "1.45rem", margin: 0 }}>
                        {Number(member.publishedCount) || 0}
                      </p>
                      <p className="list-item__meta" style={{ margin: 0 }}>
                        published
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

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
                {admin ? (
                  <>
                    <Link to="/app/team" className="btn btn--ghost">
                      Manage team
                    </Link>
                    <Link to="/app/accounts" className="btn btn--ghost">
                      Manage connected accounts
                    </Link>
                  </>
                ) : (
                  <Link to="/app/requests" className="btn btn--ghost">
                    My approval requests
                  </Link>
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
