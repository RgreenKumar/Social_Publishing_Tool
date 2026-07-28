import { Navigate, Outlet } from "react-router-dom";
import { isLoggedIn } from "../api";

export default function ProtectedRoute() {
  if (!isLoggedIn()) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
