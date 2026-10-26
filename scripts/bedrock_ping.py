"""Ask a Bedrock server (Geyser) for its status the way a Bedrock client's server list does (RakNet unconnected ping),
without a Bedrock client. Geyser answers with the Java server's MOTD and player count when it can reach the Java
server (passthrough), so an answer also shows the whole chain is up.

    python3 scripts/bedrock_ping.py [host] [port]     # default 127.0.0.1 19132
"""
import os
import socket
import struct
import sys
import time

MAGIC = bytes.fromhex('00ffff00fefefefefdfdfdfd12345678')


def ping(host='127.0.0.1', port=19132, timeout=3.0):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(timeout)
    packet = b'\x01' + struct.pack('>q', int(time.time() * 1000)) + MAGIC + os.urandom(8)
    s.sendto(packet, (host, port))
    data, _ = s.recvfrom(4096)
    if data[0] != 0x1c: raise ValueError(f'unexpected reply {data[0]:#x}')
    length = struct.unpack('>H', data[33:35])[0]
    return data[35:35 + length].decode('utf-8', 'replace').split(';')


if __name__ == '__main__':
    host = sys.argv[1] if len(sys.argv) > 1 else '127.0.0.1'
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 19132
    fields = ping(host, port)
    names = ['edition', 'motd', 'protocol', 'version', 'players', 'max players', 'server id', 'motd 2', 'game mode', 'game mode id', 'port v4', 'port v6']
    for name, value in zip(names, fields):
        print(f'{name}: {value}')
