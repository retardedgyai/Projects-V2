"""Grimoire7 = Grimoire6 re-laid on the native-resolution (1:1) v7 art."""
import json
import os

f = open('project/Grimoire6.dc.html', encoding='utf-8').read()
f = f.replace('<title>魔術書 v6 魔術の窓</title>', '<title>魔術書 v7</title>')
rep = [
    # css sizes
    ('.node{position:absolute;width:56px;height:56px;', '.node{position:absolute;width:48px;height:48px;'),
    ('.node .ring{position:absolute;left:0;top:0;width:56px;height:56px}', '.node .ring{position:absolute;left:0;top:0;width:48px;height:48px}'),
    ('.node .ic{position:absolute;left:12px;top:12px;width:32px;height:32px}', '.node .ic{position:absolute;left:8px;top:8px;width:32px;height:32px}'),
    ('.tab{position:absolute;width:60px;height:60px;', '.tab{position:absolute;width:56px;height:52px;'),
    ('.sh{text-shadow:2px 2px 0 #05060c}', '.sh{text-shadow:1px 1px 0 #05060c,2px 2px 0 rgba(5,6,12,0.6)}'),
    # tabs
    ('<img class="px" src="{{t.bg}}" alt="" width="60" height="60">\n<img class="px" src="{{t.icon}}" alt="" width="28" height="28" style="{{t.iconStyle}}">',
     '<img class="px" src="{{t.bg}}" alt="" width="56" height="52">\n<img class="px" src="{{t.icon}}" alt="" width="32" height="32" style="{{t.iconStyle}}">'),
    # map window
    ('<div style="position: absolute; left: 80px; top: 124px; width: 716px; height: 552px; overflow: hidden">\n<img class="px" src="{{neb}}" alt="" width="716" height="552"',
     '<div style="position: absolute; left: 76px; top: 116px; width: 724px; height: 568px; overflow: hidden">\n<img class="px" src="{{neb}}" alt="" width="724" height="568"'),
    ('<span style="font-size: 24px; line-height: 32px; color: #f4d870">幹 ・ 基礎魔術</span>',
     '<span style="font-size: 32px; line-height: 32px; color: #f4d870">幹</span><span style="font-size: 16px; color: #f0e0b0">基礎魔術</span>'),
    ('<span style="position: absolute; left: 0; right: 0; top: 16px; text-align: center; font-size: 16px; color: #5a6278">?</span>',
     '<span style="position: absolute; left: 0; right: 0; top: 12px; text-align: center; font-size: 16px; color: #5a6278">?</span>'),
    ('src="/_blob/81a35d3c6c2d2b537c24e9578be56a13"', 'src="/_blob/a6eafa31c383f9d7d3e0c1fa86358705"'),
    # frame + plaque
    ('src="/_blob/2fc6f4a729303d93cf0077ca1295e0b5"', 'src="/_blob/119650d01a283dd485d01b93630d8410"'),
    ('<div class="sh" style="position: absolute; left: 472px; top: 76px; width: 336px; height: 40px;',
     '<div class="sh" style="position: absolute; left: 470px; top: 62px; width: 340px; height: 44px;'),
    ('<span style="font-family: \'Jacquard 12\', serif; font-size: 32px; line-height: 32px; color: #d4a838">Grimoire</span>',
     '<span style="font-family: \'Jacquard 12\', serif; font-size: 24px; line-height: 24px; color: #f0cc5a">Grimoire</span>'),
    # page
    ('<div style="position: absolute; left: 820px; top: 124px; width: 420px; height: 552px">\n<img class="px" src="/_blob/242f6a919441d3b872798214f175b65b" alt="" width="420" height="552"',
     '<div style="position: absolute; left: 820px; top: 116px; width: 408px; height: 568px">\n<img class="px" src="/_blob/ff7c2833b3defbe0ac799e7f52bba279" alt="" width="408" height="568"'),
    ('<div class="{{turn}}" style="position: absolute; left: 26px; top: 22px; right: 26px; bottom: 22px;',
     '<div class="{{turn}}" style="position: absolute; left: 24px; top: 26px; right: 24px; bottom: 26px;'),
]
for a, b in rep:
    assert a in f, a[:60]
    f = f.replace(a, b)
