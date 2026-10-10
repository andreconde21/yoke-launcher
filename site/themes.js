// Omarchy palettes (basecamp/omarchy themes/*/colors.toml, MIT), the same
// values Yoke and Conductore ship. Picking one re-themes the page.
const THEMES = {
  "tokyo-night":      { name: "Tokyo Night",      bg: "#1A1B26", fg: "#A9B1D6", accent: "#7AA2F7", raised: "#24283B" },
  "gruvbox":          { name: "Gruvbox",          bg: "#282828", fg: "#D4BE98", accent: "#7DAEA3", raised: "#3C3836" },
  "everforest":       { name: "Everforest",       bg: "#2D353B", fg: "#D3C6AA", accent: "#7FBBB3", raised: "#343F44" },
  "ristretto":        { name: "Ristretto",        bg: "#2C2525", fg: "#E6D9DB", accent: "#F38D70", raised: "#3D2F2A" },
  "osaka-jade":       { name: "Osaka Jade",       bg: "#111C18", fg: "#C1C497", accent: "#509475", raised: "#23372B" },
  "nord":             { name: "Nord",             bg: "#2E3440", fg: "#D8DEE9", accent: "#81A1C1", raised: "#3B4252" },
  "rose-pine":        { name: "Rosé Pine Dawn",   bg: "#FAF4ED", fg: "#575279", accent: "#56949F", raised: "#F2E9E1" },
  "catppuccin-latte": { name: "Catppuccin Latte", bg: "#EFF1F5", fg: "#4C4F69", accent: "#1E66F5", raised: "#DCE0E8" },
};

function applyTheme(id) {
  const t = THEMES[id] || THEMES["tokyo-night"];
  const root = document.documentElement.style;
  root.setProperty("--bg", t.bg);
  root.setProperty("--fg", t.fg);
  root.setProperty("--accent", t.accent);
  root.setProperty("--raised", t.raised);
  document.querySelectorAll("[data-theme-id]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.themeId === id)));
  const label = document.getElementById("theme-name");
  if (label) label.textContent = t.name;
  try { localStorage.setItem("yoke-theme", id); } catch (_) {}
}

function buildThemePicker() {
  const list = document.getElementById("themes");
  if (!list) return;
  for (const [id, t] of Object.entries(THEMES)) {
    const b = document.createElement("button");
    b.type = "button";
    b.dataset.themeId = id;
    b.title = t.name;
    b.setAttribute("aria-label", t.name);
    b.style.setProperty("--sw-bg", t.bg);
    b.style.setProperty("--sw-fg", t.fg);
    b.style.setProperty("--sw-accent", t.accent);
    b.innerHTML = "<span>Aa</span>";
    b.addEventListener("click", () => applyTheme(id));
    list.appendChild(b);
  }
}

buildThemePicker();
let saved = null;
try { saved = localStorage.getItem("yoke-theme"); } catch (_) {}
applyTheme(saved && THEMES[saved] ? saved : "tokyo-night");
