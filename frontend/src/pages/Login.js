import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import BrandLogo from "../components/BrandLogo";
import { api, saveSession } from "../api";

export default function Login() {
  const navigate = useNavigate();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await api.login({ login, password });
      saveSession(data);
      navigate("/app");
    } catch (err) {
      setError(err.message || "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth">
      <div className="panel auth__card">
        <Link to="/" className="auth__brand">
          <BrandLogo size={34} />
        </Link>
        <h1 className="auth__title">Welcome back</h1>
        <p className="auth__lead">
          Admin: use email. Team member: use username.
        </p>

        <form onSubmit={handleSubmit} autoComplete="off">
          <div className="field">
            <label htmlFor="login">Email or username</label>
            <input
              id="login"
              name="login"
              type="text"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              autoComplete="username"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          {error && <p className="form-error">{error}</p>}
          <button
            type="submit"
            className="btn btn--primary"
            style={{ width: "100%" }}
            disabled={loading}
          >
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p className="auth__switch">
          New admin? <Link to="/signup">Create an admin account</Link>
        </p>
      </div>
    </div>
  );
}
