"""Apply user-provided transparent logo to splash / launcher / TV banner assets."""
from PIL import Image, ImageDraw
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(
    os.environ.get("USERPROFILE", ""),
    r".cursor\projects\d-xiangmu-mytv-android-main\assets"
    r"\c__Users_feige_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"3db621d51a3192995c0a7575dd48e12a_images_ic_launcher_foreground-"
    r"bf56240a-5d48-4adb-92f1-208a05e75e9d.png",
)
# Fallback: also accept a copied path under tools/
ALT_SRC = os.path.join(ROOT, "tools", "user_icon_source.png")

BG = (253, 221, 14, 255)  # #FDDD0E 柠檬黄主题
RES = os.path.join(ROOT, "app", "src", "main", "res")

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}


def load_logo():
    path = SRC if os.path.exists(SRC) else ALT_SRC
    img = Image.open(path).convert("RGBA")
    # Ensure pure transparency outside logo (knock out near-black leftovers)
    px = img.load()
    w, h = img.size
    for x in range(w):
        for y in range(h):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # near-black fringe from removebg preview checkers / matte
            if r < 20 and g < 20 and b < 20 and a < 40:
                px[x, y] = (0, 0, 0, 0)
    return img


def fitted_logo(canvas_size, logo, scale=0.72):
    """Place logo centered on transparent canvas, scaled to scale*canvas."""
    out = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bbox = logo.getbbox()
    cropped = logo.crop(bbox)
    target = int(canvas_size * scale)
    # keep aspect
    cw, ch = cropped.size
    if cw >= ch:
        nw, nh = target, max(1, int(target * ch / cw))
    else:
        nh, nw = target, max(1, int(target * cw / ch))
    resized = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    x = (canvas_size - nw) // 2
    y = (canvas_size - nh) // 2
    out.paste(resized, (x, y), resized)
    return out


def composite_on_bg(logo_layer, size, bg=BG, round_mask=False):
    base = Image.new("RGBA", (size, size), bg)
    base.alpha_composite(logo_layer)
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        return out
    return base


def save(img, *parts):
    path = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print("wrote", path, img.size)


def main():
    # Keep a local copy of source for reproducibility
    src_img = load_logo()
    os.makedirs(os.path.dirname(ALT_SRC), exist_ok=True)
    if os.path.exists(SRC):
        shutil.copy2(SRC, ALT_SRC)
    print("source", src_img.size, "bbox", src_img.getbbox())

    # --- splash / loading transparent logos ---
    for dens, factor in DENSITIES.items():
        size = int(48 * factor)  # 48..192
        # 新电视图标含天线/文字，启动页略放大
        layer = fitted_logo(size, src_img, scale=0.88)
        save(layer, f"drawable-{dens}", "ic_splash_logo.png")

    # --- launcher mipmaps ---
    for dens, factor in DENSITIES.items():
        size = int(48 * factor)
        fg = fitted_logo(size, src_img, scale=0.82)
        save(fg, f"mipmap-{dens}", "ic_launcher_foreground.png")

        # 背景保持柠檬黄主题色
        if dens == "xxxhdpi":
            bg = Image.new("RGBA", (520, 520), BG)
            save(bg, "mipmap-xxxhdpi", "ic_launcher_background.png")
        else:
            bg = Image.new("RGBA", (size, size), BG)
            save(bg, f"mipmap-{dens}", "ic_launcher_background.png")

        full = composite_on_bg(fg, size, BG, round_mask=False)
        save(full, f"mipmap-{dens}", "ic_launcher.png")
        round_icon = composite_on_bg(fg, size, BG, round_mask=True)
        save(round_icon, f"mipmap-{dens}", "ic_launcher_round.png")

    # --- TV wide banner 320x180 ---
    banner = Image.new("RGBA", (320, 180), BG)
    bbox = src_img.getbbox()
    cropped = src_img.crop(bbox)
    target_h = int(180 * 0.72)
    cw, ch = cropped.size
    nw = max(1, int(target_h * cw / ch))
    nh = target_h
    logo = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    x = (320 - nw) // 2
    y = (180 - nh) // 2
    banner.paste(logo, (x, y), logo)
    save(banner, "drawable-xhdpi", "tv_banner.png")

    # quick previews
    for name, color, size in [
        ("preview_splash", BG, 384),
        ("preview_loading", BG, 384),
    ]:
        canvas = Image.new("RGBA", (size, size), color)
        layer = fitted_logo(192, src_img, scale=0.78)
        canvas.paste(layer, ((size - 192) // 2, (size - 192) // 2), layer)
        canvas.convert("RGB").save(os.path.join(ROOT, f"{name}.png"))
        print("preview", name)


if __name__ == "__main__":
    main()
