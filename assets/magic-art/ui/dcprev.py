"""Local preview of a Design Component (.dc.html) with headless Chrome.
A tiny runtime (sc-for / sc-if / {{holes}} / on* handlers / DCLogic state) renders the
artboard; /_blob/<id> urls map to local files. Usage:
  python dcprev.py <file.dc.html> <out.png> [state-json] [click-selector ...]"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BLOBS = json.load(open(os.path.join(HERE, 'blobs.json'), encoding='utf-8'))
CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'

RUNTIME = r'''
function get(s, p){p=p.trim(); if(p==='true')return true; if(p==='false')return false; if(/^-?\d/.test(p))return Number(p);
 return p.split('.').reduce(function(o,k){return o==null?undefined:o[k]}, s);}
function interp(str, s){var m=str.match(/^\s*\{\{([^}]+)\}\}\s*$/); if(m)return get(s,m[1]);
 return str.replace(/\{\{([^}]+)\}\}/g,function(_,p){var v=get(s,p);return v==null?'':String(v)});}
function walk(node, s, out){
 node.childNodes.forEach(function(c){
  if(c.nodeType===3){out.appendChild(document.createTextNode(interp(c.textContent,s)));return;}
  if(c.nodeType!==1)return;
  var t=c.tagName.toLowerCase();
  if(t==='sc-for'){var l=interp(c.getAttribute('list'),s)||[];var as=c.getAttribute('as');
   l.forEach(function(it,i){var ns=Object.create(s);ns[as]=it;ns.$index=i;walk(c,ns,out);});return;}
  if(t==='sc-if'){if(interp(c.getAttribute('value'),s))walk(c,s,out);return;}
  var e=document.createElementNS(c.namespaceURI,c.localName);
  Array.from(c.attributes).forEach(function(a){
   if(/^on/.test(a.name)){var f=interp(a.value,s);if(typeof f==='function')e.addEventListener(a.name.slice(2),f);return;}
   var v=interp(a.value,s); v=v==null?'':String(v); if(a.name==='src'&&v.indexOf('/_blob/')===0&&window.BLOBS[v.slice(7)])v=window.BLOBS[v.slice(7)]; if(a.name==='style')v=v.replace(/url\(\/_blob\/([0-9a-f]+)\)/g,function(_,id){return 'url('+(window.BLOBS[id]||'')+')';}); e.setAttribute(a.name,v);});
  walk(c.content||c,s,e); out.appendChild(e);});
}
class DCLogic{constructor(p){this.props=p||{};this.state={};} setState(p){Object.assign(this.state,typeof p==='function'?p(this.state):p);render();} forceUpdate(){render();}}
var COMP;
function render(){var root=document.getElementById('root');root.innerHTML='';walk(document.getElementById('tpl').content,COMP.renderVals(),root);}
window.addEventListener('load',function(){
 COMP=new Component({}); if(window.INIT)Object.assign(COMP.state,window.INIT); render();
 (window.CLICKS||[]).forEach(function(sel,i){setTimeout(function(){var el=document.querySelectorAll(sel.q)[sel.i||0]; if(el)el.click();},300+i*300);});
});
'''


def build(src, state, clicks):
    t = open(src, encoding='utf-8').read()
    for bid, path in BLOBS.items():
        t = t.replace('/_blob/' + bid, 'file:///' + path.replace('\\', '/'))
    helmet = re.search(r'<helmet>(.*?)</helmet>', t, re.S).group(1)
    body = re.search(r'<x-dc>(.*?)</x-dc>', t, re.S).group(1)
    body = re.sub(r'<helmet>.*?</helmet>', '', body, flags=re.S)
    script = re.search(r'<script type="text/x-dc"[^>]*>(.*?)</script>', t, re.S).group(1)
    return ('<!doctype html><html><head><meta charset="utf-8">' + helmet +
            '</head><body style="margin:0"><template id="tpl">' + body + '</template><div id="root"></div>'
            '<script>window.BLOBS=' + json.dumps({k: 'file:///' + v.replace(chr(92), '/') for k, v in BLOBS.items()}) + ';window.INIT=' + json.dumps(state) + ';window.CLICKS=' + json.dumps(clicks) + ';</script>'
            '<script>' + RUNTIME + '</script><script>' + script + '</script></body></html>')


if __name__ == '__main__':
    src, out = sys.argv[1], sys.argv[2]
    state = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
    clicks = [json.loads(c) for c in sys.argv[4:]]
    html = os.path.join(HERE, '_prev.html')
    open(html, 'w', encoding='utf-8').write(build(src, state, clicks))
    w, h = 1280, 720
    m = re.search(r'"\$preview":\{"width":(\d+),"height":(\d+)\}', open(src, encoding='utf-8').read())
    if m:
        w, h = int(m.group(1)), int(m.group(2))
    budget = 1500 + 300 * len(clicks) + 3000
    subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files',
                    f'--window-size={w},{h}', f'--virtual-time-budget={budget}', f'--screenshot={os.path.abspath(out)}',
                    'file:///' + html.replace('\\', '/')], capture_output=True, timeout=120)
    print('ok' if os.path.exists(out) else 'failed')
