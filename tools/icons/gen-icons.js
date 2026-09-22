/**
 * Generates `app/src/main/java/com/cycling/mynote/ui/icons/MyNoteIcons.kt` from Lucide.
 *
 * Run with `node tools/icons/gen-icons.js` from the project root. Only the icons listed in ICONS
 * are fetched, and each one is downloaded once into `tools/icons/lucide-cache/` so regeneration
 * works offline afterwards.
 *
 * Why generate rather than depend on an icon library: the app draws 64 glyphs, and pulling in a
 * Compose Multiplatform icon pack would ship well over a thousand `ImageVector`s for them. Every
 * icon here is the upstream Lucide geometry, converted element by element.
 */
const fs = require('fs');
const path = require('path');

const ICONS = [
  'notebook-text', 'search', 'calendar-days', 'settings',
  'folder-tree', 'ellipsis-vertical', 'arrow-up-down', 'list', 'layout-grid', 'plus',
  'chevron-right', 'chevron-left', 'chevron-down', 'x', 'check', 'pin', 'file-text', 'feather',
  'folder-open', 'folder', 'folder-plus', 'file-plus', 'history',
  'sun', 'moon', 'smartphone', 'database', 'trash-2', 'pencil', 'info', 'archive',
  'refresh-cw', 'external-link', 'clock', 'tag', 'wrap-text',
  'pencil-line', 'columns-2', 'eye', 'heading-1', 'bold', 'italic', 'list-checks',
  'text-quote', 'code', 'link', 'image', 'corner-down-left', 'save',
  'zap', 'globe', 'message-circle', 'notebook-pen', 'share-2', 'more-horizontal',
  'calendar-plus', 'inbox', 'cloud', 'alert-circle', 'loader', 'type', 'undo-2', 'redo-2',
  'terminal',
];

const ALLOWED = new Set(ICONS);
const TOOL_DIR = path.resolve(__dirname);
const PROJECT_DIR = path.resolve(TOOL_DIR, '..', '..');
const CACHE_DIR = path.resolve(TOOL_DIR, 'lucide-cache');
const ICON_DIR = path.resolve(PROJECT_DIR, 'app', 'src', 'main', 'java', 'com', 'cycling', 'mynote', 'ui', 'icons');

// Every icon name comes from the ALLOWED whitelist above, and the resolved path is checked to stay
// inside the cache directory, so no untrusted segment can escape it.
function cachePath(name) {
  const candidate = path.resolve(CACHE_DIR, name + '.svg');
  if (!candidate.startsWith(CACHE_DIR + path.sep)) throw new Error('refusing path outside cache: ' + name);
  return candidate;
}

const ELEMENT_RE = /<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*?)\/?>/g;
// Attribute names may contain digits (`x1`, `y1`, `x2`, `y2`); a letters-only class silently skipped
// them, which produced `NaN` coordinates in every <line>-based icon.
const ATTR_RE = /([a-zA-Z][a-zA-Z0-9-]*)="([^"]*)"/g;

const n = v => Number(v).toString();

/**
 * Converts an SVG into one path per element.
 *
 * One path per element rather than one concatenated string: SVG resets the current point for each
 * element, so merging them lets a later element's *relative* `m` continue from the previous
 * element's end. Lucide's `x` is `M18 6 6 18` followed by `m6 6 12 12`, and merging those rendered
 * it as a single diagonal stroke.
 *
 * @returns an array of `{d, filled}`; `filled` is true only for an element that opts into a fill.
 */
