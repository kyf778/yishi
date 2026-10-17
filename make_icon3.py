import struct, zlib, math

def png_chunk(ctype, data):
    chunk = ctype + data
    return struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk))

def create_png(w, h, pixels):
    hdr = b'\x89PNG\r\n\x1a\n'
    ihdr = png_chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    raw = b''
    for y in range(h):
        raw += b'\x00'
        raw += pixels[y*w*4:(y+1)*w*4]
    idat = png_chunk(b'IDAT', zlib.compress(raw))
    iend = png_chunk(b'IEND', b'')
    return hdr + ihdr + idat + iend

def render(size):
    """Render icon with supersampling AA."""
    SS = 8  # 8x supersample
    W = size * SS
    H = size * SS

    # Colors
    BG_TOP = (126, 150, 255, 255)
    BG_BOT = (79, 107, 255, 255)
    WHITE = (255, 255, 255, 255)
    FOLD = (230, 234, 255, 255)
    ACCENT = (79, 107, 255, 255)
    ACCENT_DIM = (79, 107, 255, 100)
    SHADOW = (0, 0, 0, 40)

    pixels = bytearray(W * H * 4)

    # Scale: 108 design units -> W pixels
    s = W / 108.0

    def S(v): return v * s

    def lerp(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

    def circle(cx, cy, r, px, py):
        return (px - S(cx))**2 + (py - S(cy))**2 <= S(r)**2

    def dist_line(x1, y1, x2, y2, px, py):
        x1, y1, x2, y2 = S(x1), S(y1), S(x2), S(y2)
        dx, dy = x2-x1, y2-y1
        lsq = dx*dx + dy*dy
        if lsq == 0: return ((px-x1)**2 + (py-y1)**2)**0.5
        t = max(0, min(1, ((px-x1)*dx + (py-y1)*dy) / lsq))
        return ((px - (x1+t*dx))**2 + (py - (y1+t*dy))**2)**0.5

    def line(x1, y1, x2, y2, thick, px, py):
        return dist_line(x1, y1, x2, y2, px, py) <= S(thick/2)

    def rounded_rect(x, y, w, h, r, px, py):
        x, y, w, h, r = S(x), S(y), S(w), S(h), S(r)
        if px < x or px > x+w or py < y or py > y+h: return False
        if px < x+r and py < y+r: return circle(x+r, y+r, r, px, py)
        if px > x+w-r and py < y+r: return circle(x+w-r, y+r, r, px, py)
        if px < x+r and py > y+h-r: return circle(x+r, y+h-r, r, px, py)
        if px > x+w-r and py > y+h-r: return circle(x+w-r, y+h-r, r, px, py)
        return True

    def page(px, py):
        # Main page: 34,29 to 74,79, rounded 7
        if not rounded_rect(34, 29, 40, 50, 7, px, py): return False
        # Cut folded corner
        if px > S(62) and py < S(41):
            if (px - S(62)) + (S(41) - py) > S(12): return False
        return True

    def fold(px, py):
        return px >= S(62) and py <= S(41) and (px - S(62)) + (S(41) - py) <= S(12)

    for y in range(H):
        for x in range(W):
            px, py = x + 0.5, y + 0.5
            idx = (y * W + x) * 4

            # Background gradient
            t = (px + py) / (W + H)
            color = lerp(BG_TOP, BG_BOT, t)

            # Page shadow (offset 1px down)
            if rounded_rect(34, 30, 40, 50, 7, px, py):
                if not (px > S(62) and py < S(42) and (px - S(62)) + (S(42) - py) > S(12)):
                    color = SHADOW

            # Page (white)
            if page(px, py):
                color = WHITE
            # Fold
            elif fold(px, py):
                color = FOLD

            # Clock ticks (subtle)
            if line(54, 43, 54, 45.4, 1.2, px, py): color = ACCENT_DIM
            if line(63.6, 55, 66, 55, 1.2, px, py): color = ACCENT_DIM
            if line(54, 64.6, 54, 67, 1.2, px, py): color = ACCENT_DIM
            if line(42, 55, 44.4, 55, 1.2, px, py): color = ACCENT_DIM

            # Clock hands at 10:10 (thicker, prominent)
            if line(54, 55, 61.3, 50.8, 2.5, px, py): color = ACCENT
            if line(54, 55, 49.3, 51.7, 2.5, px, py): color = ACCENT
            # Center dot
            if circle(54, 55, 1.8, px, py): color = ACCENT

            pixels[idx:idx+4] = bytes(color)

    # Downsample with box filter
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            r, g, b, a = 0, 0, 0, 0
            for sy in range(SS):
                for sx in range(SS):
                    i = ((y*SS + sy) * W + (x*SS + sx)) * 4
                    r += pixels[i]; g += pixels[i+1]; b += pixels[i+2]; a += pixels[i+3]
            n = SS * SS
            o = (y * size + x) * 4
            out[o] = r // n; out[o+1] = g // n; out[o+2] = b // n; out[o+3] = a // n

    return out

# Generate
for size, name in [(48,'mdpi'), (72,'hdpi'), (96,'xhdpi'), (144,'xxhdpi'), (192,'xxxhdpi'), (512,'preview')]:
    p = render(size)
    png = create_png(size, size, p)
    fn = f'yishi_icon_{name}.png'
    with open(fn, 'wb') as f: f.write(png)
    print(f'{fn}: {size}x{size} ({len(png)}B)')