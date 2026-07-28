import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import PlatformBadge from "../components/PlatformBadge";
import { PLATFORMS, isLivePlatform } from "../data/mock";
import { api, getStoredToken } from "../api";

const BACKEND_URL =
  (process.env.REACT_APP_API_URL || "http://localhost:8080").replace(/\/$/, "");

function liveLabel(platform) {
  return isLivePlatform(platform) ? " (live)" : " (simulated)";
}

function connectHint(platform) {
  if (platform === "linkedin") return "Not connected — uses LinkedIn login";
  if (platform === "facebook") return "Not connected — uses configured Facebook Page";
  if (platform === "threads") return "Not connected — uses configured Threads token";
  return "Not connected";
}

export default function ConnectAccounts() {
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busyId, setBusyId] = useState("");
  const [searchParams, setSearchParams] = useSearchParams();

  async function load() {
    const data = await api.listAccounts();
    setAccounts(data);
  }

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    const linkedin = searchParams.get("linkedin");
    const message = searchParams.get("message");
    if (!linkedin) return;

    if (linkedin === "connected") {
      setInfo(
        message
          ? `LinkedIn connected as ${message}`
          : "LinkedIn connected successfully"
      );
      setError("");
      load().catch((err) => setError(err.message));
    } else if (linkedin === "error") {
      setError(message || "LinkedIn connection failed");
    }
    setSearchParams({}, { replace: true });
  }, [searchParams, setSearchParams]);

  async function toggle(acc) {
    setError("");
    setInfo("");
    setBusyId(acc.id);
    try {
      if (acc.connected) {
        await api.disconnectAccount(acc.id);
        await load();
      } else if (acc.platform === "linkedin") {
        const token = getStoredToken();
        if (!token) {
          throw new Error("Please sign in again before connecting LinkedIn");
        }
        window.location.href = `${BACKEND_URL}/api/oauth/linkedin/redirect?token=${encodeURIComponent(token)}`;
        return;
      } else {
        const connected = await api.connectAccount({
          platform: acc.platform,
          displayName: acc.displayName,
        });
        if (acc.platform === "facebook") {
          setInfo(`Facebook connected: ${connected.displayName || "Page"}`);
        } else if (acc.platform === "threads") {
          setInfo(`Threads connected: ${connected.displayName || "account"}`);
        }
        await load();
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Accounts</p>
      <h1 className="page__title">Connected social accounts</h1>
      <p className="page__lead">
        LinkedIn uses OAuth. Facebook and Threads use configured tokens.
        Instagram is still simulated.
      </p>

      {error && <p className="form-error">{error}</p>}
      {info && <p className="form-success">{info}</p>}

      <div className="panel fade-rise fade-rise-delay-1">
        {accounts.map((acc) => {
          const meta = PLATFORMS.find((p) => p.id === acc.platform);
          return (
            <div key={acc.id} className="account-row">
              <PlatformBadge platformId={acc.platform} />
              <div className="account-row__info">
                <p className="account-row__name">{meta?.name || acc.platform}</p>
                <p className="account-row__meta">
                  {acc.connected
                    ? `${acc.displayName} · Connected${liveLabel(acc.platform)}`
                    : connectHint(acc.platform)}
                </p>
              </div>
              <button
                type="button"
                className={`btn btn--sm ${acc.connected ? "btn--danger" : "btn--primary"}`}
                disabled={busyId === acc.id}
                onClick={() => toggle(acc)}
              >
                {busyId === acc.id
                  ? "…"
                  : acc.connected
                    ? "Disconnect"
                    : "Connect"}
              </button>
            </div>
          );
        })}
        {accounts.length === 0 && (
          <p className="list-item__meta">No accounts loaded from API.</p>
        )}
      </div>
    </div>
  );
}
