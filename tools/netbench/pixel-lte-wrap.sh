#!/bin/bash
# Runs a command with the Pixel on LTE: Wi-Fi off (guarded) for the command's duration, back on
# afterwards. Same rules as pixel-lte-run.sh: the Wi-Fi toggle needs guard.sh start (NewTube or
# launcher focused, no shade, awake, no call); the restore falls back to "no call active" after
# 5 min of failed guards; a phone-side timer restores Wi-Fi after 4 h if this PC dies; the command
# is stopped if Wi-Fi comes back on mid-run (its results would no longer be LTE).
#
# Usage: NETBENCH_SERIAL=<adb serial> pixel-lte-wrap.sh <log-file> <command...>
# The guard (device/guard.sh) and the wrapped command read the same NETBENCH_SERIAL.
set -u
S=${NETBENCH_SERIAL:?set NETBENCH_SERIAL to the adb serial of the phone}
export NETBENCH_SERIAL
GUARD=$(cd "$(dirname "$0")" && pwd)/device/guard.sh
LOG=$1; shift
say() { echo "$(date '+%F %T') $*" | tee -a "$LOG"; }
adbs() { adb -s $S "$@"; }
wifi_on() { adbs shell cmd wifi status 2>/dev/null | grep -q "Wifi is enabled"; }

restore() {
  [ -n "${CPID:-}" ] && kill "$CPID" 2>/dev/null
  if ! wifi_on; then
    for i in $(seq 1 10); do
      if "$GUARD" start >>"$LOG" 2>&1; then break; fi
      if [ "$i" -eq 10 ]; then
        while adbs shell dumpsys telephony.registry | grep -a -q "mCallState=[12]"; do sleep 15; done
        say "restore: guard kept failing for 5 min; no call active, re-enabling Wi-Fi anyway"
      fi
      sleep 30
    done
    adbs shell svc wifi enable
    say "restore: Wi-Fi enabled"
  fi
  adbs shell 'kill $(cat /data/local/tmp/netbench-timer.pid 2>/dev/null) 2>/dev/null; rm -f /data/local/tmp/netbench-timer.pid'
  sleep 5
  adbs shell cmd wifi status | head -1 | tee -a "$LOG"
  say "restore: done"
}
trap restore EXIT

adbs shell 'nohup sh -c "sleep 14400; while dumpsys telephony.registry | grep -q \"mCallState=[12]\"; do sleep 15; done; svc wifi enable" >/dev/null 2>&1 & echo $! > /data/local/tmp/netbench-timer.pid'
# The guard's own status, not tee's: a failed guard must stop the Wi-Fi switch.
"$GUARD" start | tee -a "$LOG"
[ "${PIPESTATUS[0]}" -eq 0 ] || { say "guard failed: not switching Wi-Fi off"; exit 1; }
adbs shell svc wifi disable
say "Wi-Fi disabled"
for i in $(seq 1 30); do
  adbs shell dumpsys connectivity 2>/dev/null | grep -a -m1 "Active default network" | grep -q -v "none" && \
    adbs shell ping -c1 -W3 8.8.8.8 >/dev/null 2>&1 && break
  sleep 2
done
say "cell network up: $(adbs shell dumpsys connectivity | grep -a -m1 -o 'NetworkAgentInfo{network{[0-9]*}  handle{[0-9]*}  ni{[A-Z]*' | head -1)"

"$@" >>"$LOG" 2>&1 &
CPID=$!
say "command pid $CPID: $*"
while kill -0 "$CPID" 2>/dev/null; do
  sleep 30
  if wifi_on; then say "Wi-Fi came back on during the run: stopping it"; kill "$CPID"; break; fi
done
wait "$CPID" 2>/dev/null
say "command finished (exit $?)"
