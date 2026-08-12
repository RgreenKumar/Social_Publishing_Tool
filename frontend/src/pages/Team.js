import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { api, isAdmin } from "../api";

export default function Team() {
  const admin = isAdmin();
  const [members, setMembers] = useState([]);
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState("");

  useEffect(() => {
    if (!admin) return undefined;
    api
      .listTeamMembers()
      .then(setMembers)
      .catch((err) => setError(err.message));
    return undefined;
  }, [admin]);

  if (!admin) {
    return <Navigate to="/app" replace />;
  }

  async function load() {
    const data = await api.listTeamMembers();
    setMembers(data);
  }

  async function handleCreate(e) {
    e.preventDefault();
    setError("");
    setInfo("");
    setLoading(true);
    try {
      await api.createTeamMember({ name, username, password });
      setName("");
      setUsername("");
      setPassword("");
      setInfo(`Team member @${username.trim().toLowerCase()} created`);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id) {
    setBusyId(id);
    setError("");
    try {
      await api.deleteTeamMember(id);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="page fade-rise">
      <p className="page__eyebrow">Team</p>
      <h1 className="page__title">Team members</h1>
      <p className="page__lead">
        Create usernames and passwords for teammates. They can draft posts; you
        approve before anything goes live.
      </p>

      {error && <p className="form-error">{error}</p>}
      {info && <p className="form-success">{info}</p>}

      <div
        className="compose"
        style={{ display: "grid", gap: "1rem", gridTemplateColumns: "1fr 1fr" }}
      >
        <form className="panel" onSubmit={handleCreate}>
          <h2 style={{ fontSize: "1.05rem", marginBottom: "0.85rem" }}>
            Add member
          </h2>
          <div className="field">
            <label htmlFor="member-name">Full name</label>
            <input
              id="member-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="member-username">Username</label>
            <input
              id="member-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              minLength={3}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="member-password">Password</label>
            <input
              id="member-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={6}
              required
            />
          </div>
          <button type="submit" className="btn btn--primary" disabled={loading}>
            {loading ? "Creating…" : "Create member"}
          </button>
        </form>

        <div className="panel">
          <h2 style={{ fontSize: "1.05rem", marginBottom: "0.85rem" }}>
            Members ({members.length})
          </h2>
          {members.length === 0 && (
            <p className="list-item__meta">No team members yet.</p>
          )}
          {members.map((m) => (
            <div key={m.id} className="account-row">
              <div className="account-row__info">
                <p className="account-row__name">{m.name}</p>
                <p className="account-row__meta">@{m.username}</p>
              </div>
              <button
                type="button"
                className="btn btn--sm btn--danger"
                disabled={busyId === m.id}
                onClick={() => handleDelete(m.id)}
              >
                {busyId === m.id ? "…" : "Remove"}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
