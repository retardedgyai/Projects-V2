"""Grimoire5 = Grimoire3 + FTB-Quests behaviour: completion happens in the world (demo link only),
unclaimed rewards show '!' and are claimed in the book, one quest can be pinned to the screen."""
import re

f = open('project/Grimoire3.dc.html', encoding='utf-8').read()
f = f.replace('<title>魔術書 v3 見開き</title>', '<title>魔術書 v5 クエストの本</title>')
f = f.replace('@keyframes pulse{', '@keyframes stamp{0%{transform:scale(1.6);opacity:0}60%{transform:scale(.95);opacity:1}100%{transform:scale(1)}}\n.stampIn{animation:stamp .35s steps(4) both}\n@keyframes pulse{')

# header: count of unclaimed rewards
old = '<span style="font-size: 16px; color: #7a5a3a">{{doneCount}} / {{total}}</span>'
assert old in f
f = f.replace(old, old + '\n<sc-if value="{{hasClaims}}" hint-placeholder-val="{{ false }}"><span style="font-size: 16px; color: #a03024">! 受け取れる報酬 {{claimCount}}</span></sc-if>')

# node badges: unclaimed reward '!' and pin
old = '<sc-if value="{{n.isGoal}}" hint-placeholder-val="{{ false }}">'
assert old in f
badges = ('<sc-if value="{{n.claim}}" hint-placeholder-val="{{ false }}"><span style="position: absolute; right: -6px; top: -6px; width: 20px; height: 20px; background: #a03024; color: #fff4e0; font-size: 16px; line-height: 20px; text-align: center; box-shadow: inset 0 0 0 2px #6a1c16">!</span></sc-if>\n'
          '<sc-if value="{{n.pinned}}" hint-placeholder-val="{{ false }}"><svg class="px" width="14" height="20" viewBox="0 0 7 10" aria-hidden="true" style="position: absolute; left: -2px; top: -10px"><rect x="1" y="0" width="5" height="4" fill="#1f6fa8"></rect><rect x="2" y="1" width="2" height="1" fill="#a8ecf6"></rect><rect x="3" y="4" width="1" height="6" fill="#4a3020"></rect></svg></sc-if>\n')
f = f.replace(old, badges + old, 1)

# right page actions
m = re.search(r'<div style="margin-top: auto; display: flex; gap: 12px; align-items: center">.*?最初から</button>\n</div>', f, re.S)
assert m
actions = '''<div style="margin-top: auto; display: flex; flex-direction: column; gap: 8px">
<div style="display: flex; gap: 12px; align-items: center">
<sc-if value="{{d.canClaim}}" hint-placeholder-val="{{ false }}">
<button type="button" class="btn" onClick="{{claim}}" style="background: #f4d870; color: #2a1a10; box-shadow: inset 0 0 0 2px #8a5a10, inset 4px 4px 0 #fff4b8">報酬を受け取る <span style="padding: 0 6px; box-shadow: inset 0 0 0 2px #8a5a10">{{d.reward}}</span></button>
</sc-if>
<sc-if value="{{d.claimed}}" hint-placeholder-val="{{ false }}">
<span class="{{stampAnim}}" style="font-size: 16px; line-height: 40px; padding: 0 12px; color: #8a5a10; box-shadow: inset 0 0 0 2px #8a5a10">受け取り済み</span>
</sc-if>
<sc-if value="{{d.canAct}}" hint-placeholder-val="{{ true }}">
<button type="button" class="btn" onClick="{{togglePin}}" style="{{d.pinStyle}}">{{d.pinLabel}}</button>
</sc-if>
<button type="button" class="btn" onClick="{{reset}}" style="margin-left: auto; background: transparent; color: #7a5a3a">最初から</button>
</div>
<sc-if value="{{d.canAct}}" hint-placeholder-val="{{ true }}">
<button type="button" onClick="{{unlock}}" style="align-self: flex-start; border: 0; background: transparent; padding: 0; min-height: 32px; font-family: 'DotGothic16', monospace; font-size: 16px; line-height: 24px; color: #9c8460; cursor: pointer; text-decoration: underline">試し：ワールドで達成させる</button>
</sc-if>
</div>'''
f = f[:m.start()] + actions + f[m.end():]

# logic
f = f.replace("this.state = { sel: 'n5', tab: 'trunk', extra: [], fresh: '', tick: 0, turn: 0 };",
              "this.state = { sel: 'n4', tab: 'trunk', extra: [], fresh: '', tick: 0, turn: 0, claimed: [], pin: 'n5', stampTick: 0 };")
f = f.replace("var LAB = {", """var CL = ['n1', 'n2', 'n3'].concat(st.claimed);
function canClaim(n) { return n.st === 'done' && !!n.reward && CL.indexOf(n.id) < 0; }
var LAB = {""", 1)
old = "pos: 'left:' + n.x + 'px;top:' + n.y + 'px;',"
assert old in f
f = f.replace(old, old + " claim: canClaim(n), pinned: st.pin === n.id && n.st !== 'done',")
old = "d.hasGoals = d.goals.length > 0;"
f = f.replace(old, """d.canClaim = canClaim(sn); d.claimed = sn.st === 'done' && !!sn.reward && !d.canClaim;
var pinnedHere = st.pin === sn.id;
d.pinLabel = pinnedHere ? 'ピン留め中' : 'ピン留めする';
d.pinStyle = pinnedHere ? 'background:#1f6fa8;color:#e6f2ee;box-shadow:inset 0 0 0 2px #14506e,inset 4px 4px 0 #49b4d9;' : 'background:#cfe4e0;color:#14506e;box-shadow:inset 0 0 0 2px #1f6fa8,inset 4px 4px 0 #e6f2ee;';
""" + old, 1)
old = "unlock: function () {"
assert old in f
f = f.replace(old, """claim: function () { if (canClaim(sn)) self.setState({ claimed: st.claimed.concat([sn.id]), stampTick: st.stampTick + 1 }); },
togglePin: function () { self.setState({ pin: st.pin === sn.id ? '' : sn.id }); },
hasClaims: N.filter(canClaim).length > 0, claimCount: N.filter(canClaim).length,
stampAnim: st.stampTick ? 'stampIn' : '',
""" + old, 1)
f = f.replace("reset: function () { self.setState({ extra: [], fresh: '', sel: 'n5',",
              "reset: function () { self.setState({ claimed: [], pin: 'n5', extra: [], fresh: '', sel: 'n4',")
open('project/Grimoire5.dc.html', 'w', encoding='utf-8').write(f)
print('ok')
