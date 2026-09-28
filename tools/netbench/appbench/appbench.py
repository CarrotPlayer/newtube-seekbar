#!/usr/bin/env python3
"""In-app source benchmark (netbench phase 1).

Opens each (source, video) cell in the side-by-side benchmark build of NewTube
(io.github.aleixrodriala.arc.check: its own data, no account) with the source forced through
debug.arc.player_client, lets it play like a user would for --play-s seconds (with the app's own
auto-seek from debug.arc.bench_seek), then reads the app's NetPath lines and writes one JSON line
per open: what /player answered, what the loader chose, first frame, how far playback got
(bench-tick), the seek, and any error.

Pixel hard rules: every intent is preceded by guard.sh start (focus is the check app, the launcher
or the owner's idle NewTube; no shade; awake; no call). While a cell plays, the focus must stay on
the check app; if anything else takes it (the owner picked the phone up), the cell is aborted and
the run stops. Intents always name the check package. No logcat -c. Media volume is set to 0 for
the run and restored after.

Usage:
  NETBENCH_SERIAL=<adb serial> appbench.py --network lte --sources TV_TIZEN,WEB_EMBED \
      --videos _WB5hh7WOb4,dQw4w9WgXcQ [--support-xhr none|true|false|absent] [--repeat 1] \
      [--play-s 150] [--seek 90:0.7] [--data DIR] --run-id X
The phone comes from --serial or NETBENCH_SERIAL (no default: it refuses to run without one), the
guard is ../device/guard.sh, and results go to <data>/appbench/results (--data or NETBENCH_DATA,
default: the tools/netbench directory).
"""
import argparse
import json
import os
import re
import signal
import subprocess
import sys
import time

SERIAL = None  # --serial or NETBENCH_SERIAL; main() refuses to run without one
PKG = "io.github.aleixrodriala.arc.check"
HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(os.path.dirname(HERE), "device", "guard.sh")
DEFAULT_DATA = os.path.dirname(HERE)  # tools/netbench
RESULTS = os.path.join(DEFAULT_DATA, "appbench", "results")  # main(): <data>/appbench/results


def adb(*args, check=False, timeout=60):
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {r.stderr.decode(errors='replace')}")
    return r.stdout.decode(errors="replace")


