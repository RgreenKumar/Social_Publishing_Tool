import { NavLink, Outlet, useNavigate } from "react-router-dom";
import BrandLogo from "./BrandLogo";
import ScheduledPublishNotifier from "./ScheduledPublishNotifier";
import { api, clearSession, getStoredUser, isAdmin } from "../api";
import "./AppShell.css";

export default function AppShell() {
  const navigate = useNavigate();
  const user = getStoredUser();
  const admin = isAdmin();

  const nav = admin
    ? [
        { to: "/app", end: true, label: "Overview" },
        { to: "/app/compose", label: "Compose" },
        { to: "/app/accounts", label: "Accounts" },
        { to: "/app/history", label: "History" },
        { to: "/app/scheduled", label: "Scheduled" },
        { to: "/app/team", label: "Team" },
        { to: "/app/approvals", label: "Approvals" },
      ]
    : [
        { to: "/app", end: true, label: "Overview" },
        { to: "/app/compose", label: "Compose" },
        { to: "/app/history", label: "History" },
        { to: "/app/scheduled", label: "Scheduled" },
        { to: "/app/requests", label: "My requests" },
      ];

  async function handleSignOut() {
    try {
      await api.logout();
    } catch {
      // ignore network errors on logout
    }
    clearSession();
    navigate("/");
  }

  return (
    <div className="shell">
      <aside className="shell__top">
        <button
          type="button"
          className="shell__brand"
          onClick={() => navigate("/app")}
        >
          <BrandLogo size={32} />
        </button>

        <nav className="shell__nav" aria-label="Main">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `shell__link${isActive ? " shell__link--active" : ""}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="shell__aside">
          <span className="shell__user">
            {user?.name || "User"}
            {user?.role ? ` · ${user.role === "ADMIN" ? "Admin" : "Member"}` : ""}
          </span>
          <button type="button" className="shell__signout" onClick={handleSignOut}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="shell__main">
        <Outlet />
      </main>
      <ScheduledPublishNotifier />
    </div>
  );
}
