import { PLATFORMS } from "../data/mock";
import "./PlatformBadge.css";

export default function PlatformBadge({ platformId, size = "md" }) {
  const platform = PLATFORMS.find((p) => p.id === platformId);
  if (!platform) return null;

  return (
    <span
      className={`platform-badge platform-badge--${size}`}
      style={{ "--platform-color": platform.color }}
      title={platform.name}
    >
      <span className="platform-badge__dot" />
      {platform.short}
    </span>
  );
}
