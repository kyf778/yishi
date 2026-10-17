"""Render current w06 识别星芒 at 512px for comparison."""
from PIL import Image, ImageDraw
import math

SIZE = 512
R = SIZE / 108.0
def s(v): return v * R

img = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 255))  # white bg (matches ic_launcher_background)
d = ImageDraw.Draw(img)

# Main 4-point star from ic_launcher_foreground.xml path data
# Path: M54,31 Q57.7,50.3 77,54 Q57.7,57.7 54,77 Q50.3,57.7 31,54 Q50.3,50.3 54,31 Z
# This is a 4-point star with curved sides. Let's reconstruct it with bezier.
# Control points approximate the quadratic beziers.
def quad(p0, p1, p2, steps=40):
    pts = []
    for i in range(steps+1):
        t = i/steps
        u = 1-t
        pts.append((u*u*p0[0] + 2*u*t*p1[0] + t*t*p2[0],
                    u*u*p0[1] + 2*u*t*p1[1] + t*t*p2[1]))
    return pts

p0 = (s(54), s(31))
p1 = (s(57.7), s(50.3))
p2 = (s(77), s(54))
p3 = (s(57.7), s(57.7))
p4 = (s(54), s(77))
p5 = (s(50.3), s(57.7))
p6 = (s(31), s(54))
p7 = (s(50.3), s(50.3))

star = quad(p0, p1, p2) + quad(p2, p3, p4) + quad(p4, p5, p6) + quad(p6, p7, p0)
d.polygon(star, fill=(79, 107, 255, 255))

# Yellow star 1 (top-right)
s1_p0 = (s(73.55), s(29.39))
s1_p1 = (s(74.36), s(33.64))
s1_p2 = (s(78.61), s(34.45))
s1_p3 = (s(74.36), s(35.26))
s1_p4 = (s(73.55), s(39.51))
s1_p5 = (s(72.74), s(35.26))
s1_p6 = (s(68.49), s(34.45))
s1_p7 = (s(72.74), s(33.64))
star1 = quad(s1_p0, s1_p1, s1_p2) + quad(s1_p2, s1_p3, s1_p4) + quad(s1_p4, s1_p5, s1_p6) + quad(s1_p6, s1_p7, s1_p0)
d.polygon(star1, fill=(255, 170, 31, 255))

# Yellow star 2 (bottom-right, smaller)
s2_p0 = (s(70.56), s(69.18))
s2_p1 = (s(71.00), s(71.50))
s2_p2 = (s(73.32), s(71.94))
s2_p3 = (s(71.00), s(72.38))
s2_p4 = (s(70.56), s(74.70))
s2_p5 = (s(70.12), s(72.38))
s2_p6 = (s(67.80), s(71.94))
s2_p7 = (s(70.12), s(71.50))
star2 = quad(s2_p0, s2_p1, s2_p2) + quad(s2_p2, s2_p3, s2_p4) + quad(s2_p4, s2_p5, s2_p6) + quad(s2_p6, s2_p7, s2_p0)
d.polygon(star2, fill=(255, 170, 31, 255))

img.save('yishi_icon_w06_current.png')
print('w06 rendered')
