import { useEffect, useState } from "react";
import { api, addScheduledWatch, getScheduledWatchIds, removeScheduledWatch } from "../api";
import "./ScheduledPublishModal.css";

export default function ScheduledPublishNotifier() {
  const [modal, setModal] = useState(null);

  useEffect(() => {
    api
      .listScheduledPosts()
      .then((jobs) => {
        jobs
          .filter((j) => j.status === "scheduled" || j.status === "processing")
          .forEach((j) => addScheduledWatch(j.id));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      const ids = getScheduledWatchIds();
      if (ids.length === 0) return;

      for (const id of ids) {
        try {
          const job = await api.getScheduledPost(id);
          if (cancelled) return;

          if (job.status === "done") {
            removeScheduledWatch(id);
            setModal({
              type: "success",
              platforms: job.platforms || [],
              content: job.content,
              scheduledAt: job.scheduledAt,
            });
            return;
          }
          if (job.status === "failed") {
            removeScheduledWatch(id);
            setModal({
              type: "error",
              platforms: job.platforms || [],
              content: job.content,
              scheduledAt: job.scheduledAt,
            });
            return;
          }
        } catch {
          removeScheduledWatch(id);
        }
      }
    }

    poll();
    const timer = setInterval(poll, 10000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  if (!modal) return null;

  const preview =
    modal.content && modal.content.length > 120
      ? `${modal.content.slice(0, 120)}…`
      : modal.content || "(image post)";

  return (
    <div
      className="modal-overlay"
      role="presentation"
      onClick={() => setModal(null)}
    >
      <div
        className={`modal modal--${modal.type}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="scheduled-modal-title"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="modal__eyebrow">
          {modal.type === "success" ? "Published" : "Publish failed"}
        </p>
        <h2 id="scheduled-modal-title" className="modal__title">
          {modal.type === "success"
            ? "Your scheduled post is live!"
            : "Scheduled post could not publish"}
        </h2>
        <p className="modal__body">
          {modal.type === "success" ? (
            <>
              Posted to{" "}
              <strong>{(modal.platforms || []).join(" · ")}</strong> at{" "}
              {new Date(modal.scheduledAt).toLocaleString()}.
            </>
          ) : (
            <>
              Publishing failed for{" "}
              <strong>{(modal.platforms || []).join(" · ")}</strong>. Check
              Accounts and try again.
            </>
          )}
        </p>
        <blockquote className="modal__quote">{preview}</blockquote>
        <button
          type="button"
          className="btn btn--primary modal__btn"
          onClick={() => setModal(null)}
        >
          OK
        </button>
      </div>
    </div>
  );
}
