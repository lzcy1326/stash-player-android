"""Local sample library for Stash Player's Android TV emulator."""

import argparse
import json
import os
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Event, Thread
from urllib.parse import urlsplit
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
PORT = 18772
CACHE = ROOT / "build" / "verification" / "tv-demo-media"

TITLES = (
    "山间来信", "穿越云层", "湖边的早晨", "冰川之上", "潮汐之声",
    "白昼尽头", "风从北方来", "林间小路", "山谷万物", "南方海岸",
)
PHOTOS = (
    "photo-1470770841072-f978cf4d019e", "photo-1500530855697-b586d89ba3ee",
    "photo-1470252649378-9c29740c9fa8", "photo-1464822759023-fed622ff2c3b",
    "photo-1473116763249-2faaef81ccda", "photo-1469474968028-56623f02e42e",
    "photo-1500534623283-312aade485b7", "photo-1441974231531-c6227db76b6e",
    "photo-1472396961693-142e6e269027", "photo-1501785888041-af3ef285b470",
    "photo-1534528741775-53994a69daeb", "photo-1500648767791-00dcc994a43e",
)
MEDIA = {
    f"/art/{index}.jpg": f"https://images.unsplash.com/{photo}?w=840&fit=crop&q=80"
    for index, photo in enumerate(PHOTOS)
}
MEDIA["/sample.mp4"] = "https://developer.mozilla.org/shared-assets/videos/flower.mp4"

STUDIOS = [
    {"id": "1", "name": "远山影像", "image_path": "/art/3.jpg", "scene_count": 5,
     "child_studios": [{"id": "2", "name": "回声纪录"}]},
    {"id": "2", "name": "回声纪录", "image_path": "/art/7.jpg", "scene_count": 5,
     "parent_studio": {"id": "1", "name": "远山影像"}},
]
PERFORMERS = [
    {"id": "1", "name": "林晓", "image_path": "/art/10.jpg", "scene_count": 5, "country": "CN", "favorite": False},
    {"id": "2", "name": "周航", "image_path": "/art/11.jpg", "scene_count": 5, "country": "CN", "favorite": False},
]
SCENES = [
    {
        "id": str(i + 1),
        "title": title,
        "studio": STUDIOS[i % 2],
        "performers": [PERFORMERS[i % 2]],
        "paths": {"screenshot": f"/art/{i}.jpg", "stream": "/sample.mp4"},
        "files": [{"duration": 5.05, "width": 960, "height": 540}],
        "date": "2026-01-01",
        "details": "用于验收电视端的示例影片。所有卡片共用同一段短片。",
        "resume_time": 1.0 if i == 1 else 0.0,
    }
    for i, title in enumerate(TITLES)
]


def matching_page(items, field, variables):
    filters = variables.get("filter") or variables
    query = (filters.get("q") or "").casefold()
    items = [item for item in items if query in item.get("title", item.get("name", "")).casefold()]
    limit = filters.get("per_page", filters.get("perPage", 24))
    start = (filters.get("page", 1) - 1) * limit
    return {"count": len(items), field: items[start : start + limit]}


def answer(operation, variables):
    if operation == "Version":
        return {"version": {"version": "0.30.0-demo"}}
    if operation == "FindScenes":
        filters = variables.get("sceneFilter") or {}
        items = SCENES
        if criteria := filters.get("performers"):
            items = [item for item in items if item["performers"][0]["id"] in criteria["value"]]
        if criteria := filters.get("studios"):
            studio_ids = set(criteria["value"])
            if criteria.get("depth") == -1:
                studio_ids.update(
                    child["id"] for studio in STUDIOS if studio["id"] in criteria["value"]
                    for child in studio.get("child_studios", [])
                )
            items = [item for item in items if item["studio"]["id"] in studio_ids]
        if variables.get("sort") == "last_played_at":
            items = [item for item in items if item["resume_time"] > 0]
        return {"findScenes": matching_page(items, "scenes", variables)}
    if operation in ("PerformerCards", "StudioCards"):
        field, items = ("performers", PERFORMERS) if operation == "PerformerCards" else ("studios", STUDIOS)
        return {"find" + field.capitalize(): matching_page(items, field, variables)}
    if operation in ("FindScene", "PerformerDetail", "StudioDetail"):
        field, items = {
            "FindScene": ("findScene", SCENES),
            "PerformerDetail": ("findPerformer", PERFORMERS),
            "StudioDetail": ("findStudio", STUDIOS),
        }[operation]
        return {field: next((item for item in items if item["id"] == variables["id"]), None)}
    if operation in ("PerformerUpdate", "StudioUpdate"):
        field, items = ("performerUpdate", PERFORMERS) if operation == "PerformerUpdate" else ("studioUpdate", STUDIOS)
        values = variables["input"]
        item = next(item for item in items if item["id"] == values["id"])
        item.update(values)
        return {field: item}
    if operation == "SceneSaveActivity":
        return {"sceneSaveActivity": True}
    raise ValueError(f"Unsupported demo operation: {operation}")


