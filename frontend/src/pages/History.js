import { useEffect, useState } from "react";
import PlatformBadge from "../components/PlatformBadge";
import { api } from "../api";

function StatusPill({ status }) {
  return <span className={`status-pill status-pill--${status}`}>{status}</span>;
}

export default function History() {
  const [posts, setPosts] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .listPosts()
      .then(setPosts)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">History</p>
      <h1 className="page__title">Published posts</h1>
      <p className="page__lead">
        Results from the Spring Boot API — success or failed per network.
      </p>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="page__lead">Loading…</p>}

      <div className="panel fade-rise fade-rise-delay-1">
        {!loading && posts.length === 0 && (
          <p className="list-item__meta">No posts yet.</p>
        )}
        {posts.map((post) => (
          <div key={post.id} className="list-item">
            <div style={{ flex: 1, minWidth: 0 }}>
              <p className="list-item__title">{post.content}</p>
              <p className="list-item__meta">
                {new Date(post.createdAt).toLocaleString()}
              </p>
              <div
                style={{
                  display: "flex",
                  flexWrap: "wrap",
                  gap: "0.5rem",
                  marginTop: "0.65rem",
                }}
              >
                {post.platforms.map((p) => (
                  <span
                    key={p.platform}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "0.4rem",
                    }}
                  >
                    <PlatformBadge platformId={p.platform} size="sm" />
                    <StatusPill status={p.status} />
                  </span>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
