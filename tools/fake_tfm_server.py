"""Fake Telegram File Manager /api/v1 for testing TFM TV in the emulator.

Usage: python fake_tfm_server.py [port]   (default 8099; the emulator sees it at 10.0.2.2)
Serves media/movie.bin next to this script as every video; create it first, e.g.
python -c "import os; os.makedirs('media', exist_ok=True); open('media/movie.bin','wb').write(os.urandom(40*2**20))"
"""
import json
import os
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.path.join(HERE, "media", "movie.bin")
SIZE = os.path.getsize(MEDIA)
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099

CHANNELS = [
    {"id": 1001, "name": "Películas", "isOwner": True, "isFavorite": True, "hasDatabase": True, "videoCount": 3},
    {"id": 1002, "name": "Series", "isOwner": True, "hasDatabase": True, "videoCount": 1},
]


def video(cid, fid, name):
    return {
        "id": fid, "name": name, "path": "/", "isFile": True, "size": SIZE, "sizeText": "40 MB",
        "type": "video", "category": "video", "dateCreated": "2026-10-01T10:00:00Z", "messageId": int(fid[1:]),
        # Absolute with an internal host on purpose, like the real server behind a proxy.
        "streamUrl": f"https://192.168.0.22:8017/api/v1/channels/{cid}/files/{fid}/stream",
    }


FILES = {
    1001: [video(1001, "f1", "Big Movie (2024).mkv"), video(1001, "f2", "Otra peli.mp4"), video(1001, "f3", "Documental.avi")],
    1002: [video(1002, "f9", "Serie S01E01.mkv")],
}
refresh = {"until": 0, "result": None}
scan = {"started": 0}
watch = {}


