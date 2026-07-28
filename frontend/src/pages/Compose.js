import { useEffect, useMemo, useState } from "react";
import PlatformBadge from "../components/PlatformBadge";
import { PLATFORMS, isLivePlatform } from "../data/mock";
import { api } from "../api";

export default function Compose() {
  const [content, setContent] = useState("");
  const [selected, setSelected] = useState([]);
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState("");
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduledLocal, setScheduledLocal] = useState("");
  const [accounts, setAccounts] = useState([]);
  const [result, setResult] = useState(null);
  const [scheduledResult, setScheduledResult] = useState(null);
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

  function togglePlatform(id) {
    if (!connectedIds.has(id)) return;
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
    );
    setResult(null);
  }

  function clearImage() {
    setImageFile(null);
    setResult(null);
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
    try {
      const post = await api.publishPost({
        content: content.trim(),
        platforms: selected,
        imageFile,
        scheduledAt,
      });
      if (post?.status === "scheduled") {
        setScheduledResult(post);
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

  async function handlePublish(e) {
    e.preventDefault();
    await submitPost("now");
  }

  const liveOk = result?.platforms?.filter(
    (p) => isLivePlatform(p.platform) && p.status === "success"
  );
  const threadsSelected = selected.includes("threads");

  const canPublish =
    selected.length > 0 && (Boolean(content.trim()) || Boolean(imageFile));

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Compose</p>
      <h1 className="page__title">New post</h1>
      <p className="page__lead">
        Write a caption, attach an image, and publish live to Facebook, LinkedIn,
        and/or Threads. Or schedule it for automatic publishing later.
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
              : "JPG, PNG, or WebP — live on Facebook & LinkedIn; Threads images need PUBLIC_BASE_URL"}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0] || null;
                setImageFile(file);
                setResult(null);
                setError("");
              }}
            />
          </label>
          {threadsSelected && imageFile && (
            <p className="form-error">
              Threads image posts require <code>PUBLIC_BASE_URL</code> in backend{" "}
              <code>.env</code> (public HTTPS URL like ngrok).
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

          <div className="compose__schedule">
            <label className="compose__schedule-toggle">
              <input
                type="checkbox"
                checked={scheduleEnabled}
                onChange={(e) => {
                  setScheduleEnabled(e.target.checked);
                  setScheduledResult(null);
                  setError("");
                }}
              />
              <span>Schedule publish</span>
            </label>
            {scheduleEnabled && (
              <input
                type="datetime-local"
                value={scheduledLocal}
                onChange={(e) => {
                  setScheduledLocal(e.target.value);
                  setScheduledResult(null);
                  setError("");
                }}
              />
            )}
          </div>

          <div className="compose__actions">
            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading || !canPublish}
            >
              {loading ? "Publishing…" : "Publish now"}
            </button>
            <button
              type="button"
              className="btn btn--primary"
              disabled={loading || !canPublish || !scheduleEnabled || !scheduledLocal}
              onClick={() => submitPost("schedule")}
            >
              {loading ? "Scheduling…" : "Schedule"}
            </button>
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() => {
                setContent("");
                clearImage();
                setScheduleEnabled(false);
                setScheduledLocal("");
                setResult(null);
                setScheduledResult(null);
                setError("");
              }}
            >
              Clear
            </button>
          </div>

          {error && <p className="form-error">{error}</p>}

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
                        ? "Connect first"
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
            <div className="compose__preview">
              {content || (imageFile ? "(image only)" : "Your caption will appear here")}
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}
