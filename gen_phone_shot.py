from PIL import Image, ImageDraw, ImageFont
FONT = "C:/Windows/Fonts/msyh.ttc"
img = Image.new("RGB", (1080, 700), (237,237,237))
d = ImageDraw.Draw(img)
d.rectangle([0,0,1080,170], fill=(60,62,90))
d.text((40,60), "< 计科2024级4班", font=ImageFont.truetype(FONT,44), fill=(255,255,255))
d.rounded_rectangle([60,240,1020,430], radius=16, fill=(255,255,255))
d.text((100,280), "@全体成员 下周三16点进行数据", font=ImageFont.truetype(FONT,40), fill=(20,20,20))
d.text((100,350), "库设计的验收，实验报告也要交", font=ImageFont.truetype(FONT,40), fill=(20,20,20))
img.save("phone_test_shot.jpg", quality=88)
print("已生成 phone_test_shot.jpg")
