import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps
import math, os

OUT = "/home/claude/logo_gen/out"
os.makedirs(OUT, exist_ok=True)

# ---------- Brand colors (coherentes con el tema de la app) ----------
BG_TOP    = (18, 20, 28)      # #12141C - navy oscuro
BG_BOTTOM = (10, 11, 16)      # #0A0B10 - casi negro
ACCENT_TOP    = (255, 163, 79)   # #FFA34F - ámbar
ACCENT_BOTTOM = (230, 73, 59)    # #E6493B - rojo-naranja
WHITE = (255, 255, 255)

SS = 8  # supersampling para bordes suaves

def diagonal_gradient(size, c1, c2):
    """Gradiente diagonal (esquina sup-izq -> inf-der) tipo 135 grados."""
    h, w = size, size
    y, x = np.mgrid[0:h, 0:w]
    t = (x.astype(np.float32) + y.astype(np.float32)) / (w + h - 2)
    t = np.clip(t, 0, 1)
    c1 = np.array(c1, dtype=np.float32)
    c2 = np.array(c2, dtype=np.float32)
    grad = (c1[None, None, :] * (1 - t[..., None]) + c2[None, None, :] * t[..., None]).astype(np.uint8)
    return Image.fromarray(grad, mode="RGB")

def rounded_mask(size, radius_ratio):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    r = int(size * radius_ratio)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    return mask

def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask

def play_triangle_layer(size, scale=0.42, shift_x=0.045):
    """
    Triángulo de 'play' centrado, con relleno en degradado ámbar->rojo
    y un halo suave para que resalte incluso en tamaños pequeños.
    """
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cx, cy = size / 2, size / 2
    r = size * scale
    # Triángulo apuntando a la derecha (offset óptico como en botones de play reales)
    offset = size * shift_x
    p1 = (cx - r * 0.62 + offset, cy - r)
    p2 = (cx - r * 0.62 + offset, cy + r)
    p3 = (cx + r * 0.95 + offset, cy)

    tri_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(tri_mask).polygon([p1, p2, p3], fill=255)

    grad = diagonal_gradient(size, ACCENT_TOP, ACCENT_BOTTOM).convert("RGBA")
    grad.putalpha(tri_mask)
    layer = Image.alpha_composite(layer, grad)
    return layer

