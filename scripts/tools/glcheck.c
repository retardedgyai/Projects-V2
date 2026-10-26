/* Compile and link a vertex + fragment shader with this machine's OpenGL driver, headless (EGL, no window), and print
 * the driver's log. scripts/check_item_shader.py drives it, so a pack's core shader is known to build before a client
 * loads it (2026-10-25: NVIDIA refused the item shader's key arrays and the whole pack failed to load).
 *   cc -O1 -o glcheck glcheck.c -lEGL -lGL && ./glcheck shader.vsh shader.fsh */
#include <EGL/egl.h>
#include <GL/gl.h>
#include <GL/glext.h>
#include <stdio.h>
#include <stdlib.h>

static char *slurp(const char *path) {
    FILE *f = fopen(path, "rb"); if (!f) { perror(path); exit(2); }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    char *s = malloc(n + 1); fread(s, 1, n, f); s[n] = 0; fclose(f); return s;
}

static PFNGLCREATESHADERPROC CreateShader; static PFNGLSHADERSOURCEPROC ShaderSource;
static PFNGLCOMPILESHADERPROC CompileShader; static PFNGLGETSHADERIVPROC GetShaderiv;
static PFNGLGETSHADERINFOLOGPROC GetShaderInfoLog; static PFNGLCREATEPROGRAMPROC CreateProgram;
static PFNGLATTACHSHADERPROC AttachShader; static PFNGLLINKPROGRAMPROC LinkProgram;
static PFNGLGETPROGRAMIVPROC GetProgramiv; static PFNGLGETPROGRAMINFOLOGPROC GetProgramInfoLog;

static GLuint compile(GLenum type, const char *path, int *ok) {
    const char *src = slurp(path);
    GLuint s = CreateShader(type); ShaderSource(s, 1, &src, NULL); CompileShader(s);
    GLint status; GetShaderiv(s, GL_COMPILE_STATUS, &status);
    char log[16384]; GetShaderInfoLog(s, sizeof log, NULL, log);
    if (!status) { printf("COMPILE FAILED %s\n%s\n", path, log); *ok = 0; }
    return s;
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: glcheck vsh fsh\n"); return 2; }
    EGLDisplay d = eglGetDisplay(EGL_DEFAULT_DISPLAY); eglInitialize(d, NULL, NULL);
    EGLint attrs[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_NONE };
    EGLConfig c; EGLint n; eglChooseConfig(d, attrs, &c, 1, &n);
    EGLint pb[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
    EGLSurface surf = eglCreatePbufferSurface(d, c, pb);
    eglBindAPI(EGL_OPENGL_API);
    EGLint ctxa[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3,
                      EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL_NONE };
    EGLContext ctx = eglCreateContext(d, c, EGL_NO_CONTEXT, ctxa);
    if (!ctx || !eglMakeCurrent(d, surf, surf, ctx)) { printf("NO GL CONTEXT\n"); return 3; }
#define L(n) n = (void *)eglGetProcAddress("gl" #n)
    L(CreateShader); L(ShaderSource); L(CompileShader); L(GetShaderiv); L(GetShaderInfoLog); L(CreateProgram);
    L(AttachShader); L(LinkProgram); L(GetProgramiv); L(GetProgramInfoLog);
    printf("GL %s / %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));
    int ok = 1;
    GLuint v = compile(GL_VERTEX_SHADER, argv[1], &ok), f = compile(GL_FRAGMENT_SHADER, argv[2], &ok);
    if (!ok) return 1;
    GLuint p = CreateProgram(); AttachShader(p, v); AttachShader(p, f); LinkProgram(p);
    GLint status; GetProgramiv(p, GL_LINK_STATUS, &status);
    char log[16384]; GetProgramInfoLog(p, sizeof log, NULL, log);
    if (!status) { printf("LINK FAILED\n%s\n", log); return 1; }
    printf("OK\n");
    return 0;
}
