import struct, zlib, math

def png_chunk(chunk_type, data):
    chunk = chunk_type + data
    return struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk))

def create_png(width, height, pixels_rgba):
    header = b'\x89PNG\r\n\x1a\n'
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr = png_chunk(b'IHDR', ihdr_data)
    raw_data = b''
    for y in range(height):
        raw_data += b'\x00'
        for x in range(width):
            idx = (y * width + x) * 4
            raw_data += pixels_rgba[idx:idx+4]
    idat = png_chunk(b'IDAT', zlib.compress(raw_data))
    iend = png_chunk(b'IEND', b'')
    return header + ihdr + idat + iend

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def dist_point_line(x1, y1, x2, y2, px, py):
    dx, dy = x2 - x1, y2 - y1
    len_sq = dx*dx + dy*dy
    if len_sq == 0:
        return ((px-x1)**2 + (py-y1)**2)**0.5
    t = max(0, min(1, ((px-x1)*dx + (py-y1)*dy) / len_sq))
    proj_x, proj_y = x1 + t*dx, y1 + t*dy
    return ((px-proj_x)**2 + (py-proj_y)**2)**0.5

def in_circle(cx, cy, r, px, py):
    return (px - cx)**2 + (py - cy)**2 <= r*r

def in_rounded_rect(x, y, w, h, r, px, py):
    if px < x or px > x + w or py < y or py > y + h:
        return False
    if px < x + r and py < y + r:
        return in_circle(x + r, y + r, r, px, py)
    if px > x + w - r and py < y + r:
        return in_circle(x + w - r, y + r, r, px, py)
    if px < x + r and py > y + h - r:
        return in_circle(x + r, y + h - r, r, px, py)
    if px > x + w - r and py > y + h - r:
        return in_circle(x + w - r, y + h - r, r, px, py)
    return True

def in_page(px, py):
    # Page body: x=34 to 74, y=29 to 79, rounded corners r=7
    if not in_rounded_rect(34, 29, 40, 50, 7, px, py):
        return False
    # Cut out folded corner (top-right triangle)
    if px > 62 and py < 41:
        if (px - 62) + (41 - py) > 12:
            return False
    return True

def in_fold(px, py):
    return px >= 62 and py <= 41 and (px - 62) + (41 - py) <= 12

def render_icon(size):
    """Render icon at given size with anti-aliasing via supersampling."""
    scale = 4  # 4x supersampling
    ss = size * scale

    # Colors
    BG_TOP = (126, 150, 255, 255)
    BG_BOT = (79, 107, 255, 255)
    WHITE = (255, 255, 255, 255)
    WHITE_FADE = (255, 255, 255, 180)
    ACCENT = (79, 107, 255, 255)
    ACCENT_FADE = (79, 107, 255, 100)
    SHADOW = (58, 75, 208, 60)

    pixels = bytearray(size * size * 4)

    # Scale factor from 108 to target size
    s = 108.0 / ss

    for y in range(size):
        for x in range(size):
            # Supersample
            r_sum, g_sum, b_sum, a_sum = 0, 0, 0, 0
            for sy in range(scale):
                for sx in range(scale):
                    px = (x + (sx + 0.5) / scale) * s
                    py = (y + (sy + 0.5) / scale) * s

                    # Background gradient
                    t = (px + py) / 216
                    color = lerp(BG_TOP, BG_BOT, t)

                    # Page shadow (offset down 1.5px)
                    if in_rounded_rect(34, 30.5, 40, 50, 7, px, py):
                        if not (px > 62 and py < 42.5 and (px - 62) + (42.5 - py) > 12):
                            color = SHADOW

                    # Page (white)
                    if in_page(px, py):
                        color = WHITE
                    # Fold
                    elif in_fold(px, py):
                        color = WHITE_FADE

                    # Clock ticks (12/3/6/9)
                    if dist_point_line(54, 43, 54, 45.4, px, py) <= 0.75: color = ACCENT_FADE
                    if dist_point_line(63.6, 55, 66, 55, px, py) <= 0.75: color = ACCENT_FADE
                    if dist_point_line(54, 64.6, 54, 67, px, py) <= 0.75: color = ACCENT_FADE
                    if dist_point_line(42, 55, 44.4, 55, px, py) <= 0.75: color = ACCENT_FADE

                    # Clock hands at 10:10
                    if dist_point_line(54, 55, 61.3, 50.8, px, py) <= 1.1: color = ACCENT
                    if dist_point_line(54, 55, 49.3, 51.7, px, py) <= 1.1: color = ACCENT

                    # Center dot
                    if in_circle(54, 55, 1.6, px, py): color = ACCENT

                    r_sum += color[0]; g_sum += color[1]; b_sum += color[2]; a_sum += color[3]

            n = scale * scale
            idx = (y * size + x) * 4
            pixels[idx] = r_sum // n
            pixels[idx+1] = g_sum // n
            pixels[idx+2] = b_sum // n
            pixels[idx+3] = a_sum // n

    return pixels

# Generate all Android density variants
densities = [
    (48, 'mdpi', 1),
    (72, 'hdpi', 1.5),
    (96, 'xhdpi', 2),
    (144, 'xxhdpi', 3),
    (192, 'xxxhdpi', 4),
]

for size, name, scale_factor in densities:
    pixels = render_icon(size)
    png = create_png(size, size, pixels)
    filename = f'yishi_icon_{name}.png'
    with open(filename, 'wb') as f:
        f.write(png)
    print(f'Created {filename} ({size}x{size}, {len(png)} bytes)')

# Also create a high-res preview
preview_size = 512
preview_pixels = render_icon(preview_size)
png = create_png(preview_size, preview_size, preview_pixels)
with open('yishi_icon_preview.png', 'wb') as f:
    f.write(png)
print(f'Created yishi_icon_preview.png ({preview_size}x{preview_size}, {len(png)} bytes)')