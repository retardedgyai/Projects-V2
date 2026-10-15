"""Build Grimoire4 (immersion) from Grimoire3: auto page-turn to the next opened entry after an
unlock, your own handwritten notes filling the left margin, a candle that burns down."""
f = open('project/Grimoire3.dc.html', encoding='utf-8').read()
f = f.replace('<title>魔術書 v3 見開き</title>', '<title>魔術書 v4 没頭</title>')
f = f.replace('@keyframes pulse{', '@keyframes flick{0%,100%{transform:scaleY(1)}50%{transform:scaleY(.8)}}\n.flame{animation:flick .5s steps(2) infinite;transform-origin:50% 100%}\n@keyframes noteIn{0%{opacity:0;transform:translateY(6px)}100%{opacity:1;transform:translateY(0)}}\n@keyframes pulse{')

legend_old = '<div style="position: absolute; left: 92px; top: 618px; display: flex; gap: 18px;'
assert legend_old in f
notes = '''<div style="position: absolute; left: 96px; top: 506px; width: 200px; display: flex; flex-direction: column; gap: 0">
<span style="font-size: 16px; line-height: 22px; color: #9c8460">書き込み</span>
<sc-for list="{{notes}}" as="w" hint-placeholder-count="3">
<span style="{{w.style}}">{{w.text}}</span>
</sc-for>
</div>
<div aria-hidden="true" style="position: absolute; left: 548px; top: 520px; width: 40px; height: 90px">
<span style="{{candle.wax}}"></span>
<span class="flame" style="{{candle.flame}}"></span>
<span style="position: absolute; left: 4px; bottom: 0; width: 32px; height: 6px; background: #a8582a; box-shadow: inset 0 2px 0 #c87640"></span>
</div>
'''
f = f.replace(legend_old, notes + legend_old, 1)

# own note on the right page for finished entries
f = f.replace('<div style="font-size: 16px; line-height: 24px; color: #4a3020">{{d.concept}}</div>',
              '<div style="font-size: 16px; line-height: 24px; color: #4a3020">{{d.concept}}</div>\n'
              '<sc-if value="{{d.hasNote}}" hint-placeholder-val="{{ false }}"><div style="font-size: 16px; line-height: 24px; color: #1c2a52">✎ {{d.note}}</div></sc-if>', 1)

f = f.replace("var LAB = {", """var NOTE = { n1: 'すべては相でできている', n2: '松明が二重に見えた', n3: '机は窓辺に置いた', n4: 'Lux = Aer + Ignis', n5: '火は弱めがきれい', n6: '瓶は3つ、窓辺に', n7: '16溜まるまで一晩', n8: '相の図鑑 7/12', n9: '顎で管をはさむ', n10: '引く力の強いほうへ', n11: '足もとに川がある', n12: '帰ってこられる' };
var LAB = {""", 1)

f = f.replace("d.hasGoals = d.goals.length > 0;",
              "d.note = sn.st === 'done' ? NOTE[sn.id] : ''; d.hasNote = !!d.note;\nd.hasGoals = d.goals.length > 0;", 1)

f = f.replace("return {\nnodes: nodes,", """var doneIds = N.filter(function (n) { return n.st === 'done'; }).map(function (n) { return n.id; });
var order = doneIds.filter(function (id) { return st.extra.indexOf(id) < 0; }).concat(st.extra);
var notes = order.slice(-3).map(function (id, i, arr) {
var fresh = id === st.fresh && i === arr.length - 1;
return { text: NOTE[id], style: 'font-size:16px;line-height:22px;color:#1c2a52;' + (fresh ? 'animation:noteIn .4s steps(3) both;animation-delay:700ms;' : '') };
});
var burnt = Math.min(st.extra.length * 6, 44);
var candle = {
wax: 'position:absolute;left:14px;bottom:6px;width:12px;height:' + (56 - burnt) + 'px;background:#efe2c2;box-shadow:inset -4px 0 0 #d6c6a0,inset 0 2px 0 #fff6e0;',
flame: 'position:absolute;left:16px;bottom:' + (62 - burnt) + 'px;width:8px;height:12px;background:#ffc048;box-shadow:inset 2px 4px 0 #fff4c0;'
};
return {
notes: notes, candle: candle,
nodes: nodes,""", 1)

# after an unlock, let the ink finish, then turn the page to the entry that just opened
old_unlock = "unlock: function () { if (st.extra.indexOf(sn.id) < 0) self.setState({ extra: st.extra.concat([sn.id]), fresh: sn.id, tick: st.tick + 1 }); },"
assert old_unlock in f
new_unlock = """unlock: function () {
if (st.extra.indexOf(sn.id) >= 0) return;
var ex = st.extra.concat([sn.id]);
var doneSet = function (id) { return byId[id].base === 'done' || ex.indexOf(id) >= 0; };
var next = N.filter(function (n) { return !doneSet(n.id) && n.parents.length && n.parents.every(doneSet) && !n.side; })[0]
|| N.filter(function (n) { return !doneSet(n.id) && n.parents.length && n.parents.every(doneSet); })[0];
self.setState({ extra: ex, fresh: sn.id, tick: st.tick + 1 });
if (next) setTimeout(function () { self.setState({ sel: next.id, turn: self.state.turn + 1 }); }, 1400);
},"""
f = f.replace(old_unlock, new_unlock)
open('project/Grimoire4.dc.html', 'w', encoding='utf-8').write(f)
print('ok')
