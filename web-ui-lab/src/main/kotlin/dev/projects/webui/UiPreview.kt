package dev.projects.webui

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/** Local, read-only authoring aid. Browser actions operate a separate demo, never a Minecraft account. */
class UiPreview(private val source: Path, port: Int) : AutoCloseable {
    private val http=HttpServer.create(InetSocketAddress("127.0.0.1",port),0)
    init {
        http.createContext("/") { e ->
            e.use {
                if(e.requestMethod!="GET" || e.requestURI.path!="/") { e.sendResponseHeaders(404,-1);return@use }
                try {
                    val raw=Files.readString(source)
                    val doc=UiDocument.parse(raw)
                    val demo=ForgeDemo()
                    val params=e.requestURI.rawQuery.orEmpty().split('&').map { it.split('=',limit=2) }
                    if(params.any { it==listOf("tab","catalog") }) demo.action("tab:catalog")
                    if(params.any { it==listOf("page","2") }) demo.action("page:next")
                    if(params.any { it==listOf("state","poor") }) demo.action("forge")
                    doc.layout(demo.values(),demo.flags())
                    var html=Regex("\\{\\{([a-zA-Z0-9_-]+)}}").replace(raw) { escape(demo.values().getValue(it.groupValues[1])) }
                    val hidden=Regex("data-if=\"([^\"]+)\"").replace(html) {
                        val condition=it.groupValues[1]
                        val visible=if(condition.startsWith("!")) condition.drop(1) !in demo.flags() else condition in demo.flags()
                        if(visible) "" else "hidden=\"hidden\""
                    }
                    html=hidden.replace("</head>","""
                        <style>
                        *{box-sizing:border-box;flex-shrink:0} body{margin:0;background:#101419;min-height:100vh;display:grid;place-content:center;font-family:system-ui,sans-serif}
                        main{box-shadow:0 18px 80px #0008} p{margin:0;display:flex;align-items:center;white-space:nowrap}button{border:0;font:inherit;color:inherit;cursor:pointer;padding:0}
                        [hidden]{display:none!important}[data-action]:hover{background-color:#866744} [data-item]{display:grid;place-items:center;line-height:1;overflow:hidden;color:#e7bc75}
                        [data-enabled]:disabled{background:#35383c;color:#91969b;cursor:not-allowed}.preview-note{width:800px;color:#abb4be;font-size:13px;margin:16px 0;line-height:1.8}
                        a{color:#e7bc75}
                        </style></head>
                    """.trimIndent()).replace("</body>","""
                        <aside class="preview-note">HTML/CSSのブラウザプレビュー（Minecraft画面ではありません）。アイテムは仮記号、日本語の字形はゲームと異なります。<br/>
                        <a href="/">強化</a> · <a href="/?state=poor">素材不足</a> · <a href="/?tab=catalog">素材見本1</a> · <a href="/?tab=catalog&amp;page=2">素材見本2</a><br/>
                        保存後このページを更新。ゲーム側は「HTMLを再読込」。下のテスト制御はゲーム内専用です。</aside>
                        <script>
                        const flags=${demo.flags().joinToString(",","[","]") { "\"$it\"" }};
                        document.querySelectorAll('main *').forEach(e=>{if(getComputedStyle(e).flexGrow==='1'){e.style.flexBasis='0px';e.style.minWidth='0px';e.style.minHeight='0px';}});
                        document.querySelectorAll('main p').forEach(e=>{const a=getComputedStyle(e).textAlign;e.style.justifyContent=a==='center'?'center':a==='right'?'flex-end':'flex-start';});
                        document.querySelectorAll('[data-item]').forEach(e=>e.style.fontSize=Math.min(e.clientWidth,e.clientHeight)*0.7+'px');
                        document.querySelectorAll('[data-enabled]').forEach(e=>e.disabled=!flags.includes(e.dataset.enabled));
                        document.querySelectorAll('[data-action]').forEach(e=>e.onclick=()=>{const a=e.dataset.action;
                        if(a==='tab:forge')location.href='/';else if(a==='tab:catalog')location.href='/?tab=catalog';
                        else if(a.startsWith('page:'))location.href='/?tab=catalog&page=${if(demo.page==0) 2 else 1}';
                        else if(a==='forge')location.href='/?state=poor';else if(a==='reset')location.href='/';else if(a==='reload')location.reload();});
                        </script></body>
                    """.trimIndent())
                    val bytes=html.toByteArray(Charsets.UTF_8)
                    e.responseHeaders.add("Content-Type","text/html; charset=utf-8")
                    e.responseHeaders.add("Cache-Control","no-store")
                    e.responseHeaders.add("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
                    e.sendResponseHeaders(200,bytes.size.toLong());e.responseBody.write(bytes)
                } catch(ex: Exception) {
                    val bytes=("UI validation failed: "+ex.message).toByteArray(Charsets.UTF_8)
                    e.sendResponseHeaders(422,bytes.size.toLong());e.responseBody.write(bytes)
                }
            }
        }
        http.start()
    }
    override fun close() { http.stop(0) }
    companion object { fun escape(s: String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;") }
}
