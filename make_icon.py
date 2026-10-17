import struct, zlib, sys

def png_chunk(chunk_type, data):
    chunk = chunk_type + data
    return struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk))

def create_png(width, height, pixels_rgba):
    # PNG header
    header = b'\x89PNG\r\n\x1a\n'
    # IHDR
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr = png_chunk(b'IHDR', ihdr_data)
    # IDAT - raw RGBA data with filter bytes
    raw_data = b''
    for y in range(height):
        raw_data += b'\x00'  # filter: none
        for x in range(width):
            idx = (y * width + x) * 4
            raw_data += pixels_rgba[idx:idx+4]
    idat = png_chunk(b'IDAT', zlib.compress(raw_data))
    # IEND
    iend = png_chunk(b'IEND', b'')
    return header + ihdr + idat + iend

# Design: 108x108 adaptive icon
# Colors
BG_TOP = (126, 150, 255, 255)    # #7E96FF
BG_BOT = (79, 107, 255, 255)     # #4F6BFF
WHITE = (255, 255, 255, 255)
WHITE_FADE = (255, 255, 255, 184)
ACCENT = (79, 107, 255, 255)     # #4F6BFF
ACCENT_FADE = (79, 107, 255, 115)

W, H = 108, 108

# Helper: lerp colors
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def in_circle(cx, cy, r, x, y):
    return (x - cx)**2 + (y - cy)**2 <= r*r

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

def dist_to_line(x1, y1, x2, y2, px, py):
    dx, dy = x2 - x1, y2 - y1
    len_sq = dx*dx + dy*dy
    if len_sq == 0:
        return ((px-x1)**2 + (py-y1)**2)**0.5
    t = max(0, min(1, ((px-x1)*dx + (py-y1)*dy) / len_sq))
    proj_x, proj_y = x1 + t*dx, y1 + t*dy
    return ((px-proj_x)**2 + (py-proj_y)**2)**0.5

def in_line(x1, y1, x2, y2, thickness, px, py):
    return dist_to_line(x1, y1, x2, y2, px, py) <= thickness / 2

def in_rect(x, y, w, h, px, py):
    return x <= px <= x+w and y <= py <= y+h

# Page shape (rounded rect with folded corner)
def in_page(px, py):
    # Main body: x=34 to 74, y=29 to 79, rounded corners r=7
    if not in_rounded_rect(34, 29, 40, 50, 7, px, py):
        return False
    # Cut out the folded corner triangle (top-right)
    if px > 62 and py < 41:
        if px - 62 > 41 - py:
            return False
    return True

def in_fold(px, py):
    # The folded corner triangle
    return px >= 62 and py <= 41 and (px - 62) + (41 - py) <= 12

# Build pixel data
pixels = bytearray(W * H * 4)

for y in range(H):
    for x in range(W):
        idx = (y * W + x) * 4
        px, py = x + 0.5, y + 0.5

        # Background gradient
        t = (px + py) / (W + H)
        bg_color = lerp(BG_TOP, BG_BOT, t)

        # Default: background
        color = bg_color

        # Page (white)
        if in_page(px, py):
            color = WHITE
        # Fold (semi-transparent white)
        elif in_fold(px, py):
            color = WHITE_FADE

        # Clock ticks (12, 3, 6, 9 positions)
        cx, cy = 54, 56
        tick_len = 3
        # 12 o'clock tick
        if in_line(54, 39.5, 54, 42.5, 3, px, py): color = ACCENT_FADE
        # 3 o'clock tick
        if in_line(67.5, 56, 70.5, 56, 3, px, py): color = ACCENT_FADE
        # 6 o'clock tick
        if in_line(54, 69.5, 54, 72.5, 3, px, py): color = ACCENT_FADE
        # 9 o'clock tick
        if in_line(37.5, 56, 40.5, 56, 3, px, py): color = ACCENT_FADE

        # Clock hands at 10:10 position (classic watch display)
        # Hour hand (shorter, pointing to 10)
        if in_line(54, 56, 46.8, 51.9, 5, px, py): color = ACCENT
        # Minute hand (longer, pointing to 2)
        if in_line(54, 56, 63.5, 50.5, 5, px, py): color = ACCENT
        # Center dot
        if in_circle(54, 56, 3.2, px, py): color = ACCENT

        pixels[idx:idx+4] = bytes(color)

# Write PNG
png_data = create_png(W, H, pixels)
with open('yishi_icon_108.png', 'wb') as f:
    f.write(png_data)
print(f'Created yishi_icon_108.png ({len(png_data)} bytes)')

# Also create a simple launcher icon at different densities
# For now, just copy the 108px version for all densities
# (In a real project you'd want proper density variants)
for dpi, name in [(108, 'mdpi'), (162, 'hdpi'), (216, 'xhdpi'), (324, 'xxhdpi'), (432, 'xxxhdpi')]:
    # Scale the 108px design
    scale = dpi / 108
    scaled_w, scaled_h = int(W * scale), int(H * scale)
    scaled_pixels = bytearray(scaled_w * scaled_h * 4)

    for y in range(scaled_h):
        for x in range(scaled_w):
            src_x = int(x / scale)
            src_y = int(y / scale)
            src_idx = (src_y * W + src_x) * 4
            dst_idx = (y * scaled_w + x) * 4
            scaled_pixels[dst_idx:dst_idx+4] = pixels[src_idx:src_idx+4]

    scaled_png = create_png(scaled_w, scaled_h, scaled_pixels)
    filename = f'yishi_icon_{name}.png'
    with open(filename, 'wb') as f:
        f.write(scaled_png)
    print(f'Created {filename} ({len(scaled_png)} bytes, {scaled_w}x{scaled_h})')