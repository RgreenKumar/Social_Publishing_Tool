import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { PLATFORMS, isLivePlatform } from "../data/mock";
import { api, addScheduledWatch, isMember } from "../api";
import "../components/ScheduledPublishModal.css";

function formatScheduleLabel(localValue) {
  if (!localValue) return "";
  const date = new Date(localValue);
  if (Number.isNaN(date.getTime())) return localValue;
  return date.toLocaleString(undefined, {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function minDateTimeLocal() {
  const d = new Date(Date.now() + 60_000);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function Compose() {
  const member = isMember();
  const [content, setContent] = useState("");
  const [selected, setSelected] = useState([]);
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState("");
  const [publishMode, setPublishMode] = useState("now");
  const [scheduledLocal, setScheduledLocal] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [accounts, setAccounts] = useState([]);
  const [result, setResult] = useState(null);
  const [scheduledResult, setScheduledResult] = useState(null);
  const [approvalResult, setApprovalResult] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api
      .listAccounts()
      .then((data) => {
        setAccounts(data);
        const connected = data.filter((a) => a.connected).map((a) => a.platform);
        const live = connected.filter((p) => isLivePlatform(p));
        setSelected(live.length > 0 ? live : connected.slice(0, 2));
      })
      .catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!imageFile) {
      setImagePreview("");
      return undefined;
    }
    const url = URL.createObjectURL(imageFile);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);

  const connectedIds = useMemo(
    () => new Set(accounts.filter((a) => a.connected).map((a) => a.platform)),
    [accounts]
  );

  const selectedPlatforms = useMemo(
    () => PLATFORMS.filter((p) => selected.includes(p.id)),
    [selected]
  );

  function togglePlatform(id) {
    if (!connectedIds.has(id)) return;
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
    );
    setResult(null);
    setApprovalResult(null);
  }

  function clearImage() {
    setImageFile(null);
    setResult(null);
    setApprovalResult(null);
  }

  function openScheduleConfirm() {
    if ((!content.trim() && !imageFile) || selected.length === 0) return;
    if (!scheduledLocal) {
      setError("Select a date and time to schedule the post");
      return;
    }
    const when = new Date(scheduledLocal);
    if (Number.isNaN(when.getTime()) || when.getTime() <= Date.now()) {
      setError("Schedule time must be in the future");
      return;
    }
    setError("");
    setConfirmOpen(true);
  }

  async function submitPost(mode) {
    if ((!content.trim() && !imageFile) || selected.length === 0) return;
    if (mode === "schedule" && !scheduledLocal) {
      setError("Select a date/time to schedule the post");
      return;
    }
    const scheduledAt =
      mode === "schedule" ? new Date(scheduledLocal).toISOString() : null;
    setLoading(true);
    setError("");
    setResult(null);
    setScheduledResult(null);
    setApprovalResult(null);
    try {
      const post = await api.publishPost({
        content: content.trim(),
        platforms: selected,
        imageFile,
        scheduledAt,
      });
      if (post?.status === "scheduled") {
        addScheduledWatch(post.id);
        setScheduledResult(post);
        setConfirmOpen(false);
        return;
      }
      setResult(post);

      const failedLive = post.platforms?.filter(
        (p) => isLivePlatform(p.platform) && p.status === "failed"
      );
      if (failedLive?.length) {
        setError(failedLive.map((p) => `${p.platform}: ${p.message}`).join(" · "));
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function submitApproval() {
    if ((!content.trim() && !imageFile) || selected.length === 0) return;
    let scheduledAt = null;
    if (publishMode === "later") {
      if (!scheduledLocal) {
        setError("Select a date/time, or switch to publish when approved");
        return;
      }
      const when = new Date(scheduledLocal);
      if (Number.isNaN(when.getTime()) || when.getTime() <= Date.now()) {
        setError("Schedule time must be in the future");
        return;
      }
      scheduledAt = when.toISOString();
    }
    setLoading(true);
    setError("");
    setApprovalResult(null);
    setResult(null);
    setScheduledResult(null);
    try {
      const data = await api.submitForApproval({
        content: content.trim(),
        platforms: selected,
        imageFile,
        scheduledAt,
      });
      setApprovalResult(data);
      setContent("");
      clearImage();
      setPublishMode("now");
      setScheduledLocal("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handlePublish(e) {
    e.preventDefault();
    if (member) {
      await submitApproval();
      return;
    }
    if (publishMode === "later") {
      openScheduleConfirm();
      return;
    }
    await submitPost("now");
  }

  const liveOk = result?.platforms?.filter(
    (p) => isLivePlatform(p.platform) && p.status === "success"
  );

  const canPublish =
    selected.length > 0 && (Boolean(content.trim()) || Boolean(imageFile));

  const canSchedule =
    canPublish && Boolean(scheduledLocal) && publishMode === "later";

  const canSubmitApproval =
    canPublish && (publishMode === "now" || Boolean(scheduledLocal));

  const previewText =
    content.trim() || (imageFile ? "(image only)" : "Your caption will appear here");

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Compose</p>
      <h1 className="page__title">New post</h1>
      <p className="page__lead">
        {member
          ? "Draft a caption, image, and platforms. Submit for your admin to approve before anything goes live."
          : "Write a caption, attach an image, and publish now — or schedule it for later after you confirm the time and preview."}
      </p>

      <form className="compose fade-rise fade-rise-delay-1" onSubmit={handlePublish}>
        <div className="panel">
          <div className="field">
            <label htmlFor="caption">Caption</label>
            <textarea
              id="caption"
              placeholder="Write the post for your networks…"
              value={content}
              onChange={(e) => {
                setContent(e.target.value);
                setResult(null);
                setApprovalResult(null);
                setError("");
              }}
            />
          </div>

          <label className="media-drop">
            <strong>
              {imageFile ? imageFile.name : "Add image (optional)"}
            </strong>
            {imageFile
              ? "Click to replace — image publishes live where supported"
              : "JPG, PNG, or WebP — live on Facebook, LinkedIn, Instagram & Threads"}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0] || null;
                setImageFile(file);
                setResult(null);
                setApprovalResult(null);
                setError("");
              }}
            />
          </label>
          {selected.includes("instagram") && !imageFile && (
            <p className="form-error">
              Instagram requires an image — caption-only posts are not supported.
            </p>
          )}

          {imagePreview && (
            <div className="compose__image-preview">
              <img src={imagePreview} alt="Selected for publish" />
              <button type="button" className="btn btn--ghost" onClick={clearImage}>
                Remove image
              </button>
            </div>
          )}

          <div className="compose__when">
            <p className="compose__when-label">
              {member ? "When to publish (after approval)" : "When to publish"}
            </p>
            <div className="compose__when-tabs" role="tablist" aria-label="Publish timing">
              <button
                type="button"
                role="tab"
                aria-selected={publishMode === "now"}
                className={`compose__when-tab${publishMode === "now" ? " compose__when-tab--on" : ""}`}
                onClick={() => {
                  setPublishMode("now");
                  setConfirmOpen(false);
                  setScheduledResult(null);
                  setError("");
                }}
              >
                {member ? "Publish when approved" : "Publish now"}
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={publishMode === "later"}
                className={`compose__when-tab${publishMode === "later" ? " compose__when-tab--on" : ""}`}
                onClick={() => {
                  setPublishMode("later");
                  setScheduledResult(null);
                  setError("");
                  if (!scheduledLocal) setScheduledLocal(minDateTimeLocal());
                }}
              >
                {member ? "Request schedule" : "Schedule for later"}
              </button>
            </div>

            {publishMode === "later" && (
              <div className="compose__schedule-card">
                <label className="compose__schedule-field" htmlFor="schedule-at">
                  Date &amp; time
                </label>
                <input
                  id="schedule-at"
                  type="datetime-local"
                  min={minDateTimeLocal()}
                  value={scheduledLocal}
                  onChange={(e) => {
                    setScheduledLocal(e.target.value);
                    setScheduledResult(null);
                    setError("");
                  }}
                />
                {scheduledLocal && (
                  <p className="compose__schedule-hint">
                    {member ? "Requested for" : "Will publish on"}{" "}
                    <strong>{formatScheduleLabel(scheduledLocal)}</strong>
                  </p>
                )}
              </div>
            )}
          </div>

          <div className="compose__actions">
            {member ? (
              <button
                type="submit"
                className="btn btn--primary"
                disabled={loading || !canSubmitApproval}
              >
                {loading ? "Submitting…" : "Submit for approval"}
              </button>
            ) : publishMode === "now" ? (
              <button
                type="submit"
                className="btn btn--primary"
                disabled={loading || !canPublish}
              >
                {loading ? "Publishing…" : "Publish now"}
              </button>
            ) : (
              <button
                type="button"
                className="btn btn--primary"
                disabled={loading || !canSchedule}
                onClick={openScheduleConfirm}
              >
                Review &amp; schedule
              </button>
            )}
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() => {
                setContent("");
                clearImage();
                setPublishMode("now");
                setScheduledLocal("");
                setConfirmOpen(false);
                setResult(null);
                setScheduledResult(null);
                setApprovalResult(null);
                setError("");
              }}
            >
              Clear
            </button>
          </div>

          {error && <p className="form-error">{error}</p>}

          {approvalResult && (
            <div className="toast" role="status">
              Submitted for approval. Track status on{" "}
              <Link to="/app/requests">My requests</Link>.
            </div>
          )}

          {liveOk?.length > 0 && (
            <div className="toast" role="status">
              Posted successfully to{" "}
              {liveOk.map((p) => p.platform).join(" · ")}. Check those feeds.
            </div>
          )}

          {scheduledResult && (
            <div className="toast" role="status">
              Scheduled for{" "}
              {new Date(scheduledResult.scheduledAt).toLocaleString()} on{" "}
              {(scheduledResult.platforms || []).join(" · ")}.
            </div>
          )}

          {result && !liveOk?.length && (
            <div className="toast" role="status">
              {result.platforms
                .map((p) => `${p.platform}: ${p.status}${p.message ? ` — ${p.message}` : ""}`)
                .join(" · ")}
            </div>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div className="panel">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.85rem" }}>
              Publish to
            </h2>
            <div className="platform-toggle">
              {PLATFORMS.map((platform) => {
                const connected = connectedIds.has(platform.id);
                const on = selected.includes(platform.id);
                const live = isLivePlatform(platform.id);
                return (
                  <button
                    key={platform.id}
                    type="button"
                    disabled={!connected}
                    className={`platform-toggle__item${on ? " platform-toggle__item--on" : ""}`}
                    onClick={() => togglePlatform(platform.id)}
                  >
                    <span className="platform-toggle__check">{on ? "✓" : ""}</span>
                    <PlatformBadge platformId={platform.id} />
                    <span style={{ flex: 1, fontWeight: 650 }}>
                      {platform.name}
                    </span>
                    <span
                      style={{
                        fontSize: "0.78rem",
                        color: "var(--muted)",
                        fontWeight: 600,
                      }}
                    >
                      {!connected
                        ? member
                          ? "Admin must connect"
                          : "Connect first"
                        : live
                          ? "Live"
                          : "Simulated"}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="panel">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.75rem" }}>
              Preview
            </h2>
            {imagePreview && (
              <img
                className="compose__preview-image"
                src={imagePreview}
                alt=""
              />
            )}
            <div className="compose__preview">{previewText}</div>
          </div>
        </div>
      </form>

      {!member && confirmOpen && (
        <div
          className="modal-overlay"
          role="presentation"
          onClick={() => !loading && setConfirmOpen(false)}
        >
          <div
            className="modal modal--confirm"
            role="dialog"
            aria-modal="true"
            aria-labelledby="schedule-confirm-title"
            onClick={(e) => e.stopPropagation()}
          >
            <p className="modal__eyebrow">Confirm schedule</p>
            <h2 id="schedule-confirm-title" className="modal__title">
              Review before scheduling
            </h2>
            <p className="modal__body">
              Check the time, platforms, and post below. Confirm only when
              everything looks right.
            </p>

            <div className="modal__meta">
              <div className="modal__meta-row">
                <span>Publish at</span>
                <strong>{formatScheduleLabel(scheduledLocal)}</strong>
              </div>
              <div className="modal__meta-row">
                <span>Platforms</span>
                <strong>
                  {selectedPlatforms.map((p) => p.name).join(" · ") || "—"}
                </strong>
              </div>
            </div>

            <div className="modal__preview">
              {imagePreview && (
                <img src={imagePreview} alt="" className="modal__preview-image" />
              )}
              <p className="modal__preview-text">{previewText}</p>
            </div>

            <div className="modal__actions">
              <button
                type="button"
                className="btn btn--ghost"
                disabled={loading}
                onClick={() => setConfirmOpen(false)}
              >
                Edit
              </button>
              <button
                type="button"
                className="btn btn--primary"
                disabled={loading}
                onClick={() => submitPost("schedule")}
              >
                {loading ? "Scheduling…" : "Confirm schedule"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
