"""Extract glossy play-logo onto transparent PNG using strong-highlight seeds."""
from PIL import Image, ImageFilter
from collections import deque
import os
import math

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(ROOT, r"app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png")


def is_strong_feature(r, g, b, a):
    """Only bright glass highlights / light borders — sparse and reliable."""
    if a < 10:
        return False
    if g > 80 or b > 80:
        return True
    if r > 220 and g > 50 and b > 50:
        return True
    # dark outline / drop shadow accents
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if lum < 55 and r < 140:
        return True
    return False


def is_growable(r, g, b, a):
    if a < 10:
        return False
    if is_strong_feature(r, g, b, a):
        return True
    # reds that can form logo body
    return r > 90 and g < 90 and b < 90


def main():
    img = Image.open(SRC).convert("RGBA")
    w, h = img.size
    px = img.load()

    dist = [[math.inf] * h for _ in range(w)]
    q = deque()
    for x in range(w):
        for y in range(h):
            if is_strong_feature(*px[x, y]):
                dist[x][y] = 0
                q.append((x, y))

    print("seeds", len(q))
    # Tight growth: fill logo body without pulling in surrounding red bg
    MAX_DIST = 8
    while q:
        x, y = q.popleft()
        d = dist[x][y]
        if d >= MAX_DIST:
            continue
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if not (0 <= nx < w and 0 <= ny < h):
                continue
            nd = d + 1
            if nd >= dist[nx][ny]:
                continue
            if not is_growable(*px[nx, ny]):
                continue
            dist[nx][ny] = nd
            q.append((nx, ny))

    mask = Image.new("L", (w, h), 0)
    mp = mask.load()
    for x in range(w):
        for y in range(h):
            d = dist[x][y]
            if d == math.inf:
                continue
            if d <= MAX_DIST - 3:
                mp[x, y] = 255
            else:
                mp[x, y] = int(255 * (MAX_DIST - d) / 3)

    # keep largest component only
    visited = [[False] * h for _ in range(w)]
    best = []
    for sx in range(w):
        for sy in range(h):
            if visited[sx][sy] or mp[sx, sy] == 0:
                continue
            comp = []
            qq = deque([(sx, sy)])
            visited[sx][sy] = True
            while qq:
                x, y = qq.popleft()
                comp.append((x, y))
                for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny] and mp[nx, ny]:
                        visited[nx][ny] = True
                        qq.append((nx, ny))
            if len(comp) > len(best):
                best = comp

    clean = Image.new("L", (w, h), 0)
    cp = clean.load()
    for x, y in best:
        cp[x, y] = mp[x, y]

    # mild feather
    mask = clean.filter(ImageFilter.GaussianBlur(0.6))
    mp = mask.load()

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    for x in range(w):
        for y in range(h):
            m = mp[x, y]
            if m == 0:
                continue
            r, g, b, a = px[x, y]
            op[x, y] = (r, g, b, min(a, m))

    densities = {
        "xxxhdpi": 192,
        "xxhdpi": 144,
        "xhdpi": 96,
        "hdpi": 72,
        "mdpi": 48,
    }
    for dens, size in densities.items():
        folder = os.path.join(ROOT, "app", "src", "main", "res", f"drawable-{dens}")
        os.makedirs(folder, exist_ok=True)
        resized = out if size == w else out.resize((size, size), Image.Resampling.LANCZOS)
        path = os.path.join(folder, "ic_splash_logo.png")
        resized.save(path)
        print("saved", path, resized.size)

    alphas = [p[3] for p in out.getdata()]
    print(
        "opaque",
        sum(1 for a in alphas if a > 200),
        "transparent",
        sum(1 for a in alphas if a == 0),
        "partial",
        sum(1 for a in alphas if 0 < a <= 200),
    )
    print("bbox", out.getbbox())

    for name, color in [("preview_red", (193, 3, 9, 255)), ("preview_yellow", (253, 221, 14, 255))]:
        bg = Image.new("RGBA", (384, 384), color)
        bg.paste(out, (96, 96), out)
        bg.convert("RGB").save(os.path.join(ROOT, f"{name}.png"))
        print("preview", name)


if __name__ == "__main__":
    main()
