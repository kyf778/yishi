"""
忆时 App - 10 个图标方案渲染脚本
每个图标 512x512，带预览图
"""
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

# Brand colors
PRIMARY = (79, 107, 255, 255)
PRIMARY_DARK = (45, 65, 180, 255)
NIGHT_TOP = (27, 42, 74, 255)
NIGHT_BOT = (62, 92, 150, 255)
AMBER = (255, 170, 31, 255)
WHITE = (255, 255, 255, 255)
SOFT_WHITE = (230, 234, 255, 255)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

def circle_mask(cx, cy, r, px, py):
    return (px - cx)**2 + (py - cy)**2 <= r**2

def dist_line(x1, y1, x2, y2, px, py):
    dx, dy = x2-x1, y2-y1
    lsq = dx*dx + dy*dy
    if lsq == 0: return ((px-x1)**2 + (py-y1)**2)**0.5
    t = max(0, min(1, ((px-x1)*dx + (py-y1)*dy) / lsq))
    return ((px - (x1+t*dx))**2 + (py - (y1+t*dy))**2)**0.5

def line_mask(x1, y1, x2, y2, thick, px, py):
    return dist_line(x1, y1, x2, y2, px, py) <= thick/2

def rounded_rect_mask(x, y, w, h, r, px, py):
    if px < x or px > x+w or py < y or py > y+h: return False
    if px < x+r and py < y+r: return circle_mask(x+r, y+r, r, px, py)
    if px > x+w-r and py < y+r: return circle_mask(x+w-r, y+r, r, px, py)
    if px < x+r and py > y+h-r: return circle_mask(x+r, y+h-r, r, px, py)
    if px > x+w-r and py > y+h-r: return circle_mask(x+w-r, y+h-r, r, px, py)
    return True

def quad_bezier(p0, p1, p2, t):
    u = 1 - t
    return (u*u*p0[0] + 2*u*t*p1[0] + t*t*p2[0],
            u*u*p0[1] + 2*u*t*p1[1] + t*t*p2[1])

def bezier_curve_mask(p0, p1, p2, p3, thick, px, py, steps=60):
    """Approximate bezier curve as line segments"""
    prev = p0
    for i in range(1, steps+1):
        t = i / steps
        u = 1 - t
        pt = (u**3*p0[0] + 3*u**2*t*p1[0] + 3*u*t**2*p2[0] + t**3*p3[0],
              u**3*p0[1] + 3*u**2*t*p1[1] + 3*u*t**2*p2[1] + t**3*p3[1])
        if dist_line(prev[0], prev[1], pt[0], pt[1], px, py) <= thick/2:
            return True
        prev = pt
    return False

