import { Link } from "react-router-dom";
import BrandLogo from "../components/BrandLogo";
import LandingSlideshow from "../components/LandingSlideshow";

export default function Landing() {
  return (
    <div className="landing">
      <header className="landing__nav fade-rise">
        <Link to="/" className="landing__logo">
          <BrandLogo size={40} />
        </Link>
        <div className="landing__nav-actions">
          <Link to="/login" className="btn btn--ghost btn--sm">
            Sign in
          </Link>
          <Link to="/signup" className="btn btn--primary btn--sm">
            Get started
          </Link>
        </div>
      </header>

      <section className="landing__hero">
        <div className="landing__hero-copy fade-rise fade-rise-delay-1">
          <img
            src={`${process.env.PUBLIC_URL}/logo.png`}
            alt="PostFusion"
            className="landing__hero-logo fade-rise"
          />
          <h1 className="landing__brand">PostFusion</h1>
          <p className="landing__headline">One Click, Every Platform</p>
          <p className="landing__sub">
            Connect your social accounts and publish to Facebook, Instagram, and
            LinkedIn from one sharp workspace.
          </p>
          <div className="landing__cta">
            <Link to="/signup" className="btn btn--amber">
              Create free account
            </Link>
            <Link to="/login" className="btn btn--ghost">
              I already have one
            </Link>
          </div>
        </div>

        <div className="landing__visual fade-rise fade-rise-delay-2">
          <LandingSlideshow />
        </div>
      </section>
    </div>
  );
}
