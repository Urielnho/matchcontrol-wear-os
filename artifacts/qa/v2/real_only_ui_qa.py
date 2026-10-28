"""Check the real-data guest interface and paired favorites without creating bets or accounts."""
from pathlib import Path
import json
import re
import sys
import time

sys.path.insert(0, str(Path(__file__).parent))
import mobile_ui_qa as mobile
import mirror_transport_qa as transport

q = mobile.q
q.SERIAL = "emulator-5556"
report = {"sports": {}, "no_practice_ui": True, "guest_finances_empty": True}
canonical = re.compile(r"(?:api|basket|base):[0-9]+|f1:[0-9]{4}:[0-9]+")
forbidden = re.compile(r"\bdemo\b|\bfictici\w*|\blaboratorio\b|\bsimulad\w*", re.I)


def read_state():
    state = transport.read("emulator-5556")
    assert state["source"] == "API_FOOTBALL"
    assert not state["profile"]["id"]
    assert state["balance"] == 0 and not state["bets"] and not state["transactions"]
    assert all(f["source"] == "API_FOOTBALL" and canonical.fullmatch(f["id"]) for f in state["fixtures"])
    return state


def check_screen(name):
    root = q.dump()
    labels = " ".join(n.get("text", "") + " " + n.get("content-desc", "") for n in root.iter("node"))
    assert not forbidden.search(labels), "Practice UI is still visible"
    q.shot(name)
    read_state()


def choose(label):
    for _ in range(5):
        point = q.find(label)
        if point:
            q.adb("shell", "input", "tap", *point)
            time.sleep(1)
            return
        root = q.dump()
        anchor = next((n for n in root.iter("node") if n.get("text") in ["Todos", "Fútbol", "Baloncesto", "Béisbol", "F1"]), None)
        assert anchor is not None, "Sport selector is unavailable"
        _, y1, _, y2 = q.bounds(anchor)
        width, _ = q.size()
        reveal_left = label in ["Todos", "Fútbol"]
        q.adb("shell", "input", "swipe", int(width * (.2 if reveal_left else .85)), (y1 + y2) // 2,
              int(width * (.85 if reveal_left else .2)), (y1 + y2) // 2, 350)
    raise AssertionError("Sport chip is unavailable")


def wait_sport(sport):
    deadline = time.monotonic() + 35
    while True:
        state = read_state()
        count = sum(f.get("sport", "FOOTBALL") == sport for f in state["fixtures"])
        if count or time.monotonic() >= deadline:
            time.sleep(1)
            return count
        time.sleep(.5)


q.tap("Inicio")
q.top(3)
for label, sport, slug in [("Fútbol", "FOOTBALL", "football"), ("Baloncesto", "BASKETBALL", "basketball"),
                           ("Béisbol", "BASEBALL", "baseball"), ("F1", "FORMULA1", "f1")]:
    choose(label)
    count = wait_sport(sport)
    check_screen("premium-real-only-" + slug)
    report["sports"][sport] = {"provider_events": count, "screen_checked": True}
    print("PASS real-only " + slug + " screen", flush=True)

q.tap("Perfil")
q.top(3)
check_screen("premium-real-only-profile")
report["profile_has_no_practice_controls"] = True
q.tap("Apuestas")
check_screen("premium-real-only-bets")
report["guest_has_no_generated_tickets"] = True

q.tap("Inicio")
q.top(3)
choose("Fútbol")
initial = read_state()
favorite_id = None
added = False
try:
    for _ in range(6):
        root = q.dump()
        card = next((n for n in root.iter("node") if " contra " in n.get("content-desc", "")
                     and 150 < (q.bounds(n)[1] + q.bounds(n)[3]) // 2 < q.size()[1] * .85), None)
        if card is not None:
            description = card.get("content-desc")
            fixture = next((f for f in initial["fixtures"] if description == f["home"]["name"] + " contra " + f["away"]["name"]), None)
            if fixture is not None and fixture["id"] not in initial["favoriteMatches"]:
                favorite_id = fixture["id"]
                x1, y1, x2, y2 = q.bounds(card)
                q.adb("shell", "input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
                time.sleep(1)
                break
        q.swipe()
    if favorite_id is not None:
        q.tap("Guardar partido")
        added = True
        transport.wait()
        assert favorite_id in read_state()["favoriteMatches"]
        assert favorite_id in transport.read("emulator-5558")["favoriteMatches"]
        report["real_favorite_received_on_watch"] = True
        check_screen("premium-real-only-favorite")
finally:
    if added:
        q.tap("Guardado ★")
        transport.wait()
        assert read_state()["favoriteMatches"] == initial["favoriteMatches"]
        assert favorite_id not in transport.read("emulator-5558")["favoriteMatches"]
        report["favorite_change_restored"] = True

q.tap("Inicio")
q.top(3)
choose("Fútbol")
check_screen("premium-real-only-ready")
read_state()
(Path(__file__).parent / "real-only-ui.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("PASS real-only mobile guest smoke", flush=True)