def signal_arcs_layer(size):
    """Dos arcos sutiles arriba a la derecha, evocando 'señal / streaming en vivo'."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = size * 0.80, size * 0.28
    for i, (radius_ratio, width_ratio, alpha) in enumerate([
        (0.20, 0.028, 235), (0.30, 0.026, 150)
    ]):
        r = size * radius_ratio
        w = max(2, int(size * width_ratio))
        bbox = [cx - r, cy - r, cx + r, cy + r]
        d.arc(bbox, start=-55, end=35, fill=(255, 255, 255, alpha), width=w)
    return layer

def build_full_icon(size_px):
    """Ícono clásico auto-contenido (fondo + triángulo + acentos), esquinas redondeadas."""
    big = size_px * SS
    bg = diagonal_gradient(big, BG_TOP, BG_BOTTOM).convert("RGBA")
    tri = play_triangle_layer(big, scale=0.40, shift_x=0.04)
    arcs = signal_arcs_layer(big)

    art = Image.alpha_composite(bg, arcs)
    art = Image.alpha_composite(art, tri)

    # Aplicar la máscara redondeada al ARTE COMPLETO (no solo al fondo),
    # para que ningún acento decorativo se salga de la silueta del ícono.
    mask = rounded_mask(big, 0.225)
    art.putalpha(mask)

    art = art.resize((size_px, size_px), Image.LANCZOS)
    return art

def build_round_icon(size_px):
    big = size_px * SS
    bg = diagonal_gradient(big, BG_TOP, BG_BOTTOM).convert("RGBA")
    tri = play_triangle_layer(big, scale=0.38, shift_x=0.04)
    arcs = signal_arcs_layer(big)
    art = Image.alpha_composite(bg, arcs)
    art = Image.alpha_composite(art, tri)

    mask = circle_mask(big)
    art.putalpha(mask)

    art = art.resize((size_px, size_px), Image.LANCZOS)
    return art

def build_adaptive_background(size_px):
    """Full-bleed, SIN esquinas redondeadas (el sistema aplica su propia máscara)."""
    big = size_px * SS
    bg = diagonal_gradient(big, BG_TOP, BG_BOTTOM).convert("RGBA")
    bg = bg.resize((size_px, size_px), Image.LANCZOS)
    return bg

def build_adaptive_foreground(size_px):
    """
    Transparente, SOLO el triángulo (sin acentos decorativos), calculado para
    quedar siempre dentro del círculo seguro central (66% del canvas 108dp),
    ya que cada launcher recorta el foreground con una máscara distinta
    (círculo, squircle, rectángulo redondeado, gota) y el margen varía.
    """
    big = size_px * SS
    layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    tri = play_triangle_layer(big, scale=0.30, shift_x=0.02)
    layer = Image.alpha_composite(layer, tri)
    layer = layer.resize((size_px, size_px), Image.LANCZOS)
    return layer

def build_playstore_icon(size_px=512):
    """512x512 sin transparencia, para la ficha de Play Store."""
    big = size_px * SS
    bg = diagonal_gradient(big, BG_TOP, BG_BOTTOM).convert("RGBA")
    tri = play_triangle_layer(big, scale=0.40, shift_x=0.04)
    arcs = signal_arcs_layer(big)
    art = Image.alpha_composite(bg, arcs)
    art = Image.alpha_composite(art, tri)
    art = art.convert("RGB").resize((size_px, size_px), Image.LANCZOS)
    return art

def build_tv_banner(w=320, h=180):
    """Banner horizontal para el launcher de Android TV / Google TV (fila de apps)."""
    ss = 4
    big_w, big_h = w * ss, h * ss
    bg = diagonal_gradient(max(big_w, big_h), BG_TOP, BG_BOTTOM).convert("RGBA")
    bg = bg.resize((big_w, big_h), Image.LANCZOS)

    # Marca (triángulo) a la izquierda
    mark_size = big_h
    mark = Image.new("RGBA", (mark_size, mark_size), (0, 0, 0, 0))
    tri = play_triangle_layer(mark_size, scale=0.34, shift_x=0.03)
    arcs = signal_arcs_layer(mark_size)
    mark = Image.alpha_composite(mark, arcs)
    mark = Image.alpha_composite(mark, tri)

    canvas = bg.copy()
    pad = int(big_h * 0.12)
    canvas.alpha_composite(mark, (pad, 0))

    # Texto "JetGo" a la derecha del ícono
    from PIL import ImageFont
    draw = ImageDraw.Draw(canvas)
    font = None
    for path in [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ]:
        if os.path.exists(path):
            font = ImageFont.truetype(path, int(big_h * 0.30))
            break
    if font is None:
        font = ImageFont.load_default()

    text = "JetGo"
    text_x = mark_size + pad * 1.6
    bbox = draw.textbbox((0, 0), text, font=font)
    text_h = bbox[3] - bbox[1]
    text_y = (big_h - text_h) / 2 - bbox[1]
    draw.text((text_x, text_y), text, font=font, fill=(255, 255, 255, 255))

    canvas = canvas.resize((w, h), Image.LANCZOS)
    return canvas.convert("RGB")

# ---------------- Generar todos los tamaños ----------------

mipmap_sizes = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192
}
adaptive_layer_sizes = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432
}

for density, size in mipmap_sizes.items():
    d = f"{OUT}/mipmap-{density}"
    os.makedirs(d, exist_ok=True)
    build_full_icon(size).save(f"{d}/ic_launcher.png")
    build_round_icon(size).save(f"{d}/ic_launcher_round.png")

for density, size in adaptive_layer_sizes.items():
    d = f"{OUT}/mipmap-{density}"
    os.makedirs(d, exist_ok=True)
    build_adaptive_foreground(size).save(f"{d}/ic_launcher_foreground.png")
    build_adaptive_background(size).save(f"{d}/ic_launcher_background.png")

os.makedirs(f"{OUT}/playstore", exist_ok=True)
build_playstore_icon(512).save(f"{OUT}/playstore/ic_launcher_playstore.png")

os.makedirs(f"{OUT}/drawable-xhdpi", exist_ok=True)
build_tv_banner(320, 180).save(f"{OUT}/drawable-xhdpi/tv_banner.png")
# Versión de mayor resolución también, por si se usa en pantallas 4K de TV
os.makedirs(f"{OUT}/drawable-xxhdpi", exist_ok=True)
build_tv_banner(480, 270).save(f"{OUT}/drawable-xxhdpi/tv_banner.png")

# Vista previa grande para que el usuario vea el diseño
build_full_icon(1024).save(f"{OUT}/preview_icon_1024.png")
build_tv_banner(960, 540).save(f"{OUT}/preview_banner.png")

print("Generación completa.")
for root, dirs, files in os.walk(OUT):
    for f in files:
        print(os.path.join(root, f))
