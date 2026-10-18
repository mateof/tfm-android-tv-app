"""A fake DLNA MediaRenderer that actually pulls the video it is given.

Usage: python fake_renderer.py [--port 49152] [--refuse-direct] [--relay-port 47830]
Hosts 10.0.2.2 / 10.0.2.15 in the URI are rewritten to 127.0.0.1 so the PC can
reach the emulator's relay through `adb emu redir`.
"""
import argparse
import re
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ap = argparse.ArgumentParser()
ap.add_argument("--port", type=int, default=49152)
ap.add_argument("--refuse-direct", action="store_true")
ap.add_argument("--relay-port", type=int, default=47830)
ap.add_argument("--rate", type=int, default=400_000, help="bytes/s pulled while playing")
args = ap.parse_args()

DURATION = 600.0
lock = threading.RLock()
st = {"uri": None, "state": "NO_MEDIA_PRESENT", "pos": 0.0, "since": None, "size": None, "gen": 0, "bytes": 0}

DESC = f"""<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"><device>
<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
<friendlyName>Tele de pruebas</friendlyName><manufacturer>LG</manufacturer><modelName>FakeWebOS</modelName>
<UDN>uuid:fake-renderer-1</UDN>
<serviceList>
<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/RC/control</controlURL></service>
<service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/AVTransport/control</controlURL></service>
</serviceList></device></root>"""


def log(*a):
    print("[tv]", time.strftime("%H:%M:%S"), *a, flush=True)


def clock(s):
    s = int(s)
    return "%d:%02d:%02d" % (s // 3600, (s // 60) % 60, s % 60)


def parse_clock(v):
    h, m, s = v.split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)


def position():
    with lock:
        p = st["pos"]
        if st["state"] == "PLAYING" and st["since"]:
            p += time.time() - st["since"]
        return min(p, DURATION)


def reader(gen, start_s):
    url = re.sub(r"//(10\.0\.2\.2|10\.0\.2\.15)(:\d+)?", lambda m: "//127.0.0.1" + (m.group(2) or ""), st["uri"])
    size = st["size"]
    offset = int(size * start_s / DURATION) if size else 0
    req = urllib.request.Request(url, headers={"Range": f"bytes={offset}-", "User-Agent": "FakeWebOS/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            cr = r.headers.get("Content-Range")
            if cr and "/" in cr:
                st["size"] = int(cr.split("/")[1])
            log(f"GET {offset}- -> {r.status} {r.headers.get('Content-Type')} {cr}")
            with lock:
                if gen == st["gen"] and st["state"] == "TRANSITIONING":
                    st["state"] = "PLAYING"
                    st["since"] = time.time()
            while gen == st["gen"]:
                chunk = r.read(args.rate // 10)
                if not chunk:
                    log("end of body")
                    break
                st["bytes"] += len(chunk)
                time.sleep(0.1)
                if position() >= DURATION:
                    break
    except Exception as e:  # noqa
        log("reader error:", e)
        with lock:
            if gen == st["gen"]:
                st["state"] = "STOPPED"
                st["since"] = None
        return
    with lock:
        if gen == st["gen"] and position() >= DURATION - 1:
            st["state"] = "STOPPED"
            st["pos"] = 0
            st["since"] = None
            log("finished")


def start_reader():
    st["gen"] += 1
    threading.Thread(target=reader, args=(st["gen"], st["pos"]), daemon=True).start()


def fault(code, desc):
    return 500, f"""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>
<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
<errorCode>{code}</errorCode><errorDescription>{desc}</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""


def reply(action, inner=""):
    return 200, f"""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
<u:{action}Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">{inner}</u:{action}Response></s:Body></s:Envelope>"""


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path == "/description.xml":
            raw = DESC.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/xml")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode("utf-8", "replace")
        action = (self.headers.get("SOAPAction") or "").strip('"').split("#")[-1]
        code, text = self.soap(action, body)
        raw = text.encode()
        self.send_response(code)
        self.send_header("Content-Type", 'text/xml; charset="utf-8"')
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def soap(self, action, body):
        if action not in ("GetTransportInfo", "GetPositionInfo"):
            log("SOAP", action)
        if action == "SetAVTransportURI":
            uri = re.search(r"<CurrentURI>(.*?)</CurrentURI>", body, re.S).group(1).replace("&amp;", "&")
            log("  URI:", uri)
            if args.refuse_direct and f":{args.relay_port}/" not in uri:
                log("  refusing direct with 716")
                return fault(716, "Resource not found")
            with lock:
                st.update(uri=uri, state="STOPPED", pos=0.0, since=None, size=None)
                st["gen"] += 1
            return reply(action)
        if action == "Play":
            with lock:
                if st["uri"] is None:
                    return fault(701, "Transition not available")
                if st["state"] == "PAUSED_PLAYBACK":
                    st["state"] = "TRANSITIONING"
                else:
                    st["state"] = "TRANSITIONING"
                start_reader()
            return reply(action)
        if action == "Pause":
            with lock:
                st["pos"] = position()
                st["state"] = "PAUSED_PLAYBACK"
                st["since"] = None
                st["gen"] += 1
            return reply(action)
        if action == "Stop":
            with lock:
                st["state"] = "STOPPED"
                st["since"] = None
                st["gen"] += 1
            return reply(action)
        if action == "Seek":
            target = re.search(r"<Target>(.*?)</Target>", body).group(1)
            log("  seek to", target)
            with lock:
                st["pos"] = parse_clock(target)
                was = st["state"]
                if was == "PLAYING":
                    st["since"] = None
                    st["state"] = "TRANSITIONING"
                    start_reader()
            return reply(action)
        if action == "GetTransportInfo":
            return reply(action, f"<CurrentTransportState>{st['state']}</CurrentTransportState>"
                                 "<CurrentTransportStatus>OK</CurrentTransportStatus><CurrentSpeed>1</CurrentSpeed>")
        if action == "GetPositionInfo":
            dur = clock(DURATION) if st["uri"] else "0:00:00"
            return reply(action, f"<Track>1</Track><TrackDuration>{dur}</TrackDuration>"
                                 f"<RelTime>{clock(position())}</RelTime><AbsTime>NOT_IMPLEMENTED</AbsTime>")
        return fault(401, "Invalid Action")


log(f"fake renderer on :{args.port} (refuse direct: {args.refuse_direct})")
ThreadingHTTPServer(("0.0.0.0", args.port), H).serve_forever()
