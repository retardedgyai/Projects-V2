"""Click inside the Minecraft (XWayland) window: python3 xclick.py <root_x> <root_y>"""
import ctypes, sys, time
x11 = ctypes.CDLL("libX11.so.6"); xt = ctypes.CDLL("libXtst.so.6")
x11.XOpenDisplay.restype = ctypes.c_void_p
d = x11.XOpenDisplay(b":0")
x, y = int(sys.argv[1]), int(sys.argv[2])
xt.XTestFakeMotionEvent(ctypes.c_void_p(d), -1, x, y, 0); x11.XFlush(ctypes.c_void_p(d)); time.sleep(.3)
xt.XTestFakeButtonEvent(ctypes.c_void_p(d), 1, 1, 0); x11.XFlush(ctypes.c_void_p(d)); time.sleep(.1)
xt.XTestFakeButtonEvent(ctypes.c_void_p(d), 1, 0, 0); x11.XFlush(ctypes.c_void_p(d))
x11.XCloseDisplay(ctypes.c_void_p(d))