def render_icon(icon_id, size=512):
    SS = 4
    W = size * SS
    H = size * SS
    s = W / 108.0
    def S(v): return v * s
    
    pixels = bytearray(W * H * 4)
    
    # Helper: background gradient (diagonal)
    def bg_diagonal(top, bot):
        for y in range(H):
            for x in range(W):
                t = (x + y) / (W + H)
                idx = (y * W + x) * 4
                c = lerp(top, bot, t)
                pixels[idx:idx+4] = bytes(c)
    
    # Helper: background solid
    def bg_solid(color):
        for y in range(H):
            for x in range(W):
                idx = (y * W + x) * 4
                pixels[idx:idx+4] = bytes(color)
    
    def set_px(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            pixels[idx:idx+4] = bytes(color)
    
    # Rounded square background (Android icon shape)
    def draw_rounded_bg(color_top, color_bot, radius=22):
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # rounded square corners at 0,0 and W,H
                cx = max(radius*s, min(W - radius*s, px))
                cy = max(radius*s, min(H - radius*s, py))
                if (px - cx)**2 + (py - cy)**2 <= (radius*s)**2:
                    t = (px + py) / (W + H)
                    c = lerp(color_top, color_bot, t)
                    set_px(x, y, c)
                else:
                    # transparent outside rounded square
                    set_px(x, y, (0,0,0,0))
    
    if icon_id == 1:
        # 时字标: 时 character, 日 = rounded screen, 寸 = clock hands
        draw_rounded_bg(NIGHT_TOP, NIGHT_BOT)
        # 日 (left part) - rounded screen
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # 日 box: x 28-52, y 36-72
                if rounded_rect_mask(S(28), S(36), S(24), S(36), S(5), px, py):
                    # inner hole
                    if not rounded_rect_mask(S(33), S(41), S(14), S(26), S(3), px, py):
                        set_px(x, y, WHITE)
        # 寸 (right part) - horizontal line + dot + vertical hook
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # top horizontal stroke of 寸
                if line_mask(S(56), S(40), S(80), S(40), S(4), px, py):
                    set_px(x, y, WHITE)
                # vertical stroke with hook
                if line_mask(S(68), S(40), S(68), S(72), S(4), px, py):
                    set_px(x, y, WHITE)
                # dot (upturned) - represents the marked moment
                if circle_mask(S(58), S(52), S(3.5), px, py):
                    set_px(x, y, AMBER)
                # small tick
                if line_mask(S(60), S(58), S(76), S(64), S(3), px, py):
                    set_px(x, y, WHITE)
    
    elif icon_id == 2:
        # 十点十分月牙: clock hands at 10:10 forming crescent + star
        draw_rounded_bg(NIGHT_TOP, NIGHT_BOT)
        # Crescent moon (negative space circle)
        cx, cy, r = S(54), S(54), S(30)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                d = (px - cx)**2 + (py - cy)**2
                if d <= r**2:
                    # inner circle offset to make crescent
                    if (px - (cx + S(10)))**2 + (py - (cy - S(4)))**2 > (r - S(6))**2:
                        set_px(x, y, WHITE)
        # Clock hands at 10:10
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # hour hand to ~10
                if line_mask(S(54), S(54), S(44), S(42), S(4), px, py):
                    set_px(x, y, PRIMARY)
                # minute hand to ~2 (10 min)
                if line_mask(S(54), S(54), S(64), S(42), S(4), px, py):
                    set_px(x, y, PRIMARY)
                # center dot
                if circle_mask(S(54), S(54), S(3), px, py):
                    set_px(x, y, AMBER)
        # small star
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(78), S(30), S(3), px, py):
                    set_px(x, y, AMBER)
    
    elif icon_id == 3:
        # 折角便签: folded corner page + clock
        draw_rounded_bg(PRIMARY, PRIMARY_DARK)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # page
                if rounded_rect_mask(S(30), S(28), S(48), S(54), S(6), px, py):
                    if not (px > S(62) and py < S(40) and (px - S(62)) + (S(40) - py) > S(14)):
                        set_px(x, y, WHITE)
                # fold triangle
                if px >= S(62) and py <= S(40) and (px - S(62)) + (S(40) - py) <= S(14):
                    set_px(x, y, SOFT_WHITE)
                # fold shadow line
                if line_mask(S(62), S(40), S(76), S(40), S(1.5), px, py):
                    set_px(x, y, (200, 205, 230, 255))
        # clock on page
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(54), S(56), S(12), px, py):
                    if not circle_mask(S(54), S(56), S(10), px, py):
                        set_px(x, y, PRIMARY)
                # hands
                if line_mask(S(54), S(56), S(54), S(49), S(2), px, py):
                    set_px(x, y, PRIMARY)
                if line_mask(S(54), S(56), S(59), S(58), S(2), px, py):
                    set_px(x, y, PRIMARY)
                if circle_mask(S(54), S(56), S(1.5), px, py):
                    set_px(x, y, AMBER)
    
    elif icon_id == 4:
        # 识别星芒 v2: refined 4-point star + AI sparkle dots
        draw_rounded_bg(WHITE, (245, 247, 255, 255))
        # Main 4-point star (diamond star)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # star shape using bezier-like curves
                cx, cy = S(54), S(54)
                # 4-point star: points at N, E, S, W
                angle = math.atan2(py - cy, px - cx)
                dist = ((px - cx)**2 + (py - cy)**2)**0.5
                # star radius varies with angle
                star_r = S(26) * (0.5 + 0.5 * abs(math.cos(2 * angle))**0.5)
                # make it a proper 4-point star
                inner = S(10)
                outer = S(26)
                # use 8 points alternating
                a = (angle + math.pi/2) % (2*math.pi)
                seg = int(a / (math.pi/4))
                seg_angle = a % (math.pi/4)
                if seg % 2 == 0:
                    r_target = outer
                else:
                    r_target = inner
                # interpolate
                t = seg_angle / (math.pi/4)
                r_interp = r_target
                if dist <= r_interp:
                    set_px(x, y, PRIMARY)
        # center highlight
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(54), S(54), S(6), px, py):
                    set_px(x, y, (120, 140, 255, 255))
        # sparkle dots
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(76), S(34), S(3), px, py):
                    set_px(x, y, AMBER)
                if circle_mask(S(32), S(74), S(2.2), px, py):
                    set_px(x, y, AMBER)
    
    elif icon_id == 5:
        # 倒计时环: circular progress arc + star center
        draw_rounded_bg(PRIMARY, PRIMARY_DARK)
        # progress ring (3/4 circle)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                cx, cy = S(54), S(54)
                dist = ((px - cx)**2 + (py - cy)**2)**0.5
                if S(20) <= dist <= S(28):
                    angle = math.atan2(py - cy, px - cx)
                    # ring goes from -90deg to 180deg (270 degrees = 3/4)
                    norm_angle = (angle + math.pi/2) % (2*math.pi)
                    if norm_angle <= math.radians(270):
                        set_px(x, y, WHITE)
                    else:
                        set_px(x, y, (255, 255, 255, 60))
        # center star
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                cx, cy = S(54), S(54)
                angle = math.atan2(py - cy, px - cx)
                dist = ((px - cx)**2 + (py - cy)**2)**0.5
                a = (angle + math.pi/2) % (2*math.pi)
                seg = int(a / (math.pi/5))
                if seg % 2 == 0:
                    r_target = S(10)
                else:
                    r_target = S(4)
                if dist <= r_target:
                    set_px(x, y, AMBER)
    
    elif icon_id == 6:
        # 星夜时钟: night sky + clock face with star markers
        draw_rounded_bg(NIGHT_TOP, NIGHT_BOT)
        # clock face
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(54), S(54), S(32), px, py):
                    if not circle_mask(S(54), S(54), S(29), px, py):
                        set_px(x, y, WHITE)
        # star hour markers (12 positions, small stars)
        for i in range(12):
            angle = math.radians(i * 30 - 90)
            sx = S(54) + math.cos(angle) * S(30.5)
            sy = S(54) + math.sin(angle) * S(30.5)
            for y in range(H):
                for x in range(W):
                    px, py = x + 0.5, y + 0.5
                    if circle_mask(sx, sy, S(1.5), px, py):
                        set_px(x, y, AMBER)
        # clock hands at 10:10
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if line_mask(S(54), S(54), S(46), S(44), S(3), px, py):
                    set_px(x, y, PRIMARY)
                if line_mask(S(54), S(54), S(64), S(44), S(3), px, py):
                    set_px(x, y, PRIMARY)
                if circle_mask(S(54), S(54), S(3), px, py):
                    set_px(x, y, AMBER)
        # a few background stars
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(20), S(22), S(1.2), px, py):
                    set_px(x, y, (200, 210, 255, 255))
                if circle_mask(S(88), S(80), S(1.2), px, py):
                    set_px(x, y, (200, 210, 255, 255))
    
    elif icon_id == 7:
        # 时光沙漏: hourglass with star particles
        draw_rounded_bg(PRIMARY, PRIMARY_DARK)
        # hourglass outline
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # top triangle
                if line_mask(S(36), S(32), S(72), S(32), S(4), px, py):
                    set_px(x, y, WHITE)
                if line_mask(S(36), S(32), S(54), S(50), S(4), px, py):
                    set_px(x, y, WHITE)
                if line_mask(S(72), S(32), S(54), S(50), S(4), px, py):
                    set_px(x, y, WHITE)
                # bottom triangle
                if line_mask(S(36), S(76), S(72), S(76), S(4), px, py):
                    set_px(x, y, WHITE)
                if line_mask(S(36), S(76), S(54), S(58), S(4), px, py):
                    set_px(x, y, WHITE)
                if line_mask(S(72), S(76), S(54), S(58), S(4), px, py):
                    set_px(x, y, WHITE)
        # sand (stars) flowing
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # top sand
                if (py > S(36) and py < S(48)):
                    tx = (py - S(36)) / S(12)
                    left = S(54) - S(18) * (1-tx)
                    right = S(54) + S(18) * (1-tx)
                    if left < px < right:
                        set_px(x, y, AMBER)
                # falling stream
                if line_mask(S(54), S(50), S(54), S(58), S(2), px, py):
                    set_px(x, y, AMBER)
                # bottom sand
                if (py > S(62) and py < S(72)):
                    tx = (py - S(62)) / S(10)
                    left = S(54) - S(4) * tx
                    right = S(54) + S(4) * tx
                    if left < px < right:
                        set_px(x, y, AMBER)
    
    elif icon_id == 8:
        # AI 之眼: eye shape (recognition) + clock pupil
        draw_rounded_bg(NIGHT_TOP, NIGHT_BOT)
        # eye outline (almond shape)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # eye using two curves
                cx, cy = S(54), S(54)
                dx = (px - cx) / S(30)
                dy = (py - cy) / S(16)
                # almond: x^2 + (y * f(x))^2 <= 1 where f(x) narrows at edges
                eye_width = math.sqrt(max(0, 1 - dx*dx))
                if abs(dy) <= eye_width * 0.95:
                    if abs(dy) >= eye_width * 0.75:
                        set_px(x, y, WHITE)
        # iris (clock)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(54), S(54), S(14), px, py):
                    if not circle_mask(S(54), S(54), S(11), px, py):
                        set_px(x, y, PRIMARY)
                # pupil + clock hands
                if circle_mask(S(54), S(54), S(7), px, py):
                    set_px(x, y, WHITE)
                if line_mask(S(54), S(54), S(54), S(49), S(2), px, py):
                    set_px(x, y, PRIMARY)
                if line_mask(S(54), S(54), S(58), S(56), S(2), px, py):
                    set_px(x, y, PRIMARY)
                if circle_mask(S(54), S(54), S(1.5), px, py):
                    set_px(x, y, AMBER)
        # sparkle
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(80), S(36), S(2.5), px, py):
                    set_px(x, y, AMBER)
    
    elif icon_id == 9:
        # 便签钉: sticky note pinned by star
        draw_rounded_bg(PRIMARY, PRIMARY_DARK)
        # sticky note (slightly rotated look via parallelogram)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # note body
                if rounded_rect_mask(S(32), S(34), S(44), S(46), S(4), px, py):
                    set_px(x, y, WHITE)
                # lines on note
                if line_mask(S(40), S(48), S(68), S(48), S(1.5), px, py):
                    set_px(x, y, (180, 185, 210, 255))
                if line_mask(S(40), S(56), S(64), S(56), S(1.5), px, py):
                    set_px(x, y, (180, 185, 210, 255))
                if line_mask(S(40), S(64), S(60), S(64), S(1.5), px, py):
                    set_px(x, y, (180, 185, 210, 255))
        # pin (star) at top
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                cx, cy = S(54), S(30)
                angle = math.atan2(py - cy, px - cx)
                dist = ((px - cx)**2 + (py - cy)**2)**0.5
                a = (angle + math.pi/2) % (2*math.pi)
                seg = int(a / (math.pi/5))
                if seg % 2 == 0:
                    r_target = S(8)
                else:
                    r_target = S(3.5)
                if dist <= r_target:
                    set_px(x, y, AMBER)
    
    elif icon_id == 10:
        # 夜空指针: minimal clock hands on dark + one bright star
        draw_rounded_bg(NIGHT_TOP, NIGHT_BOT)
        # subtle clock ring
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(54), S(54), S(30), px, py):
                    if not circle_mask(S(54), S(54), S(28), px, py):
                        set_px(x, y, (255, 255, 255, 30))
        # clock hands at 10:10 (thick, white)
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                # hour hand
                if line_mask(S(54), S(54), S(44), S(44), S(5), px, py):
                    set_px(x, y, WHITE)
                # minute hand
                if line_mask(S(54), S(54), S(66), S(44), S(5), px, py):
                    set_px(x, y, WHITE)
                # center dot
                if circle_mask(S(54), S(54), S(4), px, py):
                    set_px(x, y, AMBER)
        # one bright star (the "remembered moment")
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                cx, cy = S(78), S(28)
                angle = math.atan2(py - cy, px - cx)
                dist = ((px - cx)**2 + (py - cy)**2)**0.5
                a = (angle + math.pi/2) % (2*math.pi)
                seg = int(a / (math.pi/5))
                if seg % 2 == 0:
                    r_target = S(5)
                else:
                    r_target = S(2)
                if dist <= r_target:
                    set_px(x, y, AMBER)
        # tiny bg stars
        for y in range(H):
            for x in range(W):
                px, py = x + 0.5, y + 0.5
                if circle_mask(S(24), S(78), S(1), px, py):
                    set_px(x, y, (180, 190, 230, 255))
                if circle_mask(S(84), S(72), S(1), px, py):
                    set_px(x, y, (180, 190, 230, 255))
    
    # Downsample
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

