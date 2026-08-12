import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { api, isAdmin } from "../api";

export default function Approvals() {
  const admin = isAdmin();
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busyId, setBusyId] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!admin) {
      setLoading(false);
      return undefined;
    }
    api
      .listPendingApprovals()
      .then(setItems)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
    return undefined;
  }, [admin]);

  if (!admin) {
    return <Navigate to="/app" replace />;
  }

  async function load() {
    const data = await api.listPendingApprovals();
    setItems(data);
  }

  async function approve(id) {
    setBusyId(id);
    setError("");
    setInfo("");
    try {
      await api.approveRequest(id);
      setInfo("Post approved and published (or scheduled).");
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId("");
    }
  }

  async function reject(id) {
    const note = window.prompt("Optional rejection note:") || "";
    setBusyId(id);
    setError("");
    setInfo("");
    try {
      await api.rejectRequest(id, note);
      setInfo("Request rejected.");
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Approvals</p>
      <h1 className="page__title">Pending posts</h1>
      <p className="page__lead">
        Review team drafts. Approve to publish with your connected accounts, or
        reject.
      </p>

      {error && <p className="form-error">{error}</p>}
      {info && <p className="form-success">{info}</p>}
      {loading && <p className="page__lead">Loading…</p>}

      <div className="panel fade-rise fade-rise-delay-1">
        {!loading && items.length === 0 && (
          <p className="list-item__meta">No pending approvals.</p>
        )}
        {items.map((item) => (
          <div
            key={item.id}
            className="list-item"
            style={{ alignItems: "flex-start" }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <p className="list-item__title">{item.content}</p>
              <p className="list-item__meta">
                From {item.memberName || "member"}
                {item.memberUsername ? ` (@${item.memberUsername})` : ""}
                {item.scheduledAt
                  ? ` · Schedule ${new Date(item.scheduledAt).toLocaleString()}`
                  : " · Publish now"}
                {item.hasImage ? " · Includes image" : ""}
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
            <div style={{ display: "flex", gap: "0.5rem", flexShrink: 0 }}>
              <button
                type="button"
                className="btn btn--sm btn--primary"
                disabled={busyId === item.id}
                onClick={() => approve(item.id)}
              >
                {busyId === item.id ? "…" : "Approve"}
              </button>
              <button
                type="button"
                className="btn btn--sm btn--danger"
                disabled={busyId === item.id}
                onClick={() => reject(item.id)}
              >
                Reject
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