def cache_media(entry):
    route, url = entry
    path = CACHE / route.removeprefix("/")
    if path.exists():
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with urlopen(url, timeout=12) as response:
            data = response.read()
        path.write_bytes(data)
    except OSError as error:
        print(f"Could not cache {route}: {error}", flush=True)


class DemoHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        if self.path != "/graphql":
            self.send_error(404)
            return
        try:
            request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            operation = re.search(r"\b(?:query|mutation)\s+(\w+)", request["query"])[1]
            response = {"data": answer(operation, request.get("variables") or {})}
            print(operation, flush=True)
        except (ValueError, KeyError, TypeError, StopIteration) as error:
            print(f"Demo request failed: {error}", flush=True)
            response = {"errors": [{"message": str(error)}]}
        data = json.dumps(response).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_HEAD(self):
        self.serve_media(send_body=False)

    def do_GET(self):
        self.serve_media(send_body=True)

    def serve_media(self, send_body):
        route = urlsplit(self.path).path
        path = CACHE / route.removeprefix("/")
        if route not in MEDIA or not path.is_file():
            self.send_error(404)
            return
        print(f"{self.command} {route}", flush=True)
        size = path.stat().st_size
        start, end = 0, size - 1
        requested = self.headers.get("Range")
        if requested:
            match = re.fullmatch(r"bytes=(\d+)-(\d*)", requested)
            if match:
                start = int(match[1])
                end = min(int(match[2]), end) if match[2] else end
            if not match or start > end:
                self.send_error(416)
                return
        self.send_response(206 if requested else 200)
        self.send_header("Content-Type", "video/mp4" if path.suffix == ".mp4" else "image/jpeg")
        self.send_header("Accept-Ranges", "bytes")
        if requested:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        if send_body:
            with path.open("rb") as media:
                media.seek(start)
                self.wfile.write(media.read(end - start + 1))

    def log_message(self, fmt, *args):
        pass


def find_adb():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local = ROOT / "local.properties"
    if not sdk and local.exists():
        sdk = next((line.removeprefix("sdk.dir=") for line in local.read_text().splitlines()
                    if line.startswith("sdk.dir=")), None)
    return str(Path(sdk) / "platform-tools" / "adb") if sdk else "adb"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", default="emulator-5560", help="ADB serial of the Android TV emulator")
    parser.add_argument("--apk", type=Path, help="Install this TV preview APK before opening the sample library")
    options = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", options.device):
        parser.error("--device must be an Android Emulator serial")
    adb = [find_adb(), "-s", options.device]
    subprocess.run([*adb, "get-state"], check=True)
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(cache_media, MEDIA.items()))
    with ThreadingHTTPServer(("127.0.0.1", PORT), DemoHandler) as server:
        Thread(target=server.serve_forever, daemon=True).start()
        try:
            if options.apk:
                subprocess.run([*adb, "install", "-r", str(options.apk)], check=True)
            subprocess.run([
                *adb, "shell", "am", "start", "-S", "-n",
                "gomeng.dev.stashplayer.tvpreview/gomeng.dev.stashplayer.feature.tv.StashTvActivity",
                "--ez", "demo", "true",
            ], check=True)
            print("Example library is running. Keep this window open; Ctrl+C stops it.", flush=True)
            stopped = Event()
            while not stopped.wait(1):
                pass
        except (KeyboardInterrupt, EOFError):
            pass
        finally:
            server.shutdown()


if __name__ == "__main__":
    main()
