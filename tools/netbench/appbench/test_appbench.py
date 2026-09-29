#!/usr/bin/env python3
"""appbench.parse on a fixed log: the phase fields, and the pre-existing fields left as they were.

Offline: python3 tools/netbench/appbench/test_appbench.py (no adb, no device).
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import appbench  # noqa: E402

V = "_WB5hh7WOb4"
# A cold kids open (VISIONOS refused, TV_TIZEN played), as a Pixel logged it on 2026-09-29, plus
# the lines this build adds: the warm's Cronet breakdown, the still-lift path, the embed identity.
LOG = f"""\
09-29 00:27:35.835 D/NetPath (22152): ep=1 video={V} tap
09-29 00:27:35.910 D/NetPath (22152): player-context video={V} client=VISIONOS cver=1.02 visitorSource=web-pot visitor=ae192e7163 visitorAgeMs=-1 playerPot=n
09-29 00:27:35.954 D/NetPath (22152): player-http[S] rid=1 video={V} client=101 cver=1.02
09-29 00:27:36.219 D/NetPath (22152): player-http[C] rid=1 video={V} code=200 ms=265 net=cell:183 protocol=h2
09-29 00:27:36.228 D/NetPath (22152): player-result video={V} client=VISIONOS attempt=1 status=UNPLAYABLE playable=n auth=n srvAuth=? formats=0+0 usableAdaptive=0 dash=n hls=n sabr=n reason="Este video no esta disponible"
09-29 00:27:36.230 D/NetPath (22152): embed-identity source=restored ageMin=42
09-29 00:27:36.384 D/NetPath (22152): player-result video={V} client=TV_TIZEN attempt=2 status=OK playable=y auth=n srvAuth=n formats=26+1 usableAdaptive=26 dash=n hls=n sabr=n reason="null"
09-29 00:27:36.499 D/NetPath (22152): v8-player cached=y challenges=2
09-29 00:27:36.515 D/NetPath (22152): warm rr4---sn-uxax4vopj5xn-cjoe.googlevideo.com +130ms dns=4 connect=96 ssl=61 wait=28 reused=n proto=h3
09-29 00:27:36.624 D/NetPath (22152): v8-run reused=y initMs=0 solveMs=125 stdinKb=3783
09-29 00:27:36.625 D/NetPath (22152): player-sig video={V} holders=28 n=2/27 s=0/0 nOut=27/27,unchanged=0,size=match sOut=0/0,unchanged=0,size=absent ms=239
09-29 00:27:36.627 D/NetPath (22152): player-transform video={V} client=TV_TIZEN ms=242
09-29 00:27:36.631 D/NetPath (22152): ep=1 video={V} info +796 dash=26 hls=n sabr=n live=n
09-29 00:27:36.660 D/NetPath (22152): ep=1 video={V} suggest ready +825
09-29 00:27:36.665 D/NetPath (22152): ep=1 video={V} prepare +830 type=dash-mpd
09-29 00:27:36.690 D/NetPath (22152): ep=1 video={V} media-load init +855 track=video id=244 h=480
09-29 00:27:36.736 D/NetPath (22152): ep=1 video={V} media-init done +901 track=audio loadMs=43 bytes=494
09-29 00:27:36.737 D/NetPath (22152): ep=1 video={V} media-init done +902 track=video loadMs=45 bytes=657
09-29 00:27:36.896 D/NetPath (22152): ep=1 video={V} decoder init +1061 type=video name=c2.exynos.vp9.decoder initMs=152
09-29 00:27:36.934 D/NetPath (22152): ep=1 video={V} decoder init +1099 type=audio name=c2.android.opus.decoder initMs=25
09-29 00:27:36.946 D/NetPath (22152): ep=1 video={V} first-frame +1111
09-29 00:27:36.960 D/NetPath (22152): ep=1 video={V} renderer-ready +1125 type=video
09-29 00:27:37.029 D/NetPath (22152): ep=1 video={V} ready +1194 bufferedMs=2867
09-29 00:27:37.030 D/NetPath (22152): ep=1 video={V} picture-visible +1195 state=ready-texture-overlay-gone lift=ready
09-29 00:29:38.708 D/NetPath (22152): warm rr5---sn-uxax4vopj5xn-cjol.googlevideo.com +610ms metrics=none
09-29 00:29:38.882 D/NetPath (22152): v8-run reused=y initMs=0 solveMs=145 stdinKb=3783
09-29 00:29:38.883 D/NetPath (22152): player-sig video={V} holders=26 n=2/25 s=0/0 ms=274
""".splitlines()


class PhasesTest(unittest.TestCase):
    def test_phase_fields(self):
        res = appbench.parse(LOG, V)
        self.assertEqual(res["first_frame_ms"], 1111)
        self.assertEqual(res["ready_ms"], 1194)  # not "suggest ready +825"
        self.assertEqual(res["picture_visible_ms"], 1195)
        self.assertEqual(res["picture_lift"], "ready")
        self.assertEqual(res["sig_ms"], 239)  # the played answer's, not the later one's
        self.assertEqual(res["v8_solve_ms"], 125)
        self.assertEqual(res["answer_client"], "TV_TIZEN")
        self.assertEqual(res["answer_ms"], 549)  # 36.384 - 35.835
        self.assertEqual(res["mli_ms"], 855)
        self.assertEqual(res["init_done_ms"], 902)
        self.assertEqual(res["dec_video_ms"], 152)
        self.assertEqual(res["dec_audio_ms"], 25)
        self.assertEqual(res["warm_host"], "rr4---sn-uxax4vopj5xn-cjoe.googlevideo.com")
        self.assertEqual((res["warm_ms"], res["warm_ok"], res["warm_done_ms"]), (130, True, 680))
        self.assertEqual((res["warm_dns_ms"], res["warm_connect_ms"], res["warm_ssl_ms"],
                          res["warm_wait_ms"], res["warm_reused"], res["warm_proto"]),
                         (4, 96, 61, 28, False, "h3"))
        self.assertEqual((res["embed_identity"], res["embed_fetch_ms"]), ("restored", None))

    def test_existing_fields_unchanged(self):
        res = appbench.parse(LOG, V)
        self.assertEqual(res["winner"], "TV_TIZEN")
        self.assertEqual(res["prepare"], {"ms": 830, "type": "dash-mpd"})
        self.assertEqual(res["info"], {"ms": 796, "detail": "dash=26 hls=n sabr=n live=n"})
        self.assertEqual([r["client"] for r in res["results"]], ["VISIONOS", "TV_TIZEN"])
        self.assertEqual(res["verdict"], "STALL@0s")  # no bench ticks in this excerpt

    def test_old_logs_and_missing_lines(self):
        old = [ln.replace(" lift=ready", "") for ln in LOG
               if "embed-identity" not in ln and "picture-visible" not in ln]
        old = [ln.split(" dns=")[0] for ln in old]
        res = appbench.parse(old, V)
        self.assertIsNone(res["picture_visible_ms"])
        self.assertIsNone(res["picture_lift"])
        self.assertIsNone(res["embed_identity"])
        self.assertEqual(res["warm_ms"], 130)
        self.assertIsNone(res["warm_dns_ms"])
        # A fetched identity and a failed warm with no metrics.
        res = appbench.parse(LOG[:1] + [
            "09-29 00:27:36.100 D/NetPath (1): embed-identity source=fetched ms=231",
            "09-29 00:27:36.200 D/NetPath (1): warm-failed rr1---x.googlevideo.com +8004ms reason=timeout metrics=none",
        ], V)
        self.assertEqual((res["embed_identity"], res["embed_fetch_ms"]), ("fetched", 231))
        self.assertEqual((res["warm_ok"], res["warm_ms"], res["warm_dns_ms"]), (False, 8004, None))
        self.assertIsNone(res["answer_ms"])

    def test_nothing_before_the_tap_counts(self):
        res = appbench.parse(["09-29 00:27:35.000 D/NetPath (1): warm rr1---x.googlevideo.com +90ms"] + LOG, V)
        self.assertEqual(res["warm_ms"], 130)



M = "w664JpkrDio"
# A members-only refusal settled at request 4, then the app's autoplay into a suggestion (v17 Wi-Fi).
REFUSED = f"""\
09-29 10:26:51.551 D/NetPath ( 3346): ep=3 video={M} tap
09-29 10:26:52.274 D/NetPath ( 3346): player-result video={M} client=IOS attempt=4 status=UNPLAYABLE playable=n auth=n srvAuth=n formats=0+0 usableAdaptive=0 dash=n hls=n sabr=n reason="Hazte miembro"
09-29 10:26:52.276 D/NetPath ( 3346): player-ring definitive-unplayable video={M} clients=VISIONOS,WEB_EMBED,ANDROID_VR,IOS reason-hash=1b9e3fe0 attempts=4 skipped=4
09-29 10:26:52.277 D/NetPath ( 3346): ep=3 video={M} info +726 dash=0 hls=n sabr=n live=n
""".splitlines()
AUTOPLAY = ['09-29 10:26:57.287 D/NetPath ( 3346): ep=4 video=orrMu1rSUUU open +0 "Cervical Stenosis"']


def ticks(video, *positions):
    return [f"09-29 00:28:{10 + i:02d}.000 D/NetPath (1): bench-tick video={video} pos={p} dur=213000 "
            f"buf=5000 state=READY playing=y t={i}" for i, p in enumerate(positions)]


class SettleTest(unittest.TestCase):
    def setUp(self):
        appbench.SETTLE_INFO_SEEN.clear()

    def test_the_window_is_judged_on_its_own_length(self):
        self.assertEqual(appbench.parse(LOG + ticks(V, 8000, 18000), V, 15)["verdict"], "PLAY-OK")
        self.assertEqual(appbench.parse(LOG + ticks(V, 8000, 9000), V, 15)["verdict"], "STALL@9s")
        # A full cell is judged as before.
        self.assertEqual(appbench.parse(LOG + ticks(V, 8000, 18000), V)["verdict"], "STALL@18s")

    def test_a_played_window_settles(self):
        self.assertFalse(appbench.settled(LOG + ticks(V, 8000), V, 15))
        self.assertTrue(appbench.settled(LOG + ticks(V, 8000, 18000), V, 15))

    def test_a_refusal_settles_when_the_app_moves_on(self):
        self.assertTrue(appbench.settled(REFUSED + AUTOPLAY, M, 15))

    def test_a_refusal_settles_after_a_grace(self):
        self.assertFalse(appbench.settled(REFUSED, M, 15))
        appbench.SETTLE_INFO_SEEN[M] -= 6
        self.assertTrue(appbench.settled(REFUSED, M, 15))

    def test_another_videos_playback_is_not_the_watched_one(self):
        # The autoplayed suggestion's ticks never count as the refused video's window.
        self.assertEqual(appbench.parse(REFUSED + AUTOPLAY + ticks("orrMu1rSUUU", 18000), M, 15)["verdict"],
                         "NO-START")

    def test_a_walk_still_running_does_not_settle(self):
        self.assertFalse(appbench.settled(LOG[:5], V, 15))


class SenderTurnTest(unittest.TestCase):
    """Two runs on one network take turns per open: the second waits for the first's open to end."""

    def setUp(self):
        import tempfile
        self.dir = tempfile.mkdtemp()
        appbench.SENDER_LOCK = os.path.join(self.dir, "sub", "sender.lock")

    def tearDown(self):
        appbench.SENDER_LOCK = None

    def test_a_turn_waits_for_the_other_runs_open(self):
        import subprocess
        import time
        holder = subprocess.Popen([sys.executable, "-c",
            "import fcntl,os,sys,time\n"
            f"os.makedirs(os.path.dirname({appbench.SENDER_LOCK!r}), exist_ok=True)\n"
            f"fh=open({appbench.SENDER_LOCK!r},'a'); fcntl.flock(fh, fcntl.LOCK_EX)\n"
            "print('held', flush=True); time.sleep(2)"], stdout=subprocess.PIPE)
        self.assertEqual(holder.stdout.readline().strip(), b"held")
        t = time.time()
        with appbench.sender_turn():
            waited = time.time() - t
        holder.wait()
        self.assertGreater(waited, 1.5)

    def test_off_means_no_wait(self):
        appbench.SENDER_LOCK = None
        with appbench.sender_turn():
            pass

    def test_battery_stop(self):
        class Args:
            min_battery = 8
        real = appbench.shell
        appbench.shell = lambda cmd, timeout=60: "Current Battery Service state:\n  AC powered: false\n  level: 3\n  scale: 100\n"
        try:
            self.assertEqual(appbench.run_cell(Args, "RING", V, 1, None), {"stop": "battery 3% < 8%"})
        finally:
            appbench.shell = real

if __name__ == "__main__":
    unittest.main()