def ok(data=None, page=None, message=None):
    body = {"success": True, "data": data}
    if page:
        body["page"] = page
    if message:
        body["message"] = message
    return body


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stdout.write("[tfm] %s %s\n" % (self.address_string(), fmt % args))
        sys.stdout.flush()

    def send_json(self, body, status=200):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head=False):
        path = self.path.split("?")[0]
        m = re.match(r"/api/v1/channels/(\d+)/files/(\w+)/stream$", path)
        if m:
            return self.stream(head)
        if path == "/api/v1/system/ping":
            return self.send_json(ok("pong"))
        if path == "/api/v1/system/info":
            return self.send_json(ok({"product": "TFM", "version": "fake", "setupComplete": True,
                                      "telegramConfigured": True, "telegramAuthenticated": True,
                                      "mongoConnected": True, "requiresApiKey": False}))
        if path == "/api/v1/auth/status":
            return self.send_json(ok({"step": "done", "isAuthenticated": True, "isConfigured": True}))
        if path == "/api/v1/channels":
            return self.send_json(ok(CHANNELS, page={"page": 1, "pageSize": 500, "totalItems": 2, "totalPages": 1}))
        if path == "/api/v1/channels/folders":
            return self.send_json(ok({"folders": [], "ungrouped": CHANNELS, "totalChannels": 2}))
        m = re.match(r"/api/v1/channels/(\d+)$", path)
        if m:
            return self.send_json(ok(next(c for c in CHANNELS if c["id"] == int(m.group(1)))))
        m = re.match(r"/api/v1/channels/(\d+)/refresh$", path)
        if m:
            return self.send_json(ok(time.time() < refresh["until"]))
        m = re.match(r"/api/v1/channels/(\d+)/refresh/result$", path)
        if m:
            return self.send_json(ok(refresh["result"]))
        m = re.match(r"/api/v1/channels/(\d+)/files(/search)?$", path)
        if m:
            cid = int(m.group(1))
            items = FILES.get(cid, [])
            if m.group(2):
                return self.send_json(ok(items, page={"page": 1, "pageSize": 500, "totalItems": len(items), "totalPages": 1}))
            return self.send_json(ok({"channelId": str(cid), "currentPath": "/", "items": items,
                                      "breadcrumbs": [{"name": "", "path": "/"}]}))
        m = re.match(r"/api/v1/channels/(\d+)/messages$", path)
        if m:
            cid = int(m.group(1))
            msgs = [{"id": int(f["id"][1:]), "date": "2026-10-01T10:00:00Z", "hasMedia": True, "mediaType": "video",
                     "fileName": f["name"], "fileSize": SIZE} for f in FILES.get(cid, [])]
            return self.send_json(ok(msgs))
        if path == "/api/v1/library/stats":
            return self.send_json(ok({"enabled": True}))
        if path == "/api/v1/library/continue":
            items = []
            for (cid, fid), w in watch.items():
                f = next(x for x in FILES[cid] if x["id"] == fid)
                items.append({"channelId": cid, "fileId": fid, "fileName": f["name"], "status": "matched", "file": f,
                              "watch": {"channelId": cid, "fileId": fid, "positionMs": w["positionMs"],
                                        "durationMs": w["durationMs"], "progress": w["positionMs"] / max(w["durationMs"], 1),
                                        "completed": w.get("completed") or False}})
            return self.send_json(ok(items))
        if path in ("/api/v1/library/items", "/api/v1/library/files"):
            return self.send_json(ok([], page={"page": 1, "pageSize": 500, "totalItems": 0, "totalPages": 1}))
        if path == "/api/v1/library/scan":
            elapsed = time.time() - scan["started"]
            running = scan["started"] > 0 and elapsed < 9
            return self.send_json(ok({"running": running, "channelsTotal": 2,
                                      "channelsScanned": min(2, int(elapsed / 4.5)),
                                      "currentChannel": "Películas" if elapsed < 4.5 else "Series",
                                      "filesSeen": 4, "filesNew": 1, "matched": 1, "review": 0, "unmatched": 0}))
        m = re.match(r"/api/v1/library/watch/(\d+)/(\w+)$", path)
        if m:
            w = watch.get((int(m.group(1)), m.group(2)))
            return self.send_json(ok({"channelId": int(m.group(1)), "fileId": m.group(2), **(w or {})} if w else None))
        return self.send_json(ok(None))

    def do_POST(self):
        path = self.path.split("?")[0]
        body = self.body()
        m = re.match(r"/api/v1/channels/(\d+)/refresh$", path)
        if m:
            if time.time() < refresh["until"]:
                return self.send_json({"success": False, "error": {"code": "already_running", "message": "busy"}}, 409)
            cid = int(m.group(1))
            print("[tfm] refresh requested", cid, body, flush=True)
            refresh["until"] = time.time() + 7

            def finish():
                time.sleep(7)
                n = len(FILES[cid]) + 1
                FILES[cid].append(video(cid, f"f{cid}{n}", f"Nuevo vídeo {n}.mp4"))
                refresh["result"] = {"channelId": str(cid), "photos": 0, "videos": 1, "audios": 0, "documents": 1, "total": 2}
            threading.Thread(target=finish, daemon=True).start()
            return self.send_json(ok(None, message="Channel refresh started"), 202)
        if path == "/api/v1/library/scan":
            print("[tfm] library scan requested", body, flush=True)
            scan["started"] = time.time()
            return self.send_json(ok(None, message="Library scan started"), 202)
        return self.send_json(ok(None))

    def do_PUT(self):
        path = self.path.split("?")[0]
        body = self.body()
        m = re.match(r"/api/v1/library/watch/(\d+)/(\w+)$", path)
        if m:
            key = (int(m.group(1)), m.group(2))
            prev = watch.get(key, {})
            watch[key] = {"positionMs": body.get("positionMs", 0),
                          "durationMs": body.get("durationMs") or prev.get("durationMs", 0),
                          "completed": body.get("completed")}
            print("[tfm] WATCH SAVE", key, watch[key], flush=True)
            return self.send_json(ok({"channelId": key[0], "fileId": key[1], **watch[key]}))
        return self.send_json(ok(None))

    def stream(self, head):
        rng = self.headers.get("Range")
        start, end = 0, SIZE - 1
        status = 200
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng)
            if m:
                if m.group(1):
                    start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), SIZE - 1)
                status = 206
        # Like the real GetFileStreamCached: open-ended ranges get at most 4 MB.
        if status == 206 or not rng:
            end = min(end, start + 4 * 1024 * 1024 - 1)
            status = 206
        self.send_response(status)
        self.send_header("Content-Type", "video/x-matroska")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{SIZE}")
        self.end_headers()
        if head:
            return
        try:
            with open(MEDIA, "rb") as fh:
                fh.seek(start)
                left = end - start + 1
                while left > 0:
                    chunk = fh.read(min(65536, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
        except (ConnectionError, OSError):
            pass


if __name__ == "__main__":
    print(f"[tfm] fake server on :{PORT}, media {SIZE} bytes", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