# Render all 10
for i in range(1, 11):
    p = render_icon(i, 512)
    png = create_png(512, 512, p)
    fn = f'yishi_icon_{i:02d}.png'
    with open(fn, 'wb') as f: f.write(png)
    print(f'{fn} done')

# Create preview sheet 5x2
SHEET_W, SHEET_H = 5 * 560, 2 * 620
sheet = bytearray(SHEET_W * SHEET_H * 4)
# white bg
for i in range(SHEET_W * SHEET_H):
    sheet[i*4:i*4+4] = bytes((255, 255, 255, 255))

for idx in range(10):
    row = idx // 5
    col = idx % 5
    ox = col * 560 + 24
    oy = row * 620 + 24
    # load icon
    p = render_icon(idx+1, 512)
    for y in range(512):
        for x in range(512):
            si = (y * 512 + x) * 4
            di = ((oy + y) * SHEET_W + (ox + x)) * 4
            a = p[si+3] / 255.0
            if a > 0:
                sheet[di] = int(p[si] * a + 255 * (1-a))
                sheet[di+1] = int(p[si+1] * a + 255 * (1-a))
                sheet[di+2] = int(p[si+2] * a + 255 * (1-a))
                sheet[di+3] = 255

png = create_png(SHEET_W, SHEET_H, sheet)
with open('yishi_icons_preview.png', 'wb') as f: f.write(png)
print('preview sheet done')
