#!/usr/bin/env python3
"""
Test de montée en charge de LSTracker (cahier XI : « tests de montée en charge
représentatifs avant mise en production »).

Simule des utilisateurs simultanés :
  - web (70 %) : connexion par le formulaire, puis consultation du tableau de
    bord (ses 11 appels, période de deux ans) et de la liste des échantillons ;
  - mobile (30 %) : connexion à l'API, métadonnées, synchronisation (pull des
    dernières 24 h).
Chaque palier dure --duree secondes ; on relève, par appel, le nombre de
requêtes, les erreurs et les temps de réponse (médiane, 95e et 99e centiles).

Bibliothèque standard uniquement. L'identifiant et le mot de passe d'un
compte ADMIN de test sont lus dans l'environnement (jamais en argument) :

  LST_LOGIN=charge_test LST_PASSWORD=... \\
    python3 scripts/charge/test_charge.py --url http://localhost:8060 --paliers 10,25,50 --duree 60

À lancer sur la pré-production (démonstration) ou une copie, jamais sur la
production en service.
"""
import argparse
import datetime as dt
import http.cookiejar
import json
import os
import random
import re
import statistics
import sys
import threading
import time
import urllib.parse
import urllib.request
from collections import defaultdict

END = dt.date.today()
START = END - dt.timedelta(days=730)
PERIOD = {"startDate": START.isoformat(), "endDate": END.isoformat()}

DASHBOARD = [
    "dashboard/data/funnel", "dashboard/data/funnel-previous", "dashboard/data/series",
    "dashboard/data/coverage", "dashboard/data/type-breakdown", "dashboard/data/top-performers",
    "dashboard/data/step-durations", "dashboard/sample_status_by_sample_type",
    "dashboard/data/by-region", "dashboard/data/by-district", "dashboard/data/by-site",
]


class Stats:
    def __init__(self):
        self.lock = threading.Lock()
        self.times = defaultdict(list)
        self.errors = defaultdict(int)

    def record(self, name, seconds, ok):
        with self.lock:
            self.times[name].append(seconds)
            if not ok:
                self.errors[name] += 1


def timed(stats, name, fn):
    t0 = time.perf_counter()
    ok = True
    try:
        fn()
    except Exception:
        ok = False
    stats.record(name, time.perf_counter() - t0, ok)
    return ok


def web_user(base, login, password, stop, stats):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def connect():
        page = opener.open(base + "/login", timeout=30).read().decode("utf-8", "replace")
        token = re.search(r'name="_csrf" value="([^"]+)"', page).group(1)
        data = urllib.parse.urlencode({"username": login, "password": password, "_csrf": token}).encode()
        resp = opener.open(base + "/login", data=data, timeout=30)
        if "/login" in resp.geturl():
            raise RuntimeError("connexion refusée")

    if not timed(stats, "web : connexion", connect):
        return
    qs = urllib.parse.urlencode(PERIOD)
    while not stop.is_set():
        for path in DASHBOARD:
            if stop.is_set():
                return
            timed(stats, path, lambda p=path: opener.open(f"{base}/{p}?{qs}", timeout=60).read())
        timed(stats, "sample/data (liste, 25 lignes)", lambda: opener.open(
            f"{base}/sample/data?draw=1&start=0&length=25", timeout=60).read())
        time.sleep(random.uniform(1, 3))  # temps de lecture


def mobile_user(base, login, password, stop, stats):
    token = {}

    def connect():
        req = urllib.request.Request(base + "/api_v2/auth/login",
                                     data=json.dumps({"username": login, "password": password}).encode(),
                                     headers={"Content-Type": "application/json"})
        token["t"] = json.loads(urllib.request.urlopen(req, timeout=30).read())["access_token"]

    if not timed(stats, "mobile : connexion", connect):
        return
    headers = {"Authorization": "Bearer " + token["t"]}
    since = urllib.parse.quote((dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=1)).isoformat())
    while not stop.is_set():
        timed(stats, "api_v2/meta/full", lambda: urllib.request.urlopen(
            urllib.request.Request(base + "/api_v2/meta/full", headers=headers), timeout=60).read())
        timed(stats, "api_v2/sync/samples/pull (24 h)", lambda: urllib.request.urlopen(
            urllib.request.Request(f"{base}/api_v2/sync/samples/pull?since={since}", headers=headers),
            timeout=60).read())
        time.sleep(random.uniform(5, 10))  # synchronisation périodique


def pct(values, p):
    s = sorted(values)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]


def run_level(base, users, duration, login, password):
    stats, stop = Stats(), threading.Event()
    threads = []
    for i in range(users):
        target = mobile_user if i % 10 >= 7 else web_user
        t = threading.Thread(target=target, args=(base, login, password, stop, stats), daemon=True)
        threads.append(t)
        t.start()
        time.sleep(0.05)  # montée progressive
    time.sleep(duration)
    stop.set()
    for t in threads:
        t.join(timeout=90)
    return stats


def report(level, duration, stats):
    total = sum(len(v) for v in stats.times.values())
    errors = sum(stats.errors.values())
    print(f"\n### {level} utilisateurs simultanés ({duration} s)\n")
    print(f"{total} requêtes, {total / duration:.1f} par seconde, {errors} erreur(s)\n")
    print("| Appel | Requêtes | Erreurs | Médiane (ms) | 95e centile (ms) | 99e centile (ms) | Max (ms) |")
    print("|---|---:|---:|---:|---:|---:|---:|")
    for name in sorted(stats.times, key=lambda n: -pct(stats.times[n], 95)):
        v = [x * 1000 for x in stats.times[name]]
        print(f"| {name} | {len(v)} | {stats.errors[name]} | {statistics.median(v):.0f} | "
              f"{pct(v, 95):.0f} | {pct(v, 99):.0f} | {max(v):.0f} |")
    sys.stdout.flush()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", required=True, help="ex. http://localhost:8060")
    ap.add_argument("--paliers", default="10,25,50", help="utilisateurs simultanés par palier")
    ap.add_argument("--duree", type=int, default=60, help="durée de chaque palier, en secondes")
    a = ap.parse_args()
    login, password = os.environ.get("LST_LOGIN"), os.environ.get("LST_PASSWORD")
    if not login or not password:
        sys.exit("Renseigner LST_LOGIN et LST_PASSWORD (compte ADMIN de test).")
    base = a.url.rstrip("/")
    print(f"# Test de charge LSTracker — {dt.datetime.now():%d/%m/%Y %H:%M}\n")
    print(f"Cible : {base} · période du tableau de bord : {START:%d/%m/%Y} au {END:%d/%m/%Y} · "
          f"70 % web, 30 % mobile")
    for level in [int(x) for x in a.paliers.split(",")]:
        report(level, a.duree, run_level(base, level, a.duree, login, password))


if __name__ == "__main__":
    main()