# ornamental dividers instead of plain rules on the page
f = f.replace('padding-top: 6px; box-shadow: inset 0 2px 0 #b8a078', 'padding-top: 14px; background: url(/_blob/9ac7959446ad11389a30683e47c00d50) no-repeat center top')
f = f.replace('padding-top: 10px; box-shadow: inset 0 2px 0 #b8a078', 'padding-top: 16px; background: url(/_blob/9ac7959446ad11389a30683e47c00d50) no-repeat center top')
# js: assets + layout
js = [
    ("var R = { done: B + '5b83ce379a86ea8b6bcf868f5dfbe2b4', open: B + '680d56e0c84c950380114a79fa30a116', locked: B + '3ad9fb7b0eb77f2bdd1dd0e6c416efdd', side: B + '40cccf3b6625b5a253b13e2408d8623a', goal: B + '38201ea9dd128fbf90ac2c07d57f709f', hidden: B + 'f4e4951fde8036971df9a2e530375896' };",
     "var R = { done: B + '9ddba6f362b9c79722cdf78517ecfd8b', open: B + '332688f4d24600d143d97997c6c5f336', locked: B + '4bb7e29104edfd1ca793e8b4aee7c581', side: B + 'b499577d4442aa1b2ad1261ad5a524a6', goal: B + 'e0ce260706c6868edf716e93d6c26532', hidden: B + '0c42df9778ae7e57a1a96e2a9eec928e' };"),
    ("var NEB = [B + '7d8cccb84476d012763919ae4b47ab52', B + 'b9e913624a1a618b1562ed77ecfc3218', B + '7b9b4f66ea65059918a8e8e9c6e5b341', B + 'f88793bf7a929aaf4908c68bd44a5b86', B + '47c06b57307f6c9c6de0c51f047e1c4c', B + '92324c7b97b9e893999b376be4bfe7d5', B + '83e236a41ae309b851edde08b843a528'];",
     "var NEB = [B + '4b8bbd97cee550d4f7f55e0f9433d1db', B + 'f30528859d08f774426a1f16a5b7be56', B + '388b8ac78b04c647ad9530769d933b8e', B + 'e4d36a55d7ea7f8deebcd59f1a8151ae', B + 'a24b1d542e83b077362724bdaaf0864b', B + 'bb6d093377dd59a1f106803d1ee73588', B + '4afbf4858b5e8db406b3e0a2a8266932'];"),
    ("desk: B + 'f8c39c51d8f6c329f450e5161082b276', distiller: B + '2ae12397a4c732bd3186b14999fc82d8', jar: B + 'ccfaf76ce2520f1efa6acd7ff7dcd0c8'",
     "desk: B + 'a78cb39eac137046918875f67d0f331a', distiller: B + '1515a70cffd15352c0990cc1dbb012da', jar: B + 'b446653a8db7f64fdd66f4014f0b7ee6'"),
    ("var OX = 28, OY = 22, SX = 86, SY = 112, S = 56;", "var OX = 40, OY = 6, SX = 90, SY = 116, S = 48;"),
    ("for (var t = 32; t < L - 31; t += 8) {", "for (var t = 30; t < L - 29; t += 8) {"),
    ("iconStyle: (n.big ? 'left:6px;top:6px;width:44px;height:44px;' : '') + (faded", "iconStyle: (faded"),
    ("labelStyle: 'position:absolute;left:-30px;right:-30px;top:60px;", "labelStyle: 'position:absolute;left:-40px;right:-40px;top:52px;"),
    ("style: 'position:absolute;left:' + (OX + c[1] * SX + 28) + 'px;top:56px;", "style: 'position:absolute;left:' + (OX + c[1] * SX + 24) + 'px;top:60px;"),
    ("pos: 'left:' + (on ? 2 : 12) + 'px;top:' + (150 + i * 70) + 'px;',", "pos: 'left:' + (on ? 0 : 8) + 'px;top:' + (148 + i * 64) + 'px;',"),
    ("iconStyle: 'position:absolute;left:10px;top:16px;'", "iconStyle: 'position:absolute;left:12px;top:10px;'"),
]
for a, b in js:
    assert a in f, a[:60]
    f = f.replace(a, b)
open('project/Grimoire7.dc.html', 'w', encoding='utf-8').write(f)
print('ok')
