import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { api, isMember } from "../api";

export default function MyRequests() {
  const member = isMember();
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!member) {
      setLoading(false);
      return undefined;
    }
    api
      .listMyApprovals()
      .then(setItems)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
    return undefined;
  }, [member]);

  if (!member) {
    return <Navigate to="/app" replace />;
  }

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">My requests</p>
      <h1 className="page__title">Approval status</h1>
      <p className="page__lead">
        Track drafts you sent to your admin for approval.
      </p>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="page__lead">Loading…</p>}

      <div className="panel fade-rise fade-rise-delay-1">
        {!loading && items.length === 0 && (
          <p className="list-item__meta">
            No requests yet. Compose a post and submit it.
          </p>
        )}
        {items.map((item) => (
          <div key={item.id} className="list-item">
            <div style={{ flex: 1, minWidth: 0 }}>
              <p className="list-item__title">{item.content}</p>
              <p className="list-item__meta">
                {item.scheduledAt
                  ? `Requested schedule ${new Date(item.scheduledAt).toLocaleString()}`
                  : "Publish when approved"}
                {item.adminNote ? ` · Note: ${item.adminNote}` : ""}
              </p>
              <div
                className="list-item__platforms"
                style={{ marginTop: "0.5rem" }}
              >
                {(item.platforms || []).map((p) => (
                  <PlatformBadge key={p} platformId={p} size="sm" />
                ))}
              </div>
            </div>
            <span
              className={`status-pill status-pill--${
                item.status === "approved"
                  ? "success"
                  : item.status === "rejected"
                    ? "failed"
                    : "pending"
              }`}
            >
              {item.status}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
