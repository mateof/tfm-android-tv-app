"""Fake Telegram File Manager API for testing TFM TV in the emulator.

Usage: python fake_tfm_server.py [port] [--old] [--slow] [--broken]
  port      default 8099; the emulator sees it at 10.0.2.2
  --old     a server without the endpoints of 2026-11 (404): the app has to work without them
  --slow    every answer takes a second
  --broken  the streams answer 500

Serves media/movie.mkv (or .mp4, .bin) next to this script as every video. To make one with
two audio tracks and subtitles:

  ffmpeg -f lavfi -i testsrc2=s=640x360:r=24 -f lavfi -i sine=220 -f lavfi -i sine=440 -i s.srt \
    -t 180 -map 0 -map 1 -map 2 -map 3 -c:v libx264 -preset veryfast -pix_fmt yuv420p -c:a aac -c:s srt \
    -metadata:s:a:0 language=spa -metadata:s:a:1 language=eng -metadata:s:s:0 language=spa media/movie.mkv
"""
import json
import os
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, unquote, urlparse

try:
    import fake_art
except ImportError:  # no Pillow: the app shows its placeholders
    fake_art = None

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = next((p for p in (os.path.join(HERE, "media", "movie" + e) for e in (".mkv", ".mp4", ".bin")) if os.path.exists(p)), None)
SIZE = os.path.getsize(MEDIA) if MEDIA else 0
FLAGS = {a for a in sys.argv[1:] if a.startswith("--")}
PORT = next((int(a) for a in sys.argv[1:] if a.isdigit()), 8099)
OLD = "--old" in FLAGS

# Absolute with an internal host on purpose, like the real server behind a proxy.
INTERNAL = "https://192.168.0.22:8017"

CHANNELS = [
    {"id": 1001, "name": "Cine en casa", "isOwner": True, "isFavorite": True},
    {"id": 1002, "name": "Series", "isOwner": True, "isFavorite": True},
    {"id": 1003, "name": "Documentales de naturaleza", "isOwner": False, "isFavorite": True},
    {"id": 1004, "name": "Conciertos", "isOwner": False},
    {"id": 1005, "name": "Vídeos de la familia", "isOwner": True},
    {"id": 1006, "name": "Cortos", "isOwner": False},
    {"id": 1007, "name": "Archivo secreto", "isOwner": True, "isHidden": True},
]
for _c in CHANNELS:
    _c.update({"type": "channel", "hasDatabase": True, "imageUrl": f"/api/channel/image/{_c['id']}"})


def channel(cid):
    return next((c for c in CHANNELS if c["id"] == cid), None)


def video(cid, fid, name, path="/", size=None, day=1):
    message = int(re.sub(r"\D", "", fid) or 0)
    out = {
        "id": fid, "name": name, "path": path, "isFile": True, "size": size or 1_400_000_000 + message * 7_000_000,
        "sizeText": "1.4 GB", "type": os.path.splitext(name)[1], "category": "Video",
        "dateCreated": f"2026-11-{day % 28 + 1:02d}T10:00:00Z", "dateModified": f"2026-11-{day % 28 + 1:02d}T10:00:00Z",
        "messageId": message, "isSplit": False,
        "streamUrl": f"{INTERNAL}/api/file/GetFileStreamCached/{cid}/{fid}/{quote(name)}",
        "downloadUrl": f"{INTERNAL}/api/file/GetFileByTfmId/{quote(name)}?idChannel={cid}&idFile={fid}",
    }
    if not OLD:
        out.update({
            "firstMessageId": message,
            "contentUrl": f"{INTERNAL}/api/v1/channels/{cid}/files/{fid}/content",
            "playbackUrl": f"{INTERNAL}/api/v1/channels/{cid}/files/{fid}/stream",
            "thumbnailUrl": f"{INTERNAL}/api/v1/channels/{cid}/messages/{message}/thumbnail",
        })
    return out


def folder(fid, name, parent="/"):
    return {"id": fid, "name": name, "path": parent, "isFile": False, "hasChildren": True, "type": "folder", "category": "Folder"}


