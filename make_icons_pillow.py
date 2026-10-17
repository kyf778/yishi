"""
忆时 App - 10 个图标方案 (Pillow 高效版)
"""
from PIL import Image, ImageDraw
import math

SIZE = 512
R = SIZE / 108.0  # scale from 108-unit design space

def s(v): return v * R

# Brand colors
PRIMARY = (79, 107, 255, 255)
PRIMARY_DARK = (45, 65, 180, 255)
NIGHT_TOP = (27, 42, 74, 255)
NIGHT_BOT = (62, 92, 150, 255)
AMBER = (255, 170, 31, 255)
WHITE = (255, 255, 255, 255)
SOFT_WHITE = (230, 234, 255, 255)

def rounded_bg(draw, top, bot):
    """Draw diagonal gradient rounded square"""
    # Create gradient
    grad = Image.new('RGBA', (SIZE, SIZE), (0,0,0,0))
    gd = ImageDraw.Draw(grad)
    for y in range(SIZE):
        for x in range(SIZE):
            t = (x + y) / (2 * SIZE)
            c = (
                int(top[0] + (bot[0]-top[0])*t),
                int(top[1] + (bot[1]-top[1])*t),
                int(top[2] + (bot[2]-top[2])*t),
                255
            )
            gd.point((x,y), c)
    # mask rounded square
    mask = Image.new('L', (SIZE, SIZE), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, SIZE-1, SIZE-1], radius=s(22), fill=255)
    return Image.composite(grad, Image.new('RGBA',(SIZE,SIZE),(0,0,0,0)), mask)

def star_points(cx, cy, outer, inner, n=5, rot=-90):
    pts = []
    for i in range(n*2):
        r = outer if i % 2 == 0 else inner
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + math.cos(a)*r, cy + math.sin(a)*r))
    return pts

def four_point_star(cx, cy, outer, inner):
    pts = []
    for i in range(8):
        r = outer if i % 2 == 0 else inner
        a = math.radians(-90 + i * 45)
        pts.append((cx + math.cos(a)*r, cy + math.sin(a)*r))
    return pts