function elementPaths(svg) {
  const paths = [];
  for (const match of svg.matchAll(ELEMENT_RE)) {
    const tag = match[1];
    const attrs = Object.fromEntries(Array.from(match[2].matchAll(ATTR_RE), a => [a[1], a[2]]));
    const filled = Boolean(attrs.fill && attrs.fill !== 'none');
    let d = null;

    if (tag === 'path') {
      d = attrs.d;
    } else if (tag === 'circle') {
      const r = Number(attrs.r);
      const step = n(2 * r);
      d = `M${n(Number(attrs.cx) - r)} ${n(attrs.cy)}a${attrs.r} ${attrs.r} 0 1 0 ${step} 0a${attrs.r} ${attrs.r} 0 1 0 -${step} 0`;
    } else if (tag === 'ellipse') {
      const rx = Number(attrs.rx);
      const step = n(2 * rx);
      d = `M${n(Number(attrs.cx) - rx)} ${n(attrs.cy)}a${attrs.rx} ${attrs.ry} 0 1 0 ${step} 0a${attrs.rx} ${attrs.ry} 0 1 0 -${step} 0`;
    } else if (tag === 'rect') {
      const x = Number(attrs.x), y = Number(attrs.y), w = Number(attrs.width), h = Number(attrs.height);
      const r = attrs.rx !== undefined ? Number(attrs.rx) : attrs.ry !== undefined ? Number(attrs.ry) : 0;
      d = r > 0
        ? `M${n(x + r)} ${n(y)}H${n(x + w - r)}A${n(r)} ${n(r)} 0 0 1 ${n(x + w)} ${n(y + r)}` +
          `V${n(y + h - r)}A${n(r)} ${n(r)} 0 0 1 ${n(x + w - r)} ${n(y + h)}` +
          `H${n(x + r)}A${n(r)} ${n(r)} 0 0 1 ${n(x)} ${n(y + h - r)}` +
          `V${n(y + r)}A${n(r)} ${n(r)} 0 0 1 ${n(x + r)} ${n(y)}Z`
        : `M${n(x)} ${n(y)}H${n(x + w)}V${n(y + h)}H${n(x)}Z`;
    } else if (tag === 'line') {
      d = `M${n(attrs.x1)} ${n(attrs.y1)}L${n(attrs.x2)} ${n(attrs.y2)}`;
    } else if (tag === 'polyline' || tag === 'polygon') {
      const body = attrs.points.trim().split(/\s+/).map((p, i) => (i === 0 ? 'M' : 'L') + p).join('');
      d = tag === 'polygon' ? body + 'Z' : body;
    }

    if (d) paths.push({ d, filled });
  }
  return paths;
}

/** Fails loudly rather than emitting a path Compose cannot parse. */
function verify(name, paths) {
  if (paths.length === 0) throw new Error(name + ': no drawable elements');
  paths.forEach((p) => {
    if (/NaN|undefined|null/.test(p.d)) {
      throw new Error(`${name}: path data is not numeric: ${p.d}`);
    }
  });
  return paths;
}

async function fetchIcon(name) {
  if (!ALLOWED.has(name)) throw new Error('icon not whitelisted: ' + name);
  const file = cachePath(name);
  if (fs.existsSync(file) && fs.statSync(file).size > 0) return fs.readFileSync(file, 'utf8');
  const res = await fetch(`https://unpkg.com/lucide-static@latest/icons/${name}.svg`);
  if (!res.ok) throw new Error(`fetch ${name}: ${res.status}`);
  const text = await res.text();
  fs.mkdirSync(CACHE_DIR, { recursive: true });
  fs.writeFileSync(file, text);
  return text;
}

function propertyName(name) {
  return name.split('-').map((s, i) => (i === 0 ? s : s.charAt(0).toUpperCase() + s.slice(1))).join('');
}

(async () => {
  const entries = [];
  for (const name of ICONS) {
    entries.push({ name, paths: verify(name, elementPaths(await fetchIcon(name))) });
  }

  const body = entries.map(({ name, paths }) => {
    const args = paths
      .map(p => `LucidePath("${p.d}", filled = ${p.filled})`)
      .join(',\n            ');
    return `    /** \`${name}\` from Lucide (ISC). */\n` +
      `    val ${propertyName(name)}: ImageVector by lazy {\n` +
      `        lucide(\n            "${name}",\n            ${args},\n        )\n    }`;
  }).join('\n\n');

  const out = `package com.cycling.mynote.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One drawable element of a Lucide glyph: its path data, and whether it is filled or stroked. */
private class LucidePath(val d: String, val filled: Boolean)

/**
 * Builds an icon from Lucide's elements, as stroke-drawn vector paths.
 *
 * Each element becomes its own path rather than one concatenated string: SVG resets the current
 * point per element, so merging them would let a later element's relative commands continue from
 * the previous element's end and draw somewhere else entirely.
 *
 * The colour always comes from the caller through \`Icon(tint = ...)\`; the black here is only a
 * placeholder that the tint replaces.
 */
private fun lucide(name: String, vararg paths: LucidePath): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path.d).toNodes(),
                fill = if (path.filled) SolidColor(Color.Black) else null,
                stroke = if (path.filled) null else SolidColor(Color.Black),
                strokeLineWidth = if (path.filled) 0f else STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Lucide's own stroke width for the 24 unit grid. */
private const val STROKE_WIDTH = 2f

/**
 * The subset of [Lucide](https://lucide.dev) the app draws, expressed as [ImageVector]s so only the
 * glyphs actually used are shipped instead of a full icon pack.
 */
object MyNoteIcons {

${body}
}
`;

  fs.mkdirSync(ICON_DIR, { recursive: true });
  const target = path.resolve(ICON_DIR, 'MyNoteIcons.kt');
  if (!target.startsWith(ICON_DIR + path.sep)) throw new Error('refusing path outside icon dir');
  fs.writeFileSync(target, out);
  console.log(`wrote ${entries.length} icons to ${target}`);
})();