MOVIES = [
    ("La última marea", 2025, 8.1, ["Drama", "Aventura"], 128, "Una farera y su hija esperan el regreso del mar en un pueblo que se quedó sin él."),
    ("Órbita baja", 2024, 7.4, ["Ciencia ficción", "Suspense"], 112, "Tres técnicos quedan atrapados en una estación que nadie recuerda haber construido. Mientras el aire se acaba, descubren que alguien más firma los partes de mantenimiento."),
    ("El coleccionista de ecos", 2023, 6.9, ["Misterio"], 101, "Un afinador de campanas graba sonidos que aún no han ocurrido."),
    ("Verano en Finisterre", 2022, 7.8, ["Comedia", "Romance"], 96, "Dos desconocidos comparten por error la misma casa de alquiler."),
    ("Niebla sobre el valle", 2021, 7.1, ["Terror"], 92, "Nadie entra en el valle después de la primera nevada."),
    ("Los relojeros", 2020, 8.4, ["Drama", "Historia"], 141, "La vida de una familia que fabricó el mismo reloj durante cien años."),
    ("Planeta de papel", 2019, 7.7, ["Animación", "Familia"], 88, "Una niña dibuja un mundo y se queda a vivir en él."),
    ("Ruta 1936", 2018, 6.6, ["Aventura"], 110, "Un camión, una carta y tres mil kilómetros."),
    ("Cordillera", 2017, 7.2, ["Documental"], 84, "Un año en la montaña más alta de la península."),
    ("Silencio de radio", 2016, 6.8, ["Suspense"], 99, "Un radioaficionado capta una voz que repite su nombre."),
    ("La fábrica de nubes", 2015, 7.0, ["Animación"], 79, "En la fábrica se acabaron los días de sol."),
    ("Doce faros", 2014, 7.5, ["Drama"], 117, "Doce noches, doce faros, una promesa."),
]
SERIES = [
    ("El turno de noche", 2023, 8.6, ["Drama", "Policíaca"], 2, 8, "Una comisaría pequeña, una ciudad grande y las horas en que no pasa nada."),
    ("Puerto Escondido", 2021, 7.9, ["Comedia"], 1, 10, "Los vecinos de un puerto pesquero compran un barco que no flota."),
    ("Línea 7", 2022, 8.0, ["Ciencia ficción"], 3, 6, "Una línea de metro que para en años distintos."),
    ("Crónicas del páramo", 2019, 7.3, ["Fantasía"], 1, 8, "Nueve clanes y un solo pozo."),
    ("Fogones", 2024, 7.1, ["Documental"], 2, 5, "Cocinas que llevan un siglo encendidas."),
]


def image(kind, seed):
    return f"{INTERNAL}/api/v1/library/images/{kind}~{quote(seed)}?size={'w342' if kind == 'poster' else 'w1280'}"


FILES = {c["id"]: [] for c in CHANNELS}
watch = {(1001, "m102"): {"positionMs": 62_000, "durationMs": 180_000, "lastPlayedAt": "2026-11-25T21:00:00Z"},
         (1002, "e20103"): {"positionMs": 95_000, "durationMs": 180_000, "lastPlayedAt": "2026-11-24T21:00:00Z"},
         (1005, "v501"): {"positionMs": 40_000, "durationMs": 180_000, "lastPlayedAt": "2026-11-20T21:00:00Z"}}
for _n in (1, 2):
    watch[(1002, f"e2010{_n}")] = {"positionMs": 0, "durationMs": 180_000, "completed": True, "lastPlayedAt": "2026-11-23T21:00:00Z"}
watch[(1001, "m104")] = {"positionMs": 0, "durationMs": 180_000, "completed": True, "lastPlayedAt": "2026-11-21T21:00:00Z"}

