"""Product capture worker for the specifically selected Minecraft window.

No input injection, resizing, sharpening, overlays, or desktop/monitor capture.
Agent window selection and inspection use the computer-use tools separately.
"""
import argparse
import json
import os
from pathlib import Path
import time
from windows_capture import WindowsCapture, Frame, InternalCaptureControl


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--window', required=True, help='Exact title of the selected Minecraft window')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--interval', type=float, default=0.1)
    args = parser.parse_args()
    if not args.window.startswith('Minecraft') or not 0.08 <= args.interval <= 5:
        parser.error('Select a Minecraft window and a 0.08..5 second snapshot interval')
    args.output.mkdir(parents=True, exist_ok=True)
    capture = WindowsCapture(cursor_capture=False, draw_border=None, secondary_window=False,
                             minimum_update_interval=round(args.interval * 1000),
                             monitor_index=None, window_name=args.window)
    last = 0.0
    sequence = 0

    def status(state):
        temporary = args.output / 'capture-status.tmp'
        temporary.write_text(json.dumps({'state': state, 'sequence': sequence,
                                         'interval': args.interval, 'window': args.window}), encoding='utf-8')
        os.replace(temporary, args.output / 'capture-status.json')

    @capture.event
    def on_frame_arrived(frame: Frame, control: InternalCaptureControl):
        nonlocal last, sequence
        now = time.monotonic()
        if now - last < args.interval:
            return
        last = now
        temporary = args.output / 'incoming.png'
        frame.save_as_image(str(temporary))
        try:
            os.replace(temporary, args.output / 'latest.png')
        except PermissionError:
            # Windows can briefly deny replacement while the local PNG host reads
            # the previous frame. Keep that complete frame and retry the next one.
            return
        sequence += 1
        status('capturing')
        if (args.output / 'stop').exists():
            status('stopped')
            control.stop()

    @capture.event
    def on_closed():
        status('window-closed')

    status('starting')
    try:
        capture.start()
    except Exception:
        status('failed')
        raise


if __name__ == '__main__':
    main()
