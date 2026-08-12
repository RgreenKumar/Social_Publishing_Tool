export const PLATFORMS = [
  {
    id: "facebook",
    name: "Facebook",
    color: "#1877F2",
    short: "Fb",
  },
  {
    id: "instagram",
    name: "Instagram",
    color: "#E1306C",
    short: "Ig",
  },
  {
    id: "threads",
    name: "Threads",
    color: "#101010",
    short: "Th",
  },
  {
    id: "linkedin",
    name: "LinkedIn",
    color: "#0A66C2",
    short: "In",
  },
];

export const LIVE_PLATFORMS = new Set(["linkedin", "facebook", "instagram", "threads"]);

export function isLivePlatform(id) {
  return LIVE_PLATFORMS.has(id);
}

export const MOCK_ACCOUNTS = [
  {
    id: "acc-fb",
    platform: "facebook",
    displayName: "Demo Facebook Page",
    connected: true,
    connectedAt: "2026-07-12",
  },
  {
    id: "acc-ig",
    platform: "instagram",
    displayName: "@postfusion",
    connected: true,
    connectedAt: "2026-07-14",
  },
  {
    id: "acc-li",
    platform: "linkedin",
    displayName: "PostFusion",
    connected: false,
    connectedAt: null,
  },
];

export const MOCK_POSTS = [
  {
    id: "p1",
    content:
      "Shipping our summer update — one compose screen, many networks. Try PostFusion today.",
    createdAt: "2026-07-18T09:30:00",
    platforms: [
      { platform: "facebook", status: "success" },
      { platform: "instagram", status: "success" },
    ],
  },
  {
    id: "p2",
    content: "Behind the scenes: connecting LinkedIn OAuth tokens securely.",
    createdAt: "2026-07-16T15:10:00",
    platforms: [
      { platform: "facebook", status: "success" },
      { platform: "instagram", status: "failed" },
    ],
  },
  {
    id: "p3",
    content: "Monday motivation: write once, publish everywhere.",
    createdAt: "2026-07-14T08:00:00",
    platforms: [
      { platform: "facebook", status: "success" },
      { platform: "instagram", status: "success" },
    ],
  },
];