def make_icon(i):
    img = Image.new('RGBA', (SIZE, SIZE), (0,0,0,0))
    
    if i == 1:
        # 时字标
        img = rounded_bg(None, NIGHT_TOP, NIGHT_BOT)
        d = ImageDraw.Draw(img)
        # 日 (left) - rounded rectangle screen
        d.rounded_rectangle([s(28), s(36), s(52), s(72)], radius=s(5), fill=WHITE)
        d.rounded_rectangle([s(33), s(41), s(47), s(67)], radius=s(3), fill=NIGHT_BOT)
        # 寸 (right)
        d.line([s(56), s(40), s(80), s(40)], fill=WHITE, width=int(s(4)))
        d.line([s(68), s(40), s(68), s(72)], fill=WHITE, width=int(s(4)))
        # upturned dot (the marked moment)
        d.ellipse([s(54.5), s(50.5), s(61.5), s(57.5)], fill=AMBER)
        # small diagonal tick
        d.line([s(60), s(58), s(76), s(64)], fill=WHITE, width=int(s(3)))
    
    elif i == 2:
        # 十点十分月牙
        img = rounded_bg(None, NIGHT_TOP, NIGHT_BOT)
        d = ImageDraw.Draw(img)
        # crescent moon
        d.ellipse([s(24), s(24), s(84), s(84)], fill=WHITE)
        d.ellipse([s(34), s(20), s(88), s(80)], fill=NIGHT_BOT)
        # clock hands 10:10
        d.line([s(54), s(54), s(44), s(42)], fill=PRIMARY, width=int(s(4)))
        d.line([s(54), s(54), s(64), s(42)], fill=PRIMARY, width=int(s(4)))
        d.ellipse([s(51), s(51), s(57), s(57)], fill=AMBER)
        # small star
        d.ellipse([s(76), s(28), s(80), s(32)], fill=AMBER)
    
    elif i == 3:
        # 折角便签 + 时钟
        img = rounded_bg(None, PRIMARY, PRIMARY_DARK)
        d = ImageDraw.Draw(img)
        # page
        d.rounded_rectangle([s(30), s(28), s(78), s(82)], radius=s(6), fill=WHITE)
        # fold corner
        fold_pts = [(s(62), s(28)), (s(78), s(28)), (s(78), s(44))]
        d.polygon(fold_pts, fill=SOFT_WHITE)
        d.line([s(62), s(44), s(78), s(28)], fill=(200,205,230,255), width=int(s(1.5)))
        # clock
        d.ellipse([s(42), s(44), s(66), s(68)], outline=PRIMARY, width=int(s(2)))
        d.line([s(54), s(56), s(54), s(49)], fill=PRIMARY, width=int(s(2)))
        d.line([s(54), s(56), s(59), s(58)], fill=PRIMARY, width=int(s(2)))
        d.ellipse([s(52.5), s(54.5), s(55.5), s(57.5)], fill=AMBER)
    
    elif i == 4:
        # 识别星芒 v2
        img = rounded_bg(None, WHITE, (245, 247, 255, 255))
        d = ImageDraw.Draw(img)
        # main 4-point star
        pts = four_point_star(s(54), s(54), s(26), s(10))
        d.polygon(pts, fill=PRIMARY)
        # center highlight
        d.ellipse([s(48), s(48), s(60), s(60)], fill=(120, 140, 255, 255))
        # sparkles
        d.ellipse([s(73), s(31), s(79), s(37)], fill=AMBER)
        d.ellipse([s(29.8), s(71.8), s(34.2), s(76.2)], fill=AMBER)
    
    elif i == 5:
        # 倒计时环
        img = rounded_bg(None, PRIMARY, PRIMARY_DARK)
        d = ImageDraw.Draw(img)
        # background ring (faint)
        d.ellipse([s(26), s(26), s(82), s(82)], outline=(255,255,255,40), width=int(s(8)))
        # progress arc 270 degrees
        d.arc([s(26), s(26), s(82), s(82)], start=-90, end=180, fill=WHITE, width=int(s(8)))
        # center 5-point star
        pts = star_points(s(54), s(54), s(10), s(4), 5)
        d.polygon(pts, fill=AMBER)
    
    elif i == 6:
        # 星夜时钟
        img = rounded_bg(None, NIGHT_TOP, NIGHT_BOT)
        d = ImageDraw.Draw(img)
        # clock face ring
        d.ellipse([s(22), s(22), s(86), s(86)], fill=(255,255,255,30))
        d.ellipse([s(25), s(25), s(83), s(83)], fill=NIGHT_BOT)
        # star markers
        for i in range(12):
            a = math.radians(i * 30 - 90)
            sx = s(54) + math.cos(a) * s(29)
            sy = s(54) + math.sin(a) * s(29)
            d.ellipse([sx-s(1.5), sy-s(1.5), sx+s(1.5), sy+s(1.5)], fill=AMBER)
        # hands 10:10
        d.line([s(54), s(54), s(46), s(44)], fill=PRIMARY, width=int(s(3)))
        d.line([s(54), s(54), s(64), s(44)], fill=PRIMARY, width=int(s(3)))
        d.ellipse([s(51), s(51), s(57), s(57)], fill=AMBER)
        # bg stars
        d.ellipse([s(18.8), s(20.8), s(21.2), s(23.2)], fill=(200,210,255,255))
        d.ellipse([s(86.8), s(78.8), s(89.2), s(81.2)], fill=(200,210,255,255))
    
    elif i == 7:
        # 时光沙漏
        img = rounded_bg(None, PRIMARY, PRIMARY_DARK)
        d = ImageDraw.Draw(img)
        # top triangle
        d.polygon([(s(36), s(32)), (s(72), s(32)), (s(54), s(50))], outline=WHITE, width=int(s(4)))
        # bottom triangle
        d.polygon([(s(36), s(76)), (s(72), s(76)), (s(54), s(58))], outline=WHITE, width=int(s(4)))
        # top sand
        d.polygon([(s(40), s(38)), (s(68), s(38)), (s(54), s(48))], fill=AMBER)
        # falling stream
        d.line([s(54), s(48), s(54), s(58)], fill=AMBER, width=int(s(2)))
        # bottom sand pile
        d.polygon([(s(50), s(70)), (s(58), s(70)), (s(54), s(62))], fill=AMBER)
    
    elif i == 8:
        # AI 之眼
        img = rounded_bg(None, NIGHT_TOP, NIGHT_BOT)
        d = ImageDraw.Draw(img)
        # eye shape
        eye_pts = []
        for t in range(101):
            tt = t / 100.0
            # upper and lower curves
            x = s(24) + tt * s(60)
            # almond curve
            w = math.sin(tt * math.pi)
            y_up = s(54) - w * s(18)
            y_dn = s(54) + w * s(18)
            eye_pts.append((x, y_up))
        for t in range(100, -1, -1):
            tt = t / 100.0
            x = s(24) + tt * s(60)
            w = math.sin(tt * math.pi)
            y_dn = s(54) + w * s(18)
            eye_pts.append((x, y_dn))
        d.polygon(eye_pts, fill=WHITE)
        # iris
        d.ellipse([s(40), s(40), s(68), s(68)], fill=PRIMARY)
        # pupil
        d.ellipse([s(47), s(47), s(61), s(61)], fill=WHITE)
        # clock hands
        d.line([s(54), s(54), s(54), s(49)], fill=PRIMARY, width=int(s(2)))
        d.line([s(54), s(54), s(58), s(56)], fill=PRIMARY, width=int(s(2)))
        d.ellipse([s(52.5), s(52.5), s(55.5), s(55.5)], fill=AMBER)
        # sparkle
        d.ellipse([s(77.5), s(33.5), s(82.5), s(38.5)], fill=AMBER)
    
    elif i == 9:
        # 便签钉
        img = rounded_bg(None, PRIMARY, PRIMARY_DARK)
        d = ImageDraw.Draw(img)
        # sticky note
        d.rounded_rectangle([s(32), s(34), s(76), s(80)], radius=s(4), fill=WHITE)
        # lines
        d.line([s(40), s(48), s(68), s(48)], fill=(180,185,210,255), width=int(s(1.5)))
        d.line([s(40), s(56), s(64), s(56)], fill=(180,185,210,255), width=int(s(1.5)))
        d.line([s(40), s(64), s(60), s(64)], fill=(180,185,210,255), width=int(s(1.5)))
        # pin star
        pts = star_points(s(54), s(30), s(8), s(3.5), 5)
        d.polygon(pts, fill=AMBER)
    
    elif i == 10:
        # 夜空指针
        img = rounded_bg(None, NIGHT_TOP, NIGHT_BOT)
        d = ImageDraw.Draw(img)
        # faint ring
        d.ellipse([s(24), s(24), s(84), s(84)], outline=(255,255,255,30), width=int(s(2)))
        # hands 10:10
        d.line([s(54), s(54), s(44), s(44)], fill=WHITE, width=int(s(5)))
        d.line([s(54), s(54), s(66), s(44)], fill=WHITE, width=int(s(5)))
        # center
        d.ellipse([s(50), s(50), s(58), s(58)], fill=AMBER)
        # bright star
        pts = star_points(s(78), s(28), s(5), s(2), 5)
        d.polygon(pts, fill=AMBER)
        # tiny stars
        d.ellipse([s(23), s(77), s(25), s(79)], fill=(180,190,230,255))
        d.ellipse([s(83), s(71), s(85), s(73)], fill=(180,190,230,255))
    
    return img

# Render and save all 10
imgs = []
for i in range(1, 11):
    img = make_icon(i)
    img.save(f'yishi_icon_{i:02d}.png')
    imgs.append(img)
    print(f'icon {i} done')

# Preview sheet 5x2
cell = 560
sheet = Image.new('RGBA', (5*cell, 2*cell), (255,255,255,255))
for idx, img in enumerate(imgs):
    row = idx // 5
    col = idx % 5
    ox = col * cell + (cell - SIZE) // 2
    oy = row * cell + (cell - SIZE) // 2
    sheet.paste(img, (ox, oy), img)
sheet.save('yishi_icons_preview.png')
print('preview done')