ITEMS = {}
for _i, (_t, _y, _r, _g, _m, _o) in enumerate(MOVIES, start=1):
    _f = video(1001, f"m{100 + _i}", f"{_t} ({_y}) 1080p.mkv", "/Cine/", day=28 - _i)
    FILES[1001].append(_f)
    ITEMS[f"movie{_i}"] = {"kind": "movie", "title": _t, "year": _y, "rating": _r, "genres": _g, "runtime": _m, "overview": _o,
                           "files": [(1001, _f, None, None)], "tagline": "Nada vuelve igual." if _i == 1 else None}
_extra = video(1001, "m150", "La última marea (2025) 4K HDR.mkv", "/Cine/4K/", size=14_000_000_000, day=27)
FILES[1001].append(_extra)
ITEMS["movie1"]["files"].append((1001, _extra, None, None))
for _i, (_t, _y, _r, _g, _seasons, _eps, _o) in enumerate(SERIES, start=1):
    _files = []
    for _s in range(1, _seasons + 1):
        for _e in range(1, _eps + 1):
            _f = video(1002, f"e{_i + 1}0{_s}{_e:02d}"[:7], f"{_t} {_s}x{_e:02d}.mkv", f"/{_t}/Temporada {_s}/", day=_e + _s)
            FILES[1002].append(_f)
            _files.append((1002, _f, _s, _e))
    ITEMS[f"series{_i}"] = {"kind": "series", "title": _t, "year": _y, "rating": _r, "genres": _g, "runtime": 45, "overview": _o,
                            "files": _files, "seasons": _seasons}
for _n in range(1, 260):
    FILES[1005].append(video(1005, f"v{500 + _n}", f"Cumpleaños {_n:03d}.mp4", "/", size=30_000_000 + _n * 1000, day=_n))
for _n, _name in enumerate(("Bosques del norte", "Aves de la ría", "El lobo", "Mareas vivas"), start=1):
    FILES[1003].append(video(1003, f"d{300 + _n}", _name + ".mp4", day=_n))
REVIEW = [video(1003, "d390", "peli_rara_final_v2.avi"), video(1003, "d391", "Capitulo 3.mkv")]
# Posted in the channel but not indexed: only playable by message.
UNINDEXED = {1003: [(9001, "Recién subido.mp4"), (9002, "Sin indexar.mkv")]}

refresh = {"started": 0, "channel": None, "result": None, "canceled": False}
scan = {"started": 0, "cancelled": False, "folders": None}
SCAN_SECONDS = 12
REFRESH_SECONDS = 9


def watch_dto(cid, fid):
    w = watch.get((cid, fid))
    if not w:
        return None
    return {"channelId": cid, "fileId": fid, "positionMs": w["positionMs"], "durationMs": w["durationMs"],
            "progress": w["positionMs"] / max(w["durationMs"], 1), "completed": bool(w.get("completed")),
            "lastPlayedAt": w.get("lastPlayedAt")}


def item_dto(iid):
    it = ITEMS[iid]
    states = [watch.get((c, f["id"])) for c, f, _, _ in it["files"]]
    done = sum(1 for w in states if w and w.get("completed"))
    going = next((w for w in states if w and not w.get("completed") and w["positionMs"] > 0), None)
    series = it["kind"] == "series"
    last = max((w["lastPlayedAt"] for w in states if w and w.get("lastPlayedAt")), default=None)
    return {
        "id": iid, "kind": it["kind"], "title": it["title"], "originalTitle": None, "year": it["year"], "overview": it["overview"],
        "genres": it["genres"], "rating": it["rating"], "voteCount": 1200, "runtime": it["runtime"],
        "posterUrl": image("poster", it["title"]), "backdropUrl": image("backdrop", it["title"]),
        "provider": "tmdb", "providerId": iid, "fileCount": len(it["files"]), "channelIds": [it["files"][0][0]],
        "seasonCount": it.get("seasons", 0), "addedAt": f"{it['year']}-11-01T10:00:00Z",
        "watch": {"completed": (done == len(states)) if series else done > 0, "inProgress": going is not None,
                  "positionMs": going["positionMs"] if going else 0, "durationMs": going["durationMs"] if going else 0,
                  "progress": going["positionMs"] / going["durationMs"] if going else 0,
                  "episodesTotal": len(states) if series else 0, "episodesWatched": done if series else 0, "lastPlayedAt": last}}