def shell(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


CURRENT_LOGCAT = None
KEPT_STARTED = [False]
NO_GUARD = False  # only for my own emulator (--serial emulator-*): nobody else uses it


def guard(mode="start"):
    if NO_GUARD:
        return True, "no guard (own emulator)"
    # The guard checks the same phone this run drives.
    r = subprocess.run([GUARD, mode], capture_output=True, timeout=60,
                       env={**os.environ, "NETBENCH_SERIAL": SERIAL})
    return r.returncode == 0, r.stdout.decode(errors="replace").strip()


def focus():
    out = shell("dumpsys window | grep -m1 mCurrentFocus")
    return out.strip()


def setprop(key, value):
    # Android can't reliably clear a property; the app treats "none" as unset.
    shell(f"setprop {key} {value if value else 'none'}")


def device_time():
    # logcat -T wants the device's own clock: 'MM-DD hh:mm:ss.mmm'.
    return shell("date +'%m-%d %H:%M:%S.000'").strip()


def media_volume():
    out = shell("cmd media_session volume --stream 3 --get")
    m = re.search(r"volume is (\d+)", out)
    return int(m.group(1)) if m else None


def set_media_volume(v):
    shell(f"cmd media_session volume --stream 3 --set {v}")



def extra_props(args):
    """--prop KEY=VALUE switches, only debug.arc.* keys."""
    props = {}
    for item in getattr(args, "prop", None) or []:
        key, sep, value = item.partition("=")
        if not sep or not key.startswith("debug.arc.") or not re.fullmatch(r"[A-Za-z0-9_.]+", key) \
                or not re.fullmatch(r"[A-Za-z0-9_.:-]*", value):
            raise SystemExit(f"bad --prop {item!r}")
        props[key] = value
    return props

def parse(lines, video):
    """Turn the NetPath lines of one open into a verdict."""
    res = {"results": [], "winner": None, "info": None, "prepare": None, "first_frame_ms": None,
           "ticks": [], "seek": None, "errors": [], "http403": 0, "auto_reload_cap": False,
           "loads_403": 0, "readiness": [], "autoplay_stop": None}
    for ln in lines:
        if "NetPath" not in ln:
            continue
        m = re.search(r"player-result video=(\S+) client=(\S+) attempt=(\d+) status=(\S+) playable=(\S) .*?"
                      r"formats=(\S+) usableAdaptive=(\d+) dash=(\S) hls=(\S) sabr=(\S) reason=\"(.*?)\"", ln)
        if m and m.group(1) == video:
            res["results"].append({"client": m.group(2), "attempt": int(m.group(3)), "status": m.group(4),
                                   "playable": m.group(5), "formats": m.group(6),
                                   "usable": int(m.group(7)), "hls": m.group(9), "sabr": m.group(10),
                                   "reason": m.group(11)[:80]})
            continue
        m = re.search(r"player-transform video=(\S+) client=(\S+)", ln)
        if m and m.group(1) == video:
            res["winner"] = m.group(2)
        m = re.search(r"video=" + re.escape(video) + r" info \+(\d+) (.*)", ln)
        if m:
            res["info"] = {"ms": int(m.group(1)), "detail": m.group(2)}
        m = re.search(r"video=" + re.escape(video) + r" prepare \+(\d+) type=(\S+)", ln)
        if m:
            res["prepare"] = {"ms": int(m.group(1)), "type": m.group(2)}
        m = re.search(r"video=" + re.escape(video) + r" first-frame \+(\d+)", ln)
        if m and res["first_frame_ms"] is None:
            res["first_frame_ms"] = int(m.group(1))
        m = re.search(r"video=" + re.escape(video) + r" error \+(\d+) (.*)", ln)
        if m:
            res["errors"].append({"ms": int(m.group(1)), "error": m.group(2)[:160],
                                  "ticks_before": len(res["ticks"])})
        m = re.search(r"bench-tick video=(\S+) pos=(-?\d+) dur=(-?\d+) buf=(-?\d+) state=(\S+) playing=(\S) t=(\d+)", ln)
        if m and m.group(1) == video:
            res["ticks"].append({"pos": int(m.group(2)), "dur": int(m.group(3)), "buf": int(m.group(4)),
                                 "state": m.group(5), "playing": m.group(6), "t": int(m.group(7))})
        m = re.search(r"bench-seek video=(\S+) from=(\d+) to=(\d+)", ln)
        if m and m.group(1) == video:
            res["seek"] = {"from": int(m.group(2)), "to": int(m.group(3))}
        # The readiness gate (pre-roll wait) and the autoplay budget; NetPath context carries no
        # video id on the loader thread, so these are per open, not per video.
        m = re.search(r" (readiness-(?:wait|retry|served)|recovery-deferred reason=readiness) ?(.*)", ln)
        if m:
            res["readiness"].append(m.group(1).split()[0] + " " + m.group(2)[:80])
        if "bot-check cooldown video=" + video in ln:
            res["bot_cooldown"] = True
        if "bot-check trip" in ln:
            res["bot_trip"] = True
        if "player-ring anon-tizen next" in ln:
            res["anon_tizen_next"] = True
        m = re.search(r"autoplay-stop (.*)", ln)
        if m:
            res["autoplay_stop"] = m.group(1)[:80]
        if "http403=y" in ln:
            res["http403"] += 1
        if "auto-reload cap hit" in ln and video in ln:
            res["auto_reload_cap"] = True
    ticks = res["ticks"]
    max_pos = max((t["pos"] for t in ticks), default=-1)
    dur = max((t["dur"] for t in ticks), default=-1)
    ended = any(t["state"] == "ENDED" for t in ticks)
    after_seek = [t for t in ticks if res["seek"] and t["t"] > 0 and t["pos"] > res["seek"]["to"] + 5000]
    res["max_pos_ms"] = max_pos
    res["dur_ms"] = dur
    res["ended"] = ended
    res["played_after_seek"] = bool(after_seek)
    # An error the app recovered from (recovery walk, re-mint): playing ticks advanced after it.
    after_error = ticks[res["errors"][-1]["ticks_before"]:] if res["errors"] else []
    playing_after = [t for t in after_error if t["playing"] == "y" and t["state"] == "READY"]
    res["recovered"] = bool(res["errors"]) and (ended or (
        len(playing_after) >= 2 and playing_after[-1]["pos"] > playing_after[0]["pos"]))
    if res["first_frame_ms"] is None:
        verdict = "NO-START"
    elif res["errors"] and not res["recovered"]:
        verdict = f"FAIL@{max(0, max_pos) // 1000}s"
    elif res["errors"]:
        verdict = f"RECOVERED@{max(0, max_pos) // 1000}s"
    elif ended or (res["seek"] and after_seek) or max_pos >= 140_000:
        verdict = "PLAY-OK"
    elif max_pos >= 60_000:
        verdict = f"PARTIAL@{max_pos // 1000}s"
    else:
        verdict = f"STALL@{max(0, max_pos) // 1000}s"
    res["verdict"] = verdict
    return res


def wait_for_guard(max_wait_s=120):
    """A heads-up notification or a glance at the phone fails the guard for a few seconds: wait it
    out (never act meanwhile) and stop only if it keeps failing."""
    deadline = time.time() + max_wait_s
    ok, why = guard("start")
    while not ok and time.time() < deadline:
        time.sleep(5)
        ok, why = guard("start")
    return ok, why


def run_cell(args, source, video, trial, out):
    ok, why = wait_for_guard()
    if not ok:
        return {"stop": f"guard before open: {why}"}
    setprop("debug.arc.player_client", None if source == "RING" else source)
    setprop("debug.arc.support_xhr", None if args.support_xhr == "none" else args.support_xhr)
    setprop("debug.arc.anon_tizen", "1" if args.anon_tizen else None)
    for key, value in extra_props(args).items():
        setprop(key, value)
    setprop("debug.arc.bench", "1")
    setprop("debug.arc.bench_seek", args.seek)
    # --keep-process: one clean start, then every open lands in the running app like a user's next
    # tap (in-process state such as the bot-check circuit carries over; props apply at start only).
    if not args.keep_process or not KEPT_STARTED[0]:
        shell(f"am force-stop {PKG}")
        KEPT_STARTED[0] = True
    mark = device_time()
    raw = os.path.join(RESULTS, f"{args.run_id}.{source}.{video}.t{trial}.log")
    # Stream the log while the cell runs: the phone's buffer rolls over within minutes (an
    # autoplay storm fills it fastest), so a dump at the end lost the start of the open.
    raw_fh = open(raw, "w")
    logcat = subprocess.Popen(["adb", "-s", SERIAL, "logcat", "-v", "time", "-T", mark, "-s", "NetPath"],
                              stdout=raw_fh, stderr=subprocess.DEVNULL)
    global CURRENT_LOGCAT
    CURRENT_LOGCAT = logcat
    t0 = time.time()
    shell(f"am start -p {PKG} -a android.intent.action.VIEW -d https://youtu.be/{video}")
    aborted = None
    deadline = t0 + args.play_s + 20
    time.sleep(8)
    while time.time() < deadline:
        f = focus()
        if PKG + "/" not in f:
            aborted = f"focus left the check app: {f}"
            break
        time.sleep(10)
    if not args.keep_process:
        shell(f"am force-stop {PKG}")
    time.sleep(1)
    logcat.terminate()
    try:
        logcat.wait(timeout=10)
    except subprocess.TimeoutExpired:
        logcat.kill()
    raw_fh.close()
    with open(raw, errors="replace") as fh:
        lines = fh.read().splitlines()
    res = parse(lines, video)
    row = {"ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "run_id": args.run_id, "network": args.network,
           "source": source, "support_xhr": args.support_xhr, "anon_tizen": bool(args.anon_tizen), "props": extra_props(args), "video": video, "trial": trial,
           "aborted": aborted, **res}
    out.write(json.dumps(row) + "\n")
    out.flush()
    tried = ",".join(r["client"] + ":" + (("OK" if r["playable"] == "y" else r["status"])) for r in res["results"])
    print(f"[{source:14}] {video} t{trial} {row['verdict']:14} winner={res['winner']} "
          f"prepare={res['prepare'] and res['prepare']['type']} ff={res['first_frame_ms']} "
          f"maxPos={res['max_pos_ms']//1000 if res['max_pos_ms']>=0 else -1}s "
          f"seek={'y' if res['seek'] else 'n'} errors={len(res['errors'])} tried={tried}"
          + (f" readiness=[{'; '.join(res['readiness'][:4])}]" if res['readiness'] else "")
          + (f" autoplay-stop={res['autoplay_stop']}" if res['autoplay_stop'] else "")
          + (" BOT-TRIP" if res.get('bot_trip') else "") + (" BOT-COOLDOWN" if res.get('bot_cooldown') else "")
          + (f" ABORTED({aborted})" if aborted else ""), flush=True)
    return {"stop": aborted}


def _stop(signum, frame):
    # Background jobs start with SIGINT ignored; install handlers so a stop always reaches the
    # finally block below (force-stop, clear properties, restore volume).
    raise KeyboardInterrupt(f"signal {signum}")


def main():
    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)
    ap = argparse.ArgumentParser()
    ap.add_argument("--network", required=True)
    ap.add_argument("--sources", required=True, help="AppClient names, or RING for the natural walk")
    ap.add_argument("--videos", required=True)
    ap.add_argument("--support-xhr", default="none")
    ap.add_argument("--keep-process", action="store_true",
                    help="do not restart the app between cells (state carries over, like a user)")
    ap.add_argument("--anon-tizen", action="store_true",
                    help="planner switch: TV_TIZEN without the account right after a refusal")
    ap.add_argument("--prop", action="append", default=[],
                    help="extra debug switch for the app, KEY=VALUE (e.g. debug.arc.hls_vod=1); "
                         "repeatable, cleared at the end")
    ap.add_argument("--repeat", type=int, default=1)
    ap.add_argument("--play-s", type=int, default=150)
    ap.add_argument("--seek", default="90:0.7")
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--serial", default=os.environ.get("NETBENCH_SERIAL"),
                    help="adb serial of the phone (default: $NETBENCH_SERIAL; required; guarded "
                         "unless it is an emulator-* serial)")
    ap.add_argument("--package", default=PKG,
                    help="the app to drive (default: the signed-out .check build; .auth is the "
                         "signed-in benchmark build, -PsideBySide=auth)")
    ap.add_argument("--data", default=os.environ.get("NETBENCH_DATA") or DEFAULT_DATA,
                    help="data directory; results go to <data>/appbench/results "
                         "(default: $NETBENCH_DATA, else the tools/netbench directory)")
    args = ap.parse_args()
    global SERIAL, NO_GUARD, RESULTS
    globals()["PKG"] = args.package  # read as a default above, so not in the global list
    if not args.serial:
        ap.error("no device: pass --serial or set NETBENCH_SERIAL (the phone's adb serial)")
    SERIAL = args.serial
    NO_GUARD = SERIAL.startswith("emulator-")
    RESULTS = os.path.join(args.data, "appbench", "results")
    os.makedirs(RESULTS, exist_ok=True)
    if PKG not in shell(f"pm list packages {PKG}"):
        sys.exit(f"{PKG} is not installed")
    vol = media_volume()
    print(f"appbench {args.run_id}: network={args.network} media volume was {vol}", flush=True)
    set_media_volume(0)
    stopped = None
    try:
        with open(os.path.join(RESULTS, args.run_id + ".jsonl"), "a") as out:
            for trial in range(1, args.repeat + 1):
                for video in args.videos.split(","):
                    for source in args.sources.split(","):
                        r = run_cell(args, source.strip(), video.strip(), trial, out)
                        if r.get("stop"):
                            stopped = r["stop"]
                            break
                    if stopped:
                        break
                if stopped:
                    break
    finally:
        if CURRENT_LOGCAT is not None and CURRENT_LOGCAT.poll() is None:
            CURRENT_LOGCAT.kill()
        shell(f"am force-stop {PKG}")
        for key in ("debug.arc.player_client", "debug.arc.support_xhr", "debug.arc.anon_tizen",
                    "debug.arc.bench", "debug.arc.bench_seek", *extra_props(args)):
            setprop(key, None)
        if vol is not None:
            set_media_volume(vol)
        print(f"appbench {args.run_id}: done" + (f", STOPPED: {stopped}" if stopped else "")
              + f"; media volume restored to {vol}", flush=True)
    if stopped:
        sys.exit(2)  # a stop ends the whole sequence (the wrapper then restores Wi-Fi)


if __name__ == "__main__":
    main()
