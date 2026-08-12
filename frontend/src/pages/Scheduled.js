import { useCallback, useEffect, useState } from "react";
import PlatformBadge from "../components/PlatformBadge";
import { api } from "../api";

function StatusPill({ status }) {
  const tone =
    status === "done" || status === "success"
      ? "success"
      : status === "failed"
        ? "failed"
        : status === "processing"
          ? "pending"
          : "scheduled";
  return <span className={`status-pill status-pill--${tone}`}>{status}</span>;
}

export default function Scheduled() {
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    return api
      .listScheduledPosts()
      .then(setItems)
      .catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    load().finally(() => setLoading(false));
    const timer = setInterval(() => {
      load().catch(() => {});
    }, 15000);
    return () => clearInterval(timer);
  }, [load]);

  const upcoming = items.filter(
    (j) => j.status === "scheduled" || j.status === "processing"
  );
  const past = items.filter(
    (j) => j.status !== "scheduled" && j.status !== "processing"
  );

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Scheduled</p>
      <h1 className="page__title">Scheduled posts</h1>
      <p className="page__lead">
        Posts waiting to go live at their set time. This list refreshes
        automatically.
      </p>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="page__lead">Loading…</p>}

      <div className="panel fade-rise fade-rise-delay-1">
        <h2 style={{ fontSize: "1.05rem", marginBottom: "0.85rem" }}>
          Upcoming ({upcoming.length})
        </h2>
        {!loading && upcoming.length === 0 && (
          <p className="list-item__meta">No posts currently scheduled.</p>
        )}
        {upcoming.map((job) => (
          <div key={job.id} className="list-item">
            <div style={{ flex: 1, minWidth: 0 }}>
              <p className="list-item__title">{job.content}</p>
              <p className="list-item__meta">
                Publishes {new Date(job.scheduledAt).toLocaleString()}
              </p>
              <div
                className="list-item__platforms"
                style={{ marginTop: "0.5rem" }}
              >
                {(job.platforms || []).map((p) => (
                  <PlatformBadge key={p} platformId={p} size="sm" />
                ))}
              </div>
            </div>
            <StatusPill status={job.status} />
          </div>
        ))}
      </div>

      {past.length > 0 && (
        <div
          className="panel fade-rise fade-rise-delay-2"
          style={{ marginTop: "1rem" }}
        >
          <h2 style={{ fontSize: "1.05rem", marginBottom: "0.85rem" }}>
            Recently finished ({past.length})
          </h2>
          {past.map((job) => (
            <div key={job.id} className="list-item">
              <div style={{ flex: 1, minWidth: 0 }}>
                <p className="list-item__title">{job.content}</p>
                <p className="list-item__meta">
                  Was set for {new Date(job.scheduledAt).toLocaleString()}
                </p>
                <div
                  className="list-item__platforms"
                  style={{ marginTop: "0.5rem" }}
                >
                  {(job.platforms || []).map((p) => (
                    <PlatformBadge key={p} platformId={p} size="sm" />
                  ))}
                </div>
              </div>
              <StatusPill status={job.status} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