def file_dto(cid, f, with_item=True):
    owner = next(((iid, s, e) for iid, it in ITEMS.items() for c, g, s, e in it["files"] if c == cid and g["id"] == f["id"]), None)
    out = {"channelId": cid, "fileId": f["id"], "fileName": f["name"], "folderPath": f["path"], "size": f["size"],
           "sizeText": f["sizeText"], "status": "matched" if owner else "unmatched",
           "parsed": {"title": os.path.splitext(f["name"])[0]}, "file": f, "watch": watch_dto(cid, f["id"])}
    if owner:
        out.update({"itemId": owner[0], "kind": ITEMS[owner[0]]["kind"], "season": owner[1], "episode": owner[2]})
        if with_item:
            out["item"] = item_dto(owner[0])
    return out


def item_detail(iid):
    it = ITEMS[iid]
    out = item_dto(iid)
    out["tagline"] = it.get("tagline")
    if it["kind"] == "movie":
        out["files"] = [file_dto(c, f, False) for c, f, _, _ in it["files"]]
        out["resume"] = next((f for f in out["files"] if f["watch"] and f["watch"]["positionMs"] > 0 and not f["watch"]["completed"]), None)
        return out
    seasons, flat = [], []
    for s in range(1, it["seasons"] + 1):
        episodes = []
        for c, f, season, e in it["files"]:
            if season != s:
                continue
            w = watch_dto(c, f["id"])
            episodes.append({"season": s, "number": e, "title": f"Capítulo {e}: {['El aviso', 'Horas bajas', 'La llave', 'Dos puertas'][e % 4]}",
                             "overview": "Lo que empezó como un favor se complica cuando aparece una segunda versión de la misma historia.",
                             "stillUrl": image("still", f"{it['title']} {s}{e}"), "airDate": f"2023-0{s}-{e + 10}", "runtime": 45,
                             "rating": 8.0, "files": [file_dto(c, f, False)], "watch": w, "completed": bool(w and w["completed"])})
        flat += episodes
        seasons.append({"number": s, "name": f"Temporada {s}", "episodeCount": len(episodes), "episodes": episodes,
                        "posterUrl": image("poster", f"{it['title']} {s}"), "episodesWatched": sum(e["completed"] for e in episodes)})
    out["seasons"] = seasons
    going = next((e for e in flat if e["watch"] and not e["completed"] and e["watch"]["positionMs"] > 0), None)
    out["nextUp"] = going or next((e for e in flat if not e["completed"]), None)
    out["resume"] = going["files"][0] if going else None
    return out


def file_of(cid, fid):
    return next((f for f in FILES.get(cid, []) if f["id"] == fid), None)


def ok(data=None, page=None, message=None):
    body = {"success": True, "data": data}
    if page:
        body["page"] = page
    if message:
        body["message"] = message
    return body


