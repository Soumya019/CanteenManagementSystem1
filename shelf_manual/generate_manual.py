#!/usr/bin/env python3
"""
Garia Station Electric Hub — Counter Sales Manual generator.

Builds a customer-facing price manual PDF: every stocked item gets a
colour-matched product illustration (matched to the packaging on the shop
shelves), the item name, pack/shelf notes, and the recommended retail
price taken from the commercial inventory ledger (13 July 2026).

Only selling prices appear in the output — purchase costs and margins stay
in the ledger, so the manual is safe to keep on the shop counter.

Usage:  python3 generate_manual.py [output.pdf]
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table,
    TableStyle, Image as RLImage, PageBreak, KeepTogether,
)

HERE = os.path.dirname(os.path.abspath(__file__))
ICON_DIR = os.path.join(HERE, "icons")
PHOTO_DIR = os.path.join(HERE, "photos")
CARD_DIR = os.path.join(HERE, "cards")

try:
    from photo_queries import ITEM_PHOTO
except ImportError:
    ITEM_PHOTO = {}
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

S = 600          # icon draw canvas (supersampled)
OUT = 300        # final icon size

# ---------------------------------------------------------------- brands ---

BRANDS = {
    # bg = card colour, band = header strip colour (None = text only),
    # txt = brand text colour
    "magik":    dict(name="MAGIK",      bg="#1B2A4A", band=None,      txt="#FFFFFF"),
    "rkgold":   dict(name="RK GOLD",    bg="#1A1B20", band=None,      txt="#E7C55A"),
    "z4":       dict(name="Z4 KABEL",   bg="#F2C400", band=None,      txt="#1A1A1A"),
    "heera":    dict(name="HEERA",      bg="#49C7B8", band=None,      txt="#B3261E"),
    "pritam":   dict(name="PRITAM",     bg="#F4F4F4", band="#D1332E", txt="#FFFFFF"),
    "alfa":     dict(name="PRITAM ALFA", bg="#F5C51C", band=None,     txt="#C62222"),
    "silver":   dict(name="SILVER LINE", bg="#D9DDE2", band=None,     txt="#4A4F55"),
    "kamla":    dict(name="KAMLA",      bg="#79C043", band=None,      txt="#FFFFFF"),
    "velox":    dict(name="VELOX",      bg="#F4F4F4", band="#C62222", txt="#FFFFFF"),
    "aastha":   dict(name="AASTHA",     bg="#F4F4F4", band="#C43A2F", txt="#FFFFFF"),
    "polonix":  dict(name="POLONIX",    bg="#E03A2F", band=None,      txt="#FFFFFF"),
    "florex":   dict(name="FLOREX",     bg="#D6DE4E", band=None,      txt="#8E2A20"),
    "stronx":   dict(name="STRONX",     bg="#F4F4F4", band="#E23B2E", txt="#FFFFFF"),
    "starflex": dict(name="STAR-FLEX",  bg="#EDEDED", band="#2E7D32", txt="#FFFFFF"),
    "hrcab":    dict(name="H.R. CAB",   bg="#EDEDED", band="#26466D", txt="#FFFFFF"),
    "ultima":   dict(name="ULTIMA",     bg="#EAD28A", band=None,      txt="#4A3B12"),
    "zg":       dict(name="Z.G.",       bg="#EDEDED", band="#37474F", txt="#FFFFFF"),
    None:       dict(name="",           bg="#EDEFF2", band=None,      txt="#37474F"),
}


def _rgb(hexcol):
    hexcol = hexcol.lstrip("#")
    return tuple(int(hexcol[i:i + 2], 16) for i in (0, 2, 4))


def _lum(rgb):
    r, g, b = rgb
    return 0.299 * r + 0.587 * g + 0.114 * b


def _font(sz):
    return ImageFont.truetype(FONT_BOLD, sz)


def _ctext(d, xy, s, sz, fill):
    d.text(xy, s, font=_font(sz), fill=fill, anchor="mm")


# ------------------------------------------------------------ icon glyphs ---
# All glyphs draw on a 600x600 canvas, roughly centred on (300, 320).
# fg is the main glyph colour, ln the outline colour.

def g_bulb(d, fg, ln, big=False):
    r = 105 if big else 88
    d.ellipse([300 - r, 270 - r, 300 + r, 270 + r], fill=fg, outline=ln, width=4)
    d.rectangle([268, 270 + r - 22, 332, 402], fill=fg, outline=ln, width=3)
    d.rounded_rectangle([272, 402, 328, 452], radius=10, fill="#B8BEC6", outline=ln, width=3)
    for y in (416, 432):
        d.line([274, y, 326, y], fill="#7d848d", width=4)


def g_panel(d, fg, ln):
    d.rounded_rectangle([190, 210, 410, 430], radius=28, fill=fg, outline=ln, width=5)
    d.rounded_rectangle([225, 245, 375, 395], radius=18, outline="#c3c9d1", width=5)


def g_downlight(d, fg, ln):
    d.ellipse([175, 250, 425, 370], fill=fg, outline=ln, width=5)
    d.ellipse([225, 275, 375, 345], fill="#FFF8D6", outline="#c3c9d1", width=4)


def g_tube(d, fg, ln):
    d.rounded_rectangle([120, 285, 480, 340], radius=26, fill=fg, outline=ln, width=4)
    d.rectangle([120, 285, 160, 340], fill="#9AA2AB", outline=ln, width=3)
    d.rectangle([440, 285, 480, 340], fill="#9AA2AB", outline=ln, width=3)


def _plate(d, fg, ln, box=(195, 205, 405, 415)):
    d.rounded_rectangle(list(box), radius=22, fill=fg, outline=ln, width=5)


def g_switch(d, fg, ln, accent="#4A4F55"):
    _plate(d, fg, ln)
    d.rounded_rectangle([268, 248, 332, 372], radius=12, fill="#FFFFFF",
                        outline="#9AA0A6", width=4)
    d.ellipse([292, 336, 308, 352], fill=accent)


def g_switch2way(d, fg, ln):
    g_switch(d, fg, ln)
    _ctext(d, (300, 445), "1 • 2", 30, "#6B7075")


def g_bell(d, fg, ln):
    _plate(d, fg, ln)
    d.ellipse([258, 268, 342, 352], fill="#E5A63C", outline="#9AA0A6", width=4)
    _ctext(d, (300, 310), "BELL", 24, "#5C4308")


def g_indicator(d, fg, ln):
    _plate(d, fg, ln)
    d.rounded_rectangle([258, 272, 342, 348], radius=12, fill="#E53935",
                        outline="#9AA0A6", width=4)
    d.rounded_rectangle([274, 288, 326, 332], radius=8, fill="#FF8A80")


def g_regulator(d, fg, ln):
    _plate(d, fg, ln)
    d.ellipse([248, 258, 352, 362], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.line([300, 310, 300, 266], fill="#4A4F55", width=8)
    for a in (-60, -20, 20, 60):
        import math
        x = 300 + 68 * math.sin(math.radians(a))
        y = 310 - 68 * math.cos(math.radians(a))
        d.ellipse([x - 5, y - 5, x + 5, y + 5], fill="#8b9096")


def _holes3(d, cx, cy, k=1.0):
    r1, r2 = int(13 * k), int(17 * k)
    dx, dy = int(36 * k), int(58 * k)
    d.ellipse([cx - dx - r1, cy - r1, cx - dx + r1, cy + r1], fill="#3A3F45")
    d.ellipse([cx + dx - r1, cy - r1, cx + dx + r1, cy + r1], fill="#3A3F45")
    d.ellipse([cx - r2, cy + dy - r2, cx + r2, cy + dy + r2], fill="#3A3F45")


def g_socket(d, fg, ln):
    _plate(d, fg, ln)
    _holes3(d, 300, 282)


def g_socket5(d, fg, ln):
    _plate(d, fg, ln)
    _holes3(d, 300, 270)
    d.ellipse([238, 320, 262, 344], fill="#3A3F45")
    d.ellipse([338, 320, 362, 344], fill="#3A3F45")


def g_socket2in1(d, fg, ln):
    _plate(d, fg, ln, box=(160, 225, 440, 400))
    _holes3(d, 232, 285, 0.85)
    _holes3(d, 368, 285, 0.85)


def g_combined(d, fg, ln):
    _plate(d, fg, ln, box=(160, 225, 440, 400))
    _holes3(d, 235, 280, 0.85)
    d.rounded_rectangle([340, 258, 392, 356], radius=10, fill="#FFFFFF",
                        outline="#9AA0A6", width=4)
    d.ellipse([358, 330, 374, 346], fill="#4A4F55")


def g_plug(d, fg, ln, pins=3):
    d.polygon([(226, 300), (374, 300), (346, 442), (254, 442)],
              fill="#FFFFFF", outline="#9AA0A6")
    if pins == 3:
        d.rectangle([288, 178, 312, 300], fill="#C9A227", outline="#8a6d12")
        d.rectangle([246, 226, 268, 300], fill="#C9A227", outline="#8a6d12")
        d.rectangle([332, 226, 354, 300], fill="#C9A227", outline="#8a6d12")
    else:
        d.rectangle([254, 210, 276, 300], fill="#C9A227", outline="#8a6d12")
        d.rectangle([324, 210, 346, 300], fill="#C9A227", outline="#8a6d12")
    d.line([300, 442, 300, 486], fill="#4A4F55", width=8)


def g_multiplug(d, fg, ln):
    d.rounded_rectangle([200, 215, 400, 425], radius=26, fill="#FFFFFF",
                        outline="#9AA0A6", width=5)
    _holes3(d, 300, 265, 0.8)
    d.ellipse([252, 356, 276, 380], fill="#3A3F45")
    d.ellipse([324, 356, 348, 380], fill="#3A3F45")
    d.ellipse([288, 348, 312, 372], fill="#3A3F45")


def g_extension(d, fg, ln):
    d.rounded_rectangle([140, 240, 430, 380], radius=24, fill="#FFFFFF",
                        outline="#9AA0A6", width=5)
    for cx in (205, 285, 365):
        d.ellipse([cx - 12, 280 - 12, cx + 12, 280 + 12], fill="#3A3F45")
        d.ellipse([cx - 32, 322 - 10, cx - 12, 322 + 10], fill="#3A3F45")
        d.ellipse([cx + 12, 322 - 10, cx + 32, 322 + 10], fill="#3A3F45")
    d.ellipse([398, 296, 418, 324], fill="#E53935")
    d.arc([330, 360, 500, 470], start=180, end=330, fill="#4A4F55", width=8)


def g_mcb(d, fg, ln, label="C16"):
    d.rounded_rectangle([238, 185, 362, 435], radius=16, fill="#FFFFFF",
                        outline="#9AA0A6", width=5)
    d.rectangle([238, 250, 362, 268], fill="#3F51B5")
    d.rounded_rectangle([280, 292, 320, 366], radius=8, fill="#2F3C8E",
                        outline="#222a63", width=3)
    d.rectangle([284, 322, 316, 336], fill="#FFFFFF")
    _ctext(d, (300, 400), label, 30, "#4A4F55")


def g_mainswitch(d, fg, ln):
    d.rounded_rectangle([195, 210, 405, 410], radius=14, fill="#9AA2AB",
                        outline="#5f666e", width=5)
    d.rounded_rectangle([225, 240, 375, 330], radius=8, fill="#C6CCD2",
                        outline="#5f666e", width=3)
    d.rounded_rectangle([405, 282, 448, 338], radius=8, fill="#C62828",
                        outline="#7c1a1a", width=3)


def g_meter(d, fg, ln):
    d.rounded_rectangle([210, 190, 390, 430], radius=20, fill="#3A3F45",
                        outline="#22262a", width=5)
    d.rounded_rectangle([240, 235, 360, 292], radius=8, fill="#CDE7A8")
    _ctext(d, (300, 264), "004862", 30, "#33421c")
    d.ellipse([258, 330, 286, 358], fill="#C62828")
    d.ellipse([314, 330, 342, 358], fill="#5f666e")


def g_meterbox(d, fg, ln):
    d.rounded_rectangle([195, 195, 405, 425], radius=14, fill="#D9DDE2",
                        outline="#7d848d", width=5)
    d.rounded_rectangle([240, 240, 360, 330], radius=8, outline="#7d848d", width=5)
    d.rectangle([282, 360, 318, 384], fill="#7d848d")


def g_coil(d, fg, ln, c1="#E8E4DA", c2="#C62828"):
    import math
    cx, cy, r, w = 300, 310, 122, 54
    d.ellipse([cx - r - w // 2, cy - r - w // 2, cx + r + w // 2, cy + r + w // 2],
              fill=c1, outline="#00000030")
    for i in range(10):
        if i % 2:
            a0 = i * 36
            d.arc([cx - r - w // 2, cy - r - w // 2, cx + r + w // 2, cy + r + w // 2],
                  start=a0, end=a0 + 18, fill=c2, width=w)
    hole = r - w // 2 - 6
    d.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], fill=fg)
    for a in (45, 135, 225, 315):
        x = cx + r * math.cos(math.radians(a))
        y = cy + r * math.sin(math.radians(a))
        d.rounded_rectangle([x - 14, y - 20, x + 14, y + 20], radius=8,
                            fill="#FFFFFF", outline="#9AA0A6", width=3)


def g_tape(d, fg, ln, c="#1A1A1A"):
    d.ellipse([185, 235, 415, 405], fill=c, outline="#00000060", width=4)
    d.ellipse([262, 290, 338, 350], fill=fg, outline="#9AA0A6", width=4)
    d.rectangle([400, 300, 476, 336], fill=c)


def g_modulebox(d, fg, ln, n=4):
    d.rounded_rectangle([170, 235, 430, 405], radius=14, fill="#FFFFFF",
                        outline="#9AA0A6", width=6)
    cols = min(n, 6)
    wid = 220 // max(cols, 3)
    total = wid * cols
    x0 = 300 - total // 2
    for i in range(cols):
        d.rectangle([x0 + i * wid + 6, 268, x0 + (i + 1) * wid - 6, 372],
                    outline="#9AA0A6", width=4)
    for x, y in ((196, 260), (404, 260), (196, 380), (404, 380)):
        d.ellipse([x - 8, y - 8, x + 8, y + 8], fill="#c3c9d1")


def g_coverplate(d, fg, ln, n=4):
    d.rounded_rectangle([170, 245, 430, 395], radius=20, fill="#FFFFFF",
                        outline="#9AA0A6", width=6)
    cols = min(n, 6)
    wid = 190 / max(cols, 3)
    total = wid * cols
    x0 = 300 - total / 2
    for i in range(cols):
        d.rounded_rectangle([x0 + i * wid + 7, 288, x0 + (i + 1) * wid - 7, 352],
                            radius=8, fill="#E8EAED", outline="#c3c9d1", width=3)


def g_fuse(d, fg, ln):
    d.polygon([(232, 430), (368, 430), (348, 372), (252, 372)],
              fill="#FFFFFF", outline="#9AA0A6")
    d.rounded_rectangle([272, 220, 328, 386], radius=12, fill="#FFFFFF",
                        outline="#9AA0A6", width=5)
    d.rounded_rectangle([284, 196, 316, 232], radius=8, fill="#E5A63C",
                        outline="#8a6d12", width=3)
    d.ellipse([238, 388, 258, 408], fill="#C9A227")
    d.ellipse([342, 388, 362, 408], fill="#C9A227")


def g_holder(d, fg, ln, body="#FFFFFF"):
    d.ellipse([262, 208, 338, 240], fill=body, outline="#9AA0A6", width=4)
    d.rectangle([268, 224, 332, 330], fill=body, outline="#9AA0A6", width=4)
    d.polygon([(240, 396), (360, 396), (332, 330), (268, 330)],
              fill=body, outline="#9AA0A6")
    d.ellipse([252, 384, 348, 412], fill=body, outline="#9AA0A6", width=4)
    d.line([300, 160, 300, 208], fill="#4A4F55", width=7)


def g_angleholder(d, fg, ln):
    d.ellipse([210, 360, 390, 416], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.polygon([(270, 372), (330, 372), (388, 258), (338, 232)],
              fill="#FFFFFF", outline="#9AA0A6")
    d.ellipse([326, 212, 400, 268], fill="#E8EAED", outline="#9AA0A6", width=5)


def g_ceilingrose(d, fg, ln):
    d.pieslice([215, 250, 385, 420], start=180, end=360,
               fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.ellipse([200, 322, 400, 366], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.line([300, 366, 300, 430], fill="#4A4F55", width=7)


def g_cableties(d, fg, ln):
    for i, x in enumerate((240, 285, 330)):
        d.line([x, 200, x + 42, 430], fill="#FFFFFF", width=11)
        d.rectangle([x - 12, 188, x + 14, 216], fill="#FFFFFF", outline="#9AA0A6", width=3)
    d.ellipse([330, 330, 430, 430], outline="#FFFFFF", width=11)
    d.rectangle([368, 318, 396, 344], fill="#FFFFFF", outline="#9AA0A6", width=3)


def g_tester(d, fg, ln):
    d.rounded_rectangle([272, 180, 328, 330], radius=18, fill="#E5A63C",
                        outline="#8a6d12", width=4)
    d.rectangle([322, 190, 336, 300], fill="#C9CDD4")
    d.rectangle([290, 330, 310, 452], fill="#9AA2AB", outline="#5f666e", width=3)
    d.rectangle([282, 452, 318, 464], fill="#5f666e")


def g_changeover(d, fg, ln):
    d.rounded_rectangle([200, 215, 400, 415], radius=14, fill="#D9DDE2",
                        outline="#7d848d", width=5)
    d.ellipse([252, 262, 348, 358], fill="#FFFFFF", outline="#7d848d", width=5)
    d.line([268, 342, 332, 278], fill="#C62828", width=14)
    _ctext(d, (300, 392), "1 · 0 · 2", 26, "#4A4F55")


def g_bedswitch(d, fg, ln):
    d.line([300, 140, 300, 220], fill="#4A4F55", width=7)
    d.ellipse([246, 220, 354, 396], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.rounded_rectangle([282, 268, 318, 348], radius=10, fill="#E8EAED",
                        outline="#9AA0A6", width=4)
    d.line([300, 396, 300, 470], fill="#4A4F55", width=7)


def g_connector(d, fg, ln):
    d.rounded_rectangle([165, 272, 435, 352], radius=12, fill="#FFFFFF",
                        outline="#9AA0A6", width=5)
    for i in range(1, 6):
        d.line([165 + i * 45, 272, 165 + i * 45, 352], fill="#c3c9d1", width=4)
    for i in range(6):
        cx = 188 + i * 45
        d.ellipse([cx - 8, 304 - 8, cx + 8, 304 + 8], fill="#5f666e")


def g_polyroll(d, fg, ln):
    d.rectangle([215, 235, 385, 400], fill="#F2F4F6", outline="#9AA0A6", width=5)
    d.ellipse([215, 208, 385, 262], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.ellipse([272, 222, 328, 248], fill="#c3c9d1")
    d.ellipse([215, 374, 385, 426], outline="#9AA0A6", width=5)


def g_caps(d, fg, ln):
    d.polygon([(250, 400), (310, 400), (280, 250)], fill="#FFFFFF", outline="#9AA0A6")
    d.polygon([(320, 410), (388, 386), (300, 276)], fill="#E8EAED", outline="#9AA0A6")
    d.rectangle([246, 388, 314, 404], fill="#c3c9d1")


def g_wallplug(d, fg, ln, c="#E5A63C"):
    for x in (255, 345):
        d.ellipse([x - 26, 210, x + 26, 234], fill=c, outline="#8a6d12", width=3)
        d.rectangle([x - 16, 226, x + 16, 400], fill=c, outline="#8a6d12", width=3)
        for y in range(250, 390, 24):
            d.line([x - 16, y, x + 16, y + 12], fill="#8a6d12", width=4)
        d.line([x, 340, x, 400], fill="#8a6d12", width=5)


def g_screw(d, fg, ln, c="#C9CDD4"):
    for x, y0 in ((260, 200), (340, 230)):
        d.rectangle([x - 30, y0, x + 30, y0 + 34], fill=c, outline="#5f666e", width=3)
        d.line([x - 18, y0 + 17, x + 18, y0 + 17], fill="#5f666e", width=6)
        d.polygon([(x - 16, y0 + 34), (x + 16, y0 + 34), (x, y0 + 230)],
                  fill=c, outline="#5f666e")
        for i, y in enumerate(range(y0 + 52, y0 + 200, 26)):
            k = 16 - i * 2
            d.line([x - k, y, x + k, y + 10], fill="#5f666e", width=4)


def g_clip(d, fg, ln, c="#C9CDD4"):
    for cx, cy in ((240, 270), (330, 350)):
        d.arc([cx - 55, cy - 55, cx + 55, cy + 55], start=180, end=360, fill=c, width=20)
        d.rectangle([cx - 75, cy - 6, cx - 45, cy + 10], fill=c, outline="#5f666e", width=2)
        d.rectangle([cx + 45, cy - 6, cx + 75, cy + 10], fill=c, outline="#5f666e", width=2)
        d.ellipse([cx - 66, cy - 4, cx - 54, cy + 8], fill="#5f666e")
        d.ellipse([cx + 54, cy - 4, cx + 66, cy + 8], fill="#5f666e")


def g_nails(d, fg, ln):
    for x in (250, 300, 350):
        d.ellipse([x - 16, 200, x + 16, 216], fill="#C9CDD4", outline="#5f666e", width=3)
        d.polygon([(x - 5, 214), (x + 5, 214), (x, 430)], fill="#C9CDD4", outline="#5f666e")


def g_joyconn(d, fg, ln):
    d.rounded_rectangle([170, 280, 280, 350], radius=14, fill="#FFFFFF",
                        outline="#9AA0A6", width=4)
    d.rectangle([280, 300, 330, 330], fill="#C9A227", outline="#8a6d12", width=3)
    d.rounded_rectangle([340, 280, 450, 350], radius=14, fill="#FFFFFF",
                        outline="#9AA0A6", width=4)
    d.ellipse([352, 302, 378, 328], fill="#3A3F45")


def g_pins2(d, fg, ln):
    d.ellipse([230, 260, 370, 400], fill="#FFFFFF", outline="#9AA0A6", width=5)
    d.rectangle([258, 180, 280, 262], fill="#C9A227", outline="#8a6d12", width=3)
    d.rectangle([320, 180, 342, 262], fill="#C9A227", outline="#8a6d12", width=3)


GLYPHS = {
    "bulb": g_bulb, "panel": g_panel, "downlight": g_downlight, "tube": g_tube,
    "switch": g_switch, "switch2way": g_switch2way, "bell": g_bell,
    "indicator": g_indicator, "regulator": g_regulator, "socket": g_socket,
    "socket5": g_socket5, "socket2in1": g_socket2in1, "combined": g_combined,
    "plug": g_plug, "multiplug": g_multiplug, "extension": g_extension,
    "mcb": g_mcb, "mainswitch": g_mainswitch, "meter": g_meter,
    "meterbox": g_meterbox, "coil": g_coil, "tape": g_tape,
    "modulebox": g_modulebox, "coverplate": g_coverplate, "fuse": g_fuse,
    "holder": g_holder, "angleholder": g_angleholder, "ceilingrose": g_ceilingrose,
    "cableties": g_cableties, "tester": g_tester, "changeover": g_changeover,
    "bedswitch": g_bedswitch, "connector": g_connector, "polyroll": g_polyroll,
    "caps": g_caps, "wallplug": g_wallplug, "screw": g_screw, "clip": g_clip,
    "nails": g_nails, "joyconn": g_joyconn, "pins2": g_pins2,
}


def make_photo_card(path, photo_path, label=None):
    """Compose a real product photo onto a white card with a spec chip."""
    img = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 8, S - 8, S - 8], radius=52, fill=(255, 255, 255),
                        outline=(40, 40, 40, 70), width=3)
    photo = Image.open(photo_path).convert("RGB")
    box_w, box_h = S - 72, S - 150 if label else S - 88
    photo.thumbnail((box_w, box_h), Image.LANCZOS)
    px = (S - photo.size[0]) // 2
    py = 36 + (box_h - photo.size[1]) // 2
    mask = Image.new("L", photo.size, 255)
    img.paste(photo, (px, py), mask)
    if label:
        tw = _font(38).getlength(label)
        d.rounded_rectangle([S / 2 - tw / 2 - 22, 500, S / 2 + tw / 2 + 22, 564],
                            radius=30, fill=(58, 63, 69, 235))
        _ctext(d, (S / 2, 531), label, 38, "#FFFFFF")
    img = img.resize((OUT, OUT), Image.LANCZOS)
    img.save(path)


def make_icon(path, kind, brand=None, label=None, **kw):
    b = BRANDS[brand]
    bg = _rgb(b["bg"])
    img = Image.new("RGBA", (S, S), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([8, 8, S - 8, S - 8], radius=52, fill=bg,
                        outline=(40, 40, 40, 70), width=3)
    dark = _lum(bg) < 128
    fg = (255, 255, 255) if dark else (250, 250, 250)
    ln = "#DDE3EA" if dark else "#9AA0A6"

    if b["band"]:
        d.rounded_rectangle([8, 8, S - 8, 96], radius=52, fill=_rgb(b["band"]))
        d.rectangle([8, 60, S - 8, 96], fill=_rgb(b["band"]))
        _ctext(d, (S / 2, 54), b["name"], 44, b["txt"])
    elif b["name"]:
        _ctext(d, (S / 2, 56), b["name"], 46, b["txt"])

    GLYPHS[kind](d, fg, ln, **kw)

    if label:
        chip_fill = (255, 255, 255, 235) if dark else (58, 63, 69, 255)
        chip_txt = "#1B2A4A" if dark else "#FFFFFF"
        tw = _font(38).getlength(label)
        d.rounded_rectangle([S / 2 - tw / 2 - 22, 496, S / 2 + tw / 2 + 22, 560],
                            radius=30, fill=chip_fill)
        _ctext(d, (S / 2, 527), label, 38, chip_txt)

    img = img.resize((OUT, OUT), Image.LANCZOS)
    img.save(path)


# ------------------------------------------------------------------ items ---

def item(name, price, unit, kind, brand=None, label=None, sub="", **kw):
    return dict(name=name, price=price, unit=unit, kind=kind, brand=brand,
                label=label, sub=sub, kw=kw)


SECTIONS = [
    ("Main Switches, Meters & Steel Fittings", "#455A64", [
        item("32A TPN Main Switch", 680, "pc", "mainswitch", label="32A TPN",
             sub="Metal-clad three-phase main switch"),
        item("16A SPN Main Switch", 290, "pc", "mainswitch", label="16A SPN",
             sub="Single-phase neutral main switch"),
        item("LCD Digital Energy Meter", 365, "pc", "meter", label="LCD",
             sub="Single-phase digital kWh meter"),
        item("Single Phase Meter Box", 260, "pc", "meterbox", label="METER BOX",
             sub="Enclosure for single-phase meter"),
        item("5-in-1 Multi-plug Adaptor", 23, "pc", "multiplug", label="5-IN-1",
             sub="Counter stock — multi-pin travel adaptor"),
        item("5 Pin Multi Socket (Standard)", 13, "pc", "socket5", label="5 PIN",
             sub="Standard grade multi socket"),
        item("5 Pin Multi Socket (Premium)", 15, "pc", "socket5", label="5 PIN",
             brand="aastha", sub="Premium grade — AASTHA red/white boxes"),
        item("PVC Insulation Tape", 13, "roll", "tape", label="PVC TAPE",
             brand="stronx", sub="STRONX 7.5 m rolls — tape rack"),
        item("25MM Steel Screws", 2, "pc", "screw", label="25 MM",
             sub="Loose hardware — sold per piece"),
        item("35MM Steel Screws", 2, "pc", "screw", label="35 MM",
             sub="Loose hardware — sold per piece"),
        item("16MM Steel Screws", 2, "pc", "screw", label="16 MM",
             sub="Loose hardware — sold per piece"),
        item("1.5 Inch Brass Screws", 6, "pc", "screw", label="1.5 IN",
             sub="Brass — small packets on bulb shelf", c="#DcbA5a"),
        item("1 Inch Brass Screws", 6, "pc", "screw", label="1 IN",
             sub="Brass — small packets on bulb shelf", c="#DcbA5a"),
    ]),
    ("MCBs & Combined Units", "#3F51B5", [
        item("Single Pole (SP) MCB — Silk Series", 115, "pc", "mcb", label="SP MCB",
             brand="velox", sub="VELOX red/white boxes — MCB shelf"),
        item("Double Pole (DP) Combined Switch Box", 65, "pc", "combined",
             label="DP", sub="Switch + socket combined unit"),
        item("5-in-1 Modular Combined Box", 80, "pc", "combined", label="5-IN-1",
             sub="Modular combined switch/socket box"),
        item("S.S. Combined Box / Switch Assembly", 55, "pc", "combined",
             label="S.S.", sub="Stainless-finish combined assembly"),
    ]),
    ("LED Bulbs, Panels & Tubes", "#1B2A4A", [
        item("9W LED Bulb", 35, "pc", "bulb", "magik", "9W",
             sub="MAGIK Grande — navy boxes, bulb shelves"),
        item("12W LED Bulb", 70, "pc", "bulb", "magik", "12W",
             sub="MAGIK Grande — navy boxes, bulb shelves"),
        item("15W LED Bulb", 105, "pc", "bulb", "magik", "15W",
             sub="MAGIK Grande — navy boxes, bulb shelves"),
        item("17W LED Bulb", 150, "pc", "bulb", "magik", "17W",
             sub="MAGIK Grande — navy boxes, bulb shelves"),
        item("20W LED Bulb", 200, "pc", "bulb", "magik", "20W",
             sub="MAGIK Grande — navy boxes, bulb shelves"),
        item("23W LED Bulb", 220, "pc", "bulb", "magik", "23W",
             sub="MAGIK Grande — navy boxes"),
        item("30W High-Wattage LED Bulb", 300, "pc", "bulb", "magik", "30W",
             sub="MAGIK Power Plus — navy boxes", big=True),
        item("40W High-Wattage LED Bulb", 400, "pc", "bulb", "magik", "40W",
             sub="MAGIK Power Plus — navy boxes", big=True),
        item("50W High-Wattage LED Bulb", 500, "pc", "bulb", "magik", "50W",
             sub="MAGIK Power Plus — navy boxes", big=True),
        item("9W Slim LED Panel Light (AC)", 245, "pc", "panel", None, "9W",
             sub="Slim round/square ceiling panel"),
        item("12W Slim LED Panel Light", 285, "pc", "panel", None, "12W",
             sub="Slim ceiling panel light"),
        item("20W LED Tube Light / Batten", 110, "pc", "tube", "ultima", "20W",
             sub="ULTIMA Square Sleek cartons — top shelf"),
        item("24W LED Downlight (DO)", 165, "pc", "downlight", None, "24W",
             sub="Recessed LED downlight"),
        item("36W LED Downlight (DO)", 300, "pc", "downlight", None, "36W",
             sub="Recessed LED downlight"),
        item("40W LED Downlight (DO)", 365, "pc", "downlight", None, "40W",
             sub="Recessed LED downlight"),
    ]),
    ("Premium Modular & Piano Switches", "#D1332E", [
        item("Modular Switch (White)", 30, "pc", "switch", "pritam", "6A",
             sub="PRITAM white series — red/white boxes"),
        item("Modular Socket (White)", 30, "pc", "socket", "pritam", "6A",
             sub="PRITAM white series — red/white boxes"),
        item("2-in-1 Modular Socket (White)", 165, "pc", "socket2in1", "pritam",
             "2-IN-1", sub="PRITAM white series"),
        item("Modular Indicator Unit (White)", 85, "pc", "indicator", "pritam",
             "NEON", sub="PRITAM white series"),
        item("2-Way Modular Switch (White)", 85, "pc", "switch2way", "pritam",
             "2 WAY", sub="PRITAM white series"),
        item("Modular Bell Push Switch (White)", 85, "pc", "bell", "pritam",
             "BELL", sub="PRITAM white series"),
        item("16A Heavy-Duty Modular Socket", 160, "pc", "socket", "pritam",
             "16A", sub="PRITAM heavy-duty modular"),
        item("16A Heavy-Duty Modular Switch", 105, "pc", "switch", "pritam",
             "16A", sub="PRITAM heavy-duty modular"),
        item("Modular Fan Regulator (White)", 225, "pc", "regulator", "pritam",
             "FAN REG", sub="PRITAM stepped modular regulator"),
        item("Modular A/C Starter Switch", 705, "pc", "switch", "pritam",
             "20A A/C", sub="PRITAM A/C starter — 5 pcs per box"),
        item("Piano Switch (Silver Line Series)", 36, "pc", "switch", "silver",
             "6A", sub="PRITAM Silver Line — silver boxes"),
        item("Standard Piano Socket", 36, "pc", "socket", "silver", "6A",
             sub="Piano type — silver boxes"),
        item("2-in-1 Piano Socket", 190, "pc", "socket2in1", "silver", "2-IN-1",
             sub="Piano type"),
        item("Piano Indicator Unit", 85, "pc", "indicator", "silver", "NEON",
             sub="Piano type"),
        item("2-Way Piano Switch", 85, "pc", "switch2way", "silver", "2 WAY",
             sub="Piano type"),
        item("Piano Bell Push Switch", 85, "pc", "bell", "silver", "BELL",
             sub="Piano type"),
        item("16A Heavy-Duty Piano Socket", 170, "pc", "socket", "silver", "16A",
             sub="Piano type, heavy duty"),
        item("16A Heavy-Duty Piano Switch", 120, "pc", "switch", "silver", "16A",
             sub="Piano type, heavy duty"),
        item("Fan Regulator (Piano Style)", 365, "pc", "regulator", "silver",
             "FAN REG", sub="Piano style stepped regulator"),
    ]),
    ("PRITAM Modular Boxes & Cover Plates", "#B71C1C", [
        item("2-Module Modular Box", 35, "pc", "modulebox", "pritam", "2 MOD",
             sub="Premium modular surface box", n=2),
        item("3-Module Modular Box", 38, "pc", "modulebox", "pritam", "3 MOD",
             sub="Premium modular surface box", n=3),
        item("4-Module Modular Box", 47, "pc", "modulebox", "pritam", "4 MOD",
             sub="Premium modular surface box", n=4),
        item("6-Module Modular Box", 130, "pc", "modulebox", "pritam", "6 MOD",
             sub="Premium modular surface box", n=6),
        item("8-Module Modular Box", 175, "pc", "modulebox", "pritam", "8 MOD",
             sub="Premium modular surface box", n=8),
        item("12-Module Modular Box", 395, "pc", "modulebox", "pritam", "12 MOD",
             sub="Premium modular surface box", n=12),
        item("2-Module Cover Plate", 38, "pc", "coverplate", "pritam", "2 MOD",
             sub="VITA white cover plate", n=2),
        item("3-Module Cover Plate", 44, "pc", "coverplate", "pritam", "3 MOD",
             sub="VITA white cover plate", n=3),
        item("4-Module Cover Plate", 50, "pc", "coverplate", "pritam", "4 MOD",
             sub="VITA white cover plate", n=4),
        item("6-Module Cover Plate", 125, "pc", "coverplate", "pritam", "6 MOD",
             sub="VITA white cover plate", n=6),
        item("8-Module Cover Plate", 145, "pc", "coverplate", "pritam", "8 MOD",
             sub="VITA white cover plate", n=8),
        item("12-Module Cover Plate", 220, "pc", "coverplate", "pritam", "12 MOD",
             sub="VITA white cover plate", n=12),
    ]),
    ("PRITAM Normal Series (Alfa) Accessories", "#E6A817", [
        item("16A Piano Switch (Ritam)", 46, "pc", "switch", "alfa", "16A",
             sub="PRITAM Alfa — yellow boxes"),
        item("16A Piano Socket (Ritam)", 46, "pc", "socket", "alfa", "16A",
             sub="PRITAM Alfa — yellow boxes"),
        item("6A 3-in-1 Combined Socket", 70, "pc", "combined", "alfa", "3-IN-1",
             sub="PRITAM Alfa — yellow boxes"),
        item("10A Modular Kit-Kat Fuse Unit", 100, "pc", "fuse", "alfa", "10A",
             sub="Kit-kat fuse — PRITAM Alfa / KAMLA green boxes"),
        item("16A 3-Pin Top Plug", 60, "pc", "plug", "alfa", "16A",
             sub="PRITAM Alfa — yellow boxes"),
        item("6A 3-Pin Top Plug", 44, "pc", "plug", "alfa", "6A",
             sub="PRITAM Alfa — yellow boxes"),
        item("Standard Neon Indicator", 34, "pc", "indicator", "alfa", "NEON",
             sub="PRITAM Alfa — yellow boxes"),
        item("Standard Bell Push Switch", 34, "pc", "bell", "alfa", "BELL",
             sub="PRITAM Alfa — yellow boxes"),
        item("2-in-1 Flush Socket Unit", 70, "pc", "socket2in1", "alfa", "2-IN-1",
             sub="PRITAM Alfa — yellow boxes"),
        item("2-Way Light Switch (6A)", 34, "pc", "switch2way", "alfa", "2 WAY",
             sub="PRITAM Alfa — yellow boxes"),
        item("1-Way Light Switch (6A)", 17, "pc", "switch", "alfa", "1 WAY",
             sub="PRITAM Alfa — yellow boxes"),
        item("6-16A Multi-plug Heavy Adaptor", 65, "pc", "multiplug", "alfa",
             "6-16A", sub="PRITAM Alfa — yellow boxes"),
    ]),
    ("Clips, Hardware & House Wires", "#6D4C41", [
        item("1.5 SQMM Copper FR Wire (Z.G.)", 1125, "coil", "coil", "zg",
             "1.5 SQMM", sub="Z4 KABEL yellow boxes — wire shelf, top",
             c1="#F2C400", c2="#1A1A1A"),
        item("1.5 SQMM Copper FR Wire (RK Gold)", 770, "coil", "coil", "rkgold",
             "1.5 SQMM", sub="RK GOLD black & gold boxes — top shelf",
             c1="#8B1E1E", c2="#E7C55A"),
        item("1.0 SQMM Copper FR Wire (RK Gold)", 535, "coil", "coil", "rkgold",
             "1.0 SQMM", sub="RK GOLD black & gold boxes — top shelf",
             c1="#26466D", c2="#E7C55A"),
        item("25MM Heavy Conduit Clips", 9, "pc", "clip", None, "25 MM",
             sub="Heavy conduit clip with nail"),
        item("20MM Heavy Conduit Clips", 8, "pc", "clip", None, "20 MM",
             sub="Heavy conduit clip with nail"),
        item("16MM Heavy Conduit Clips", 12, "pc", "clip", None, "16 MM",
             sub="Heavy conduit clip with nail"),
        item("14MM Wire Clips", 8, "pc", "clip", None, "14 MM",
             sub="Cable clip — grey packs, corner shelf"),
        item("12MM Wire Clips", 7, "pc", "clip", None, "12 MM",
             sub="Cable clip — grey packs, corner shelf"),
        item("10MM Wire Clips", 5, "pc", "clip", None, "10 MM",
             sub="Cable clip — grey packs, corner shelf"),
        item("8MM Concrete Wire Clips", 4, "pc", "clip", None, "8 MM",
             sub="Concrete-nail cable clip"),
        item("7MM Concrete Wire Clips", 3, "pc", "clip", None, "7 MM",
             sub="Concrete-nail cable clip"),
        item("6MM Concrete Wire Clips", 3, "pc", "clip", None, "6 MM",
             sub="Concrete-nail cable clip"),
        item("Steel Casing Pins / Nails", 145, "pkt", "nails", None, "PINS",
             sub="Casing-capping pins — packet"),
        item("5MM Link Clips", 2, "pc", "clip", None, "5 MM",
             sub="Small link clip"),
        item("Plastic Wall Plugs / Gillis", 80, "pkt", "wallplug", None, "GITTI",
             sub="Green packets — wall plug/gitti"),
    ]),
    ("Cable Ties, Holders & Flexible Cords", "#00695C", [
        item("23/76 Twin Flat Flexible Wire", 830, "coil", "coil", None, "23/76",
             sub="Twin flat flex — coil", c1="#3A3F45", c2="#C62828"),
        item("23/36 Multi-strand Flex Cord", 810, "coil", "coil", "starflex",
             "23/36", sub="STAR-FLEX green label coils — top shelf",
             c1="#2E7D32", c2="#E8E4DA"),
        item("14/36 Standard Flexible Wire", 535, "coil", "coil", "starflex",
             "14/36", sub="STAR-FLEX coils — red/yellow, top shelf",
             c1="#C62828", c2="#F2C400"),
        item("40/76 Heavy Duty Submersible Cord", 495, "coil", "coil", "hrcab",
             "40/76", sub="H.R. CAB coil — blue/red, wire shelf",
             c1="#26466D", c2="#C62828"),
        item("14/40 Super-fine Binding/Flex Wire", 215, "coil", "coil", None,
             "14/40", sub="Fine binding flex — coil", c1="#E8E4DA", c2="#C62828"),
        item("23/40 Mid-gauge Industrial Flex", 295, "coil", "coil", None,
             "23/40", sub="White & striped coils — top shelf, PRITAM rack",
             c1="#E8E4DA", c2="#26466D"),
        item("0.75 SQMM Single Core Flex Cable", 665, "coil", "coil", None,
             "0.75 SQMM", sub="Single core flex — coil", c1="#3A3F45", c2="#E8E4DA"),
        item("Industrial Polythene Roll", 650, "pkt", "polyroll", None, "POLY",
             sub="Packing polythene — per packet/roll"),
        item("2-in-1 Top Insulation Cap", 2, "pc", "caps", None, "CAP",
             sub="Insulation end caps"),
        item("Pendant Lamp Holder (Bakelite Black)", 12, "pc", "holder", None,
             "BLACK", sub="Bakelite pendant holder", body="#2B2B2B"),
        item("Pendant Lamp Holder (Standard White)", 9, "pc", "holder", None,
             "WHITE", sub="Standard pendant holder"),
        item("Angle / Batten Holder Base", 10, "pc", "angleholder", None,
             "ANGLE", sub="Angle batten holder — holder shelf"),
        item("100mm Nylon Cable Ties", 33, "pkt", "cableties", None, "100 MM",
             sub="Nylon zip ties — per packet"),
        item("150mm Nylon Cable Ties", 43, "pkt", "cableties", None, "150 MM",
             sub="Nylon zip ties — per packet"),
        item("200mm Nylon Cable Ties", 65, "pkt", "cableties", None, "200 MM",
             sub="Nylon zip ties — per packet"),
        item("250mm Nylon Cable Ties", 80, "pkt", "cableties", None, "250 MM",
             sub="Nylon zip ties — per packet"),
    ]),
    ("Joy Lights & Decorative Fittings", "#8E24AA", [
        item("Male-Female Joy Light Connectors", 4, "pc", "joyconn", None,
             "M-F", sub="Series light connectors — counter drawer"),
        item("2-in-1 Asta Plugs / Male Pins", 12, "pc", "pins2", "aastha",
             "2 PIN", sub="AASTHA red/white boxes"),
        item("Male-Female Assa Industrial Sockets", 23, "pc", "joyconn",
             "aastha", "M-F", sub="AASTHA 'Male-Female' boxes — counter shelf"),
        item("Bed Hanging Switches (Assa)", 12, "pc", "bedswitch", "aastha",
             "BED", sub="Hanging pendant switch"),
        item("Single Line (S/L) Top Plug 6A", 17, "pc", "plug", None, "6A S/L",
             sub="Single-line top plug", pins=2),
        item("Stepped Fan Regulator (Assa)", 47, "pc", "regulator", "aastha",
             "FAN REG", sub="Surface stepped regulator"),
        item("5-15A Multi-plug Universal Adaptor", 60, "pc", "multiplug",
             "florex", "5-15A", sub="FLOREX yellow-green boxes — multi-plug shelf"),
        item("Royal Hanging Bed Switches", 13, "pc", "bedswitch", None, "BED",
             sub="Royal hanging switch"),
        item("16A 3-Pin Power Top Plug", 29, "pc", "plug", None, "16A",
             sub="Power top plug"),
        item("6A 3-Pin Standard Top Plug", 29, "pc", "plug", None, "6A",
             sub="AASTHA '2 Pin Top' style boxes — counter shelf"),
        item("6-16A Combined Multi-plug Adaptor", 70, "pc", "multiplug", None,
             "6-16A", sub="Combined universal adaptor"),
    ]),
    ("Counter Stock & Extension Boards", "#2E7D32", [
        item("8-Meter Extension Flex Box (White)", 145, "pc", "extension",
             "heera", "8 MTR", sub="HEERA teal boxes — extension shelf, top right"),
        item("4-Meter Extension Flex Box (White)", 115, "pc", "extension",
             "heera", "4 MTR", sub="HEERA teal boxes — extension shelf, top right"),
        item("3-in-1 Combined Extension Cord", 160, "pc", "extension",
             "heera", "3-IN-1", sub="HEERA teal boxes / KAMLA green boxes"),
        item("Polonix Bed Switches (Heavy Duty)", 9, "pc", "bedswitch",
             "polonix", "BED", sub="POLONIX red boxes — counter shelf"),
        item("Asta Premium Pendant Holders", 20, "pc", "holder", "aastha",
             "HOLDER", sub="AASTHA holder boxes — holder shelf"),
        item("Molded Ceiling Roses (White)", 13, "pc", "ceilingrose", None,
             "2-PLATE", sub="White 'Ceiling Rose' boxes — counter shelf"),
        item("32A Dual-Source Changeover Switch", 160, "pc", "changeover", None,
             "32A", sub="Inverter/main changeover"),
        item("Digital Line Neon Testers", 25, "pc", "tester", None, "DIGITAL",
             sub="Line tester — counter jar"),
        item("Standard Insulation Line Testers", 20, "pc", "tester", None,
             "TESTER", sub="Line tester — counter jar"),
        item("6A Flush Switches (Black Series)", 12, "pc", "switch", None,
             "6A BLACK", sub="Flush piano switch, black"),
        item("6A Flush Sockets (Black Series)", 12, "pc", "socket", None,
             "6A BLACK", sub="Flush socket, black"),
        item("Counter Neon Indicator (White)", 13, "pc", "indicator", None,
             "NEON", sub="Neon indicator unit"),
        item("Counter Neon Indicator (Black)", 13, "pc", "indicator", None,
             "NEON", sub="Neon indicator unit"),
        item("6A Flush Switches (White Series)", 12, "pc", "switch", None,
             "6A WHITE", sub="Flush piano switch, white"),
        item("Parallel Wiring Blocks / Connectors", 13, "pc", "connector", None,
             "BLOCK", sub="Chocolate-block wiring connectors"),
    ]),
]

UNIT_WORD = {"pc": "per piece", "roll": "per roll", "pkt": "per packet",
             "coil": "per coil"}


# ------------------------------------------------------------------- PDF ----

NAVY = colors.HexColor("#1B2A4A")
RED = colors.HexColor("#C62828")
GREY = colors.HexColor("#5f666e")
LIGHT = colors.HexColor("#F2F4F6")

pdfmetrics.registerFont(TTFont("DVS", FONT_REG))
pdfmetrics.registerFont(TTFont("DVS-Bold", FONT_BOLD))

ST_NAME = ParagraphStyle("name", fontName="DVS-Bold", fontSize=10.5, leading=13,
                         textColor=NAVY)
ST_SUB = ParagraphStyle("sub", fontName="DVS", fontSize=8, leading=10.5,
                        textColor=GREY)
ST_PRICE = ParagraphStyle("price", fontName="DVS-Bold", fontSize=15, leading=17,
                          textColor=RED, alignment=2)
ST_UNIT = ParagraphStyle("unit", fontName="DVS", fontSize=7.5, leading=9,
                         textColor=GREY, alignment=2)
ST_H1 = ParagraphStyle("h1", fontName="DVS-Bold", fontSize=26, leading=32,
                       textColor=colors.white)
ST_BODY = ParagraphStyle("body", fontName="DVS", fontSize=10, leading=15,
                         textColor=NAVY)


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#D8DCE1"))
    canvas.setLineWidth(0.6)
    canvas.line(15 * mm, 12 * mm, A4[0] - 15 * mm, 12 * mm)
    canvas.setFont("DVS", 7.5)
    canvas.setFillColor(GREY)
    canvas.drawString(15 * mm, 8 * mm,
                      "Garia Station Electric Hub  •  Counter Sales Manual  •  "
                      "prices as per stock ledger 13-Jul-2026")
    canvas.drawRightString(A4[0] - 15 * mm, 8 * mm, f"Page {doc.page}")
    canvas.restoreState()


def cover_flowables(total_items):
    els = []
    hdr = Table(
        [[Paragraph("GARIA STATION<br/>ELECTRIC HUB", ST_H1)],
         [Paragraph("Counter Sales Manual — Customer Price List",
                    ParagraphStyle("s", fontName="DVS-Bold", fontSize=13,
                                   leading=17, textColor=colors.HexColor("#BFD4F2")))]],
        colWidths=[180 * mm])
    hdr.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), NAVY),
        ("LEFTPADDING", (0, 0), (-1, -1), 14),
        ("RIGHTPADDING", (0, 0), (-1, -1), 14),
        ("TOPPADDING", (0, 0), (0, 0), 18),
        ("BOTTOMPADDING", (0, -1), (0, -1), 18),
    ]))
    els.append(hdr)
    els.append(Spacer(0, 8 * mm))

    info = [
        ("Shop", "Near Narendra Medical Hall, Police Para, Garia Station, Kolkata"),
        ("Price list date", "13 July 2026 (from commercial inventory ledger)"),
        ("Items covered", f"{total_items} items across {len(SECTIONS)} shelf categories"),
        ("Prepared for", "Counter staff — quick rate reference for walk-in customers"),
    ]
    t = Table([[Paragraph(f"<b>{k}</b>", ST_BODY), Paragraph(v, ST_BODY)]
               for k, v in info], colWidths=[42 * mm, 138 * mm])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), LIGHT),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.white),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
    ]))
    els.append(t)
    els.append(Spacer(0, 8 * mm))

    els.append(Paragraph("How to use this manual", ParagraphStyle(
        "h2", fontName="DVS-Bold", fontSize=13, leading=16, textColor=NAVY)))
    els.append(Spacer(0, 3 * mm))
    tips = [
        "Every item shows the <b>price to charge the customer</b> (₹, per piece / "
        "roll / packet / coil). All rates are already rounded to whole rupees or "
        "convenient 5-rupee steps for fast cash and UPI handling.",
        "Profit margins (18–45%) are <b>already built into</b> these rates — do not "
        "discount below the printed price. Purchase costs are kept separately in "
        "the ledger, so this manual is safe to show or keep at the counter.",
        "Loose pieces (screws, clips, switches sold singly from a box) follow the "
        "per-piece rate printed here, even when the carton shows a pack price.",
        "Pictures are real product photographs of the brands stocked on your "
        "shelves (MAGIK, PRITAM, HEERA, RK Gold, Z4 Kabel, VELOX, Aastha and "
        "others), sourced online; a few generic loose items use a matching "
        "representative photo or illustration.",
    ]
    for tp in tips:
        els.append(Paragraph("•  " + tp, ParagraphStyle(
            "tip", parent=ST_BODY, leftIndent=6, spaceAfter=4)))
    els.append(Spacer(0, 6 * mm))

    toc_rows = []
    for i, (title, col, items) in enumerate(SECTIONS, 1):
        toc_rows.append([
            Paragraph(f"<b>{i}.</b>", ST_BODY),
            Paragraph(title, ST_BODY),
            Paragraph(f"{len(items)} items", ParagraphStyle(
                "r", parent=ST_BODY, alignment=2, textColor=GREY)),
        ])
    toc = Table(toc_rows, colWidths=[10 * mm, 135 * mm, 35 * mm])
    toc.setStyle(TableStyle([
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, colors.HexColor("#E3E7EB")),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    els.append(Paragraph("Contents", ParagraphStyle(
        "h2b", fontName="DVS-Bold", fontSize=13, leading=16, textColor=NAVY)))
    els.append(Spacer(0, 3 * mm))
    els.append(toc)
    els.append(PageBreak())
    return els


def build(outpath):
    os.makedirs(ICON_DIR, exist_ok=True)
    os.makedirs(CARD_DIR, exist_ok=True)

    # render one card per item: real product photo when available,
    # drawn illustration as fallback
    n_photo = n_icon = 0
    for si, (title, col, items) in enumerate(SECTIONS):
        for ii, it in enumerate(items):
            photo_key = ITEM_PHOTO.get(it["name"])
            photo_path = (os.path.join(PHOTO_DIR, f"{photo_key}.png")
                          if photo_key else None)
            if photo_path and os.path.exists(photo_path):
                p = os.path.join(CARD_DIR, f"s{si}_{ii}.png")
                make_photo_card(p, photo_path, it["label"])
                n_photo += 1
            else:
                p = os.path.join(ICON_DIR, f"s{si}_{ii}.png")
                make_icon(p, it["kind"], it["brand"], it["label"], **it["kw"])
                n_icon += 1
            it["icon"] = p
    print(f"rendered {n_photo} photo cards, {n_icon} icon fallbacks")

    doc = BaseDocTemplate(outpath, pagesize=A4,
                          leftMargin=15 * mm, rightMargin=15 * mm,
                          topMargin=14 * mm, bottomMargin=18 * mm,
                          title="Garia Station Electric Hub — Counter Sales Manual",
                          author="Garia Station Electric Hub")
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="f")
    doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=footer)])

    total_items = sum(len(items) for _, _, items in SECTIONS)
    story = list(cover_flowables(total_items))

    for si, (title, col, items) in enumerate(SECTIONS, 1):
        hdr = Table([[Paragraph(f"{si}.  {title}", ParagraphStyle(
            "sh", fontName="DVS-Bold", fontSize=13.5, leading=17,
            textColor=colors.white))]], colWidths=[doc.width])
        hdr.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor(col)),
            ("LEFTPADDING", (0, 0), (-1, -1), 10),
            ("TOPPADDING", (0, 0), (-1, -1), 7),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ]))

        rows = []
        for it in items:
            img = RLImage(it["icon"], width=17 * mm, height=17 * mm)
            desc = [Paragraph(it["name"], ST_NAME)]
            if it["sub"]:
                desc.append(Paragraph(it["sub"], ST_SUB))
            price = [
                Paragraph(f"₹ {it['price']:,}", ST_PRICE),
                Paragraph(UNIT_WORD[it["unit"]], ST_UNIT),
            ]
            rows.append([img, desc, price])

        t = Table(rows, colWidths=[22 * mm, doc.width - 22 * mm - 30 * mm, 30 * mm],
                  repeatRows=0)
        t.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("ROWBACKGROUNDS", (0, 0), (-1, -1), [colors.white, LIGHT]),
            ("LINEBELOW", (0, 0), (-1, -2), 0.4, colors.HexColor("#E3E7EB")),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
            ("LEFTPADDING", (0, 0), (0, -1), 2),
            ("RIGHTPADDING", (-1, 0), (-1, -1), 6),
        ]))

        # keep the banner attached to at least the first rows of its table
        story.append(KeepTogether([hdr, Spacer(0, 2 * mm)]))
        story.append(t)
        story.append(Spacer(0, 6 * mm))

    doc.build(story)
    print(f"wrote {outpath}")


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        HERE, "Garia_Electric_Hub_Counter_Sales_Manual.pdf")
    build(out)
