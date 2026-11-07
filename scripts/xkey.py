"""xkey.py <display> <key | click:x,y | fit:WINDOW_ID>...: press keys or click on a hidden X display (XTest), e.g. `xkey.py :5 click:466,973 F5`.
For the test client on dev_film's hidden screen only; it never touches 藍's own display."""
import ctypes, sys, time
x11 = ctypes.cdll.LoadLibrary("libX11.so.6"); xt = ctypes.cdll.LoadLibrary("libXtst.so.6")
x11.XOpenDisplay.restype = ctypes.c_void_p
d = x11.XOpenDisplay(sys.argv[1].encode())
if not d:
    sys.exit(f"no display {sys.argv[1]}")
d = ctypes.c_void_p(d)
for name in sys.argv[2:]:
    if name.startswith("fit:"):
        # the client's window to the whole screen (Xwayland's root keeps its own size; the client's does not follow)
        scr = x11.XDefaultScreen(d)
        x11.XMoveResizeWindow(d, ctypes.c_ulong(int(name[4:], 16)), 0, 0, x11.XDisplayWidth(d, scr), x11.XDisplayHeight(d, scr))
        x11.XFlush(d); time.sleep(.5)
        continue
    if name.startswith("click:"):
        x, y = (int(v) for v in name[6:].split(","))
        xt.XTestFakeMotionEvent(d, -1, x, y, 0); x11.XFlush(d); time.sleep(.15)
        xt.XTestFakeButtonEvent(d, 1, True, 0); x11.XFlush(d); time.sleep(.06)
        xt.XTestFakeButtonEvent(d, 1, False, 0); x11.XFlush(d); time.sleep(.3)
        continue
    code = x11.XKeysymToKeycode(d, x11.XStringToKeysym(name.encode()))
    xt.XTestFakeKeyEvent(d, code, True, 0); x11.XFlush(d); time.sleep(.06)
    xt.XTestFakeKeyEvent(d, code, False, 0); x11.XFlush(d); time.sleep(.25)
x11.XCloseDisplay(d)
