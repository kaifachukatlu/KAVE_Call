"""
Publishes a KAVÉ Call release on GitHub so the in-app updater offers it.

    python scripts/release.py "short notes about what changed"

- Reads versionName from app/build.gradle.kts (tag = v<versionName>).
- Uploads app/build/outputs/apk/debug/app-debug.apk as KAVE-Call.apk.
- If the release for this version already exists, it replaces the APK and notes (same version, newer build).
- The GitHub token is read from %USERPROFILE%/.config/kave/github_token (never stored in this repo)
  or from the GITHUB_TOKEN environment variable.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

REPO = "kaifachukatlu/KAVE_Call"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def token():
    t = os.environ.get("GITHUB_TOKEN")
    if t:
        return t.strip()
    p = os.path.join(os.path.expanduser("~"), ".config", "kave", "github_token")
    with open(p, encoding="utf-8") as f:
        return f.read().strip()


def api(method, url, tok, body=None, content_type="application/json"):
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": "token " + tok,
        "Accept": "application/vnd.github+json",
        "Content-Type": content_type,
        "User-Agent": "kave-release-script",
    })
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise SystemExit(f"GitHub said {e.code}: {e.read().decode()[:300]}")


def main():
    notes = sys.argv[1] if len(sys.argv) > 1 else "Improvements and fixes."
    gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    tag = "v" + version
    tok = token()
    base = f"https://api.github.com/repos/{REPO}/releases"

    rel = api("GET", f"{base}/tags/{tag}", tok)
    if rel is None:
        rel = api("POST", base, tok, {
            "tag_name": tag, "target_commitish": "main", "name": f"KAVÉ Call {version}",
            "body": notes, "draft": False, "prerelease": False,
        })
        print("created release", tag)
    else:
        api("PATCH", f"{base}/{rel['id']}", tok, {"body": notes})
        for a in rel.get("assets", []):
            if a["name"].lower().endswith(".apk"):
                api("DELETE", f"{base}/assets/{a['id']}", tok)
        print("updated release", tag)

    upload = rel["upload_url"].split("{")[0] + "?name=KAVE-Call.apk"
    with open(APK, "rb") as f:
        asset = api("POST", upload, tok, f.read(), "application/vnd.android.package-archive")
    print("uploaded", asset["name"], asset["size"], "bytes ->", rel["html_url"])


if __name__ == "__main__":
    main()