def error(code, message):
    return {"success": False, "error": {"code": code, "message": message}}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stdout.write("[tfm] %s\n" % (fmt % args))
        sys.stdout.flush()

    def send_json(self, body, status=200):
        if "--slow" in FLAGS:
            time.sleep(1)
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)

    def send_art(self, kind, seed):
        if not fake_art:
            return self.send_json(error("not_found", "no art"), 404)
        raw = fake_art.art(kind, seed)
        self.send_response(200)
        self.send_header("Content-Type", "image/jpeg")
        self.send_header("Cache-Control", "private, max-age=604800")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def paged(self, items):
        page, size = int(self.q.get("page", 1)), int(self.q.get("pageSize", 50))
        part = items[(page - 1) * size:page * size]
        return part, {"page": page, "pageSize": size, "totalItems": len(items),
                      "totalPages": (len(items) + size - 1) // size, "hasNext": page * size < len(items), "hasPrevious": page > 1}

    def sort(self, files):
        key = {"date": "dateModified", "size": "size"}.get(self.q.get("sortBy"), "name")
        return sorted(files, key=lambda f: (f[key].lower() if isinstance(f[key], str) else f[key]),
                      reverse=self.q.get("sortDescending") == "true")

    def parse(self):
        u = urlparse(self.path)
        self.q = {k: v[0] for k, v in parse_qs(u.query).items()}
        return u.path

    def missing(self):
        self.send_json(error("not_found", "Not found"), 404)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = self.parse()
        if re.match(r"/api/file/GetFileStreamCached/", path) or re.match(r"/api/v1/channels/\d+/(files/\w+|messages/\d+)/content$", path):
            if OLD and "/api/file/" not in path:
                return self.missing()
            return self.stream()
        m = re.match(r"/api/v1/channels/(\d+)/messages/(\d+)/thumbnail$", path)
        if m:
            if OLD or int(m.group(2)) % 7 == 0:  # some videos have no preview
                return self.missing()
            return self.send_art("thumb", m.group(2))
        m = re.match(r"/api/v1/channels/(\d+)/image$", path)
        if m:
            return self.send_art("avatar", m.group(1)) if m.group(1) != "1006" else self.missing()
        m = re.match(r"/api/v1/library/images/(\w+)~(.+)$", path)
        if m:
            return self.send_art("poster" if m.group(1) == "poster" else "backdrop", unquote(m.group(2)))
        if path == "/api/mobile/capabilities":
            if OLD:
                return self.missing()
            return self.send_json({"success": True, "data": {"apiVersion": 1, "serverVersion": "3.9.0", "features": [
                "search.v1", "files.thumbnails", "files.content", "messages.content", "messages.browse",
                "channels.refresh", "channels.refreshProgress", "library.scanFolders"]}})
        if path == "/api/v1/system/ping":
            return self.send_json(ok("pong"))
        if path == "/api/v1/system/info":
            return self.send_json(ok({"product": "TFM", "version": "fake", "setupComplete": True, "telegramConfigured": True,
                                      "telegramAuthenticated": True, "mongoConnected": True, "requiresApiKey": False}))
        if path == "/api/v1/auth/status":
            return self.send_json(ok({"step": "ok", "isAuthenticated": True, "isConfigured": True}))
        if path == "/api/v1/channels":
            hidden = self.q.get("includeHidden") == "true"
            part, page = self.paged([c for c in CHANNELS if hidden or not c.get("isHidden")])
            return self.send_json(ok(part, page))
        if path == "/api/v1/channels/folders":
            return self.send_json(ok({"folders": [
                {"id": 2, "title": "Cine y series", "iconEmoji": "🎬", "channels": CHANNELS[:2], "channelCount": 2},
                {"id": 3, "title": "Otros", "iconEmoji": "📁", "channels": CHANNELS[2:6], "channelCount": 4}],
                "ungrouped": [], "totalChannels": len(CHANNELS)}))
        if path == "/api/v1/search" and not OLD:
            q = self.q.get("q", "").lower()
            groups = [{"channelId": str(c["id"]), "channelName": c["name"], "files": [f for f in FILES[c["id"]] if q in f["name"].lower()][:50]}
                      for c in CHANNELS if not c.get("isHidden")]
            return self.send_json(ok({"channels": [g for g in groups if g["files"]], "local": [], "truncated": False}))
        m = re.match(r"/api/v1/channels/(\d+)(/.*)?$", path)
        if m:
            return self.get_channel(int(m.group(1)), m.group(2) or "")
        if path.startswith("/api/v1/library/"):
            return self.get_library(path[len("/api/v1/library/"):])
        return self.missing()

    def get_channel(self, cid, rest):
        c = channel(cid)
        if not c:
            return self.missing()
        files = FILES[cid]
        if rest == "":
            return self.send_json(ok({**c, "fileCount": len(files), "videoCount": len(files), "totalSize": sum(f["size"] for f in files),
                                      "totalSizeText": "214 GB", "isRefreshing": self.refreshing(cid), "canRefresh": True}))
        if rest == "/refresh":
            return self.send_json(ok(self.refreshing(cid)))
        if rest == "/refresh/progress":
            if OLD:
                return self.missing()
            if not self.refreshing(cid):
                return self.send_json(ok(None))
            t = (time.time() - refresh["started"]) / REFRESH_SECONDS
            return self.send_json(ok({"running": True, "phase": "searching" if t < 0.7 else "indexing", "filters": [
                {"type": "documents", "fetched": int(400 * t), "total": 1250, "done": t > 0.6},
                {"type": "video", "fetched": int(800 * t), "total": 8031, "done": False}],
                "newFiles": 2 if t > 0.7 else 0, "indexed": 1 if t > 0.85 else 0, "cancelRequested": False}))
        if rest == "/refresh/result":
            return self.send_json(ok(refresh["result"]))
        if rest == "/files/search":
            q = self.q.get("q", "").lower()
            part, page = self.paged(self.sort([f for f in files if q in f["name"].lower()]))
            return self.send_json(ok(part, page))
        if rest == "/files":
            return self.browse(cid, files)
        m = re.match(r"/files/(\w+)$", rest)
        if m:
            f = file_of(cid, m.group(1))
            return self.send_json(ok(f)) if f else self.missing()
        msgs = [{"id": f["messageId"], "date": f["dateModified"], "hasMedia": True, "mediaType": "video", "fileName": f["name"],
                 "fileSize": f["size"], "duration": 180 + f["messageId"] % 90 * 60, "text": ""} for f in files]
        msgs += [{"id": i, "date": "2026-11-25T18:00:00Z", "hasMedia": True, "mediaType": "video", "fileName": n, "fileSize": 90_000_000,
                  "duration": 754, "text": "Subido hoy"} for i, n in UNINDEXED.get(cid, [])]
        msgs.sort(key=lambda x: -x["id"])
        if OLD:
            for x in msgs:
                x.pop("duration")
        if rest == "/messages":
            start, limit = int(self.q.get("offset", 0)), int(self.q.get("limit", 30))
            return self.send_json(ok(msgs[start:start + limit]))
        if rest == "/messages/search" and not OLD:
            q, off, limit = self.q.get("q", "").lower(), int(self.q.get("offsetId", 0)), int(self.q.get("limit", 50))
            hits = [x for x in msgs if q in x["fileName"].lower()]
            part = [x for x in hits if not off or x["id"] < off][:limit]
            more = bool(part) and part[-1]["id"] != hits[-1]["id"]
            return self.send_json(ok({"items": part, "total": len(hits), "nextOffsetId": part[-1]["id"] if more else None}))
        if rest == "/messages/counts" and not OLD:
            return self.send_json(ok({"videos": len(msgs)}))
        return self.missing()

    def browse(self, cid, files):
        where = self.q.get("path", "/")
        if self.q.get("folderId"):
            where = unquote(bytes.fromhex(self.q["folderId"]).decode())
        depth = where.count("/")
        subs = sorted({"/".join(f["path"].split("/")[:depth + 1]) + "/" for f in files if f["path"].startswith(where) and f["path"] != where})
        dirs = [folder(s.encode().hex(), s.rstrip("/").split("/")[-1], where) for s in subs]
        part, page = self.paged(dirs + self.sort([f for f in files if f["path"] == where]))
        parts = [p for p in where.split("/") if p]
        crumbs = [{"name": "Files", "path": "/"}] + [{"name": p, "path": "/" + "/".join(parts[:i + 1]) + "/"} for i, p in enumerate(parts)]
        parent = None if where == "/" else "/" + "".join(p + "/" for p in parts[:-1])
        return self.send_json(ok({"channelId": str(cid), "currentPath": where,
                                  "currentFolderId": None if where == "/" else where.encode().hex(),
                                  "parentFolderId": None if parent in (None, "/") else parent.encode().hex(), "parentPath": parent,
                                  "folderName": parts[-1] if parts else "", "items": part, "breadcrumbs": crumbs}, page))

    @staticmethod
    def refreshing(cid):
        return refresh["channel"] == cid and not refresh["canceled"] and time.time() - refresh["started"] < REFRESH_SECONDS

    def get_library(self, rest):
        if rest == "stats":
            return self.send_json(ok({"enabled": True, "ready": True, "movies": len(MOVIES), "series": len(SERIES)}))
        if rest == "continue":
            going = sorted(((k, w) for k, w in watch.items() if not w.get("completed") and w["positionMs"] > 0),
                           key=lambda x: x[1].get("lastPlayedAt") or "", reverse=True)
            return self.send_json(ok([file_dto(c, file_of(c, f)) for (c, f), _ in going if file_of(c, f)]))
        if rest == "recent":
            kind = self.q.get("kind")
            items = sorted((item_dto(i) for i in ITEMS if not kind or ITEMS[i]["kind"] == kind), key=lambda x: x["addedAt"], reverse=True)
            return self.send_json(ok(items[:int(self.q.get("limit", 30))]))
        if rest == "items":
            kind, q = self.q.get("kind"), self.q.get("search", "").lower()
            part, page = self.paged([item_dto(i) for i in ITEMS if (not kind or ITEMS[i]["kind"] == kind) and q in ITEMS[i]["title"].lower()])
            return self.send_json(ok(part, page))
        m = re.match(r"items/(\w+)$", rest)
        if m:
            return self.send_json(ok(item_detail(m.group(1)))) if m.group(1) in ITEMS else self.missing()
        if rest == "files":
            status = self.q.get("status")
            files = [file_dto(1003, f) | {"status": status} for f in REVIEW[:1 if status == "review" else 2][-1:]] if status in ("review", "unmatched") else []
            part, page = self.paged(files)
            return self.send_json(ok(part, page))
        m = re.match(r"files/(\d+)/(\w+)$", rest)
        if m:
            f = file_of(int(m.group(1)), m.group(2))
            return self.send_json(ok(file_dto(int(m.group(1)), f))) if f else self.missing()
        if rest == "scan":
            t = (time.time() - scan["started"]) / SCAN_SECONDS
            running = scan["started"] > 0 and t < 1 and not scan["cancelled"]
            state = {"running": running, "cancelled": scan["cancelled"], "channelsTotal": 5, "channelsScanned": min(5, int(t * 10)),
                     "currentChannel": "Series" if t < 0.4 else None, "filesSeen": 320, "filesNew": 12, "matched": 10, "review": 1, "unmatched": 1}
            if not OLD:
                state.update({"filesToProcess": 12 if t > 0.5 else 0, "filesProcessed": min(12, max(0, int((t - 0.5) * 24)))})
            return self.send_json(ok(state if scan["started"] else None))
        if rest == "providers/search":
            return self.send_json(ok([{"provider": "tmdb", "providerId": str(n), "kind": self.q.get("kind") or "movie", "title": t, "year": y,
                                       "overview": o, "posterUrl": image("poster", t)} for n, (t, y, _, _, _, o) in enumerate(MOVIES[:5])]))
        m = re.match(r"watch/(\d+)/(\w+)$", rest)
        if m:
            return self.send_json(ok(watch_dto(int(m.group(1)), m.group(2))))
        return self.missing()

    def do_POST(self):
        path, body = self.parse(), self.body()
        m = re.match(r"/api/v1/channels/(\d+)/(favorite|hidden|refresh)$", path)
        if m:
            cid, what = int(m.group(1)), m.group(2)
            if what == "favorite":
                channel(cid)["isFavorite"] = True
                return self.send_json(ok())
            if what == "hidden":
                channel(cid)["isHidden"] = True
                return self.send_json(ok())
            if self.refreshing(cid):
                return self.send_json(error("already_running", "busy"), 409)
            print("[tfm] refresh requested", cid, body, flush=True)
            refresh.update({"started": time.time(), "channel": cid, "canceled": False, "result": None})

            def finish():
                time.sleep(REFRESH_SECONDS)
                if refresh["canceled"]:
                    return
                FILES[cid].append(video(cid, f"n{8000 + len(FILES[cid])}", f"Nuevo vídeo {len(FILES[cid])}.mp4", day=27))
                refresh["result"] = {"channelId": str(cid), "videos": 1, "documents": 1, "total": 2}
            threading.Thread(target=finish, daemon=True).start()
            return self.send_json(ok(None, message="Channel refresh started"), 202)
        if path == "/api/v1/library/scan":
            print("[tfm] library scan requested", body, flush=True)
            if scan["started"] and time.time() - scan["started"] < SCAN_SECONDS and not scan["cancelled"]:
                return self.send_json(error("already_running", "busy"), 409)
            scan.update({"started": time.time(), "cancelled": False})
            return self.send_json(ok(None, message="Library scan started"), 202)
        return self.set_watched(path, True)

    def do_DELETE(self):
        path = self.parse()
        m = re.match(r"/api/v1/channels/(\d+)/(favorite|hidden|refresh)$", path)
        if m:
            cid, what = int(m.group(1)), m.group(2)
            if what == "refresh":
                refresh["canceled"] = True
                refresh["result"] = {"channelId": str(cid), "canceled": True, "videos": 0, "total": 0}
                return self.send_json(ok(), 202)
            channel(cid)["isFavorite" if what == "favorite" else "isHidden"] = False
            return self.send_json(ok())
        if path == "/api/v1/library/scan":
            scan["cancelled"] = True
            return self.send_json(ok())
        m = re.match(r"/api/v1/library/watch/(\d+)/(\w+)$", path)
        if m:
            watch.pop((int(m.group(1)), m.group(2)), None)
            return self.send_json(ok())
        return self.set_watched(path, False)

    def set_watched(self, path, value):
        def mark(cid, fid):
            watch[(cid, fid)] = {"positionMs": 0, "durationMs": 180_000, "completed": value, "lastPlayedAt": "2026-11-25T22:00:00Z"}
        m = re.match(r"/api/v1/library/watch/(\d+)/(\w+)/watched$", path)
        if m:
            mark(int(m.group(1)), m.group(2))
            return self.send_json(ok(watch_dto(int(m.group(1)), m.group(2))))
        m = re.match(r"/api/v1/library/items/(\w+)/watched$", path)
        if m:
            for c, f, _, _ in ITEMS[m.group(1)]["files"]:
                mark(c, f["id"])
            return self.send_json(ok(item_detail(m.group(1))))
        return self.send_json(ok(None))

    def do_PUT(self):
        path, body = self.parse(), self.body()
        m = re.match(r"/api/v1/library/watch/(\d+)/(\w+)$", path)
        if m:
            key = (int(m.group(1)), m.group(2))
            prev = watch.get(key, {})
            duration = body.get("durationMs") or prev.get("durationMs", 0)
            done = bool(body.get("completed")) or (duration > 0 and body.get("positionMs", 0) / duration > 0.92)
            watch[key] = {"positionMs": 0 if done else body.get("positionMs", 0), "durationMs": duration, "completed": done,
                          "lastPlayedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
            print("[tfm] WATCH SAVE", key, watch[key], flush=True)
            return self.send_json(ok(watch_dto(*key)))
        return self.send_json(ok(None))

    def stream(self):
        if "--broken" in FLAGS or not MEDIA:
            return self.send_json(error("internal_error", "broken"), 500)
        rng = self.headers.get("Range")
        start, end = 0, SIZE - 1
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng)
            if m:
                start = int(m.group(1) or 0)
                end = min(int(m.group(2)), SIZE - 1) if m.group(2) else end
        # Like the real GetFileStreamCached: open-ended ranges get at most 4 MB.
        end = min(end, start + 4 * 1024 * 1024 - 1)
        self.send_response(206)
        self.send_header("Content-Type", "video/x-matroska")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Content-Range", f"bytes {start}-{end}/{SIZE}")
        self.end_headers()
        if self.command == "HEAD":
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
    print(f"[tfm] fake server on :{PORT}, media {MEDIA} ({SIZE} bytes) {' '.join(FLAGS)}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
