#!/usr/bin/env python3
"""Brute-force generator for darts X01 checkout paths (scores 2..170).

Produces, for every score that can be finished in <=3 darts ending on a double:
  - the minimal number of darts
  - a 'primary' recommended path (convention-matching heuristic)
  - all minimal-length paths (so the app can honour a preferred-double setting)

Scores with no 3-dart finish are flagged as 'bogey' (no checkout).

Output: checkouts.json  -> consumed by the checkout generator into a Kotlin asset.
"""
import json
from itertools import product

# ---- Build the set of possible darts --------------------------------------
# Each dart: (label, value, is_double)
DARTS = []
for n in range(1, 21):
    DARTS.append((str(n), n, False))        # single
    DARTS.append((f"D{n}", 2 * n, True))    # double
    DARTS.append((f"T{n}", 3 * n, False))   # treble
DARTS.append(("25", 25, False))             # outer (single) bull
DARTS.append(("Bull", 50, True))            # bull (double bull) counts as a double

DOUBLES = [d for d in DARTS if d[2]]        # finishing darts must come from here
VALUE = {d[0]: d[1] for d in DARTS}

# ---- Heuristic ranking to match conventional checkout charts ---------------
# Lower rank = more preferred. Even ("percentage") doubles always beat odd ones,
# so the primary suggestion never leaves an odd double unless it is forced.
FINISH_PREF = {
    "D20": 0, "D16": 0, "D8": 1, "D4": 2, "D2": 3, "D12": 4, "D10": 5,
    "D18": 6, "D14": 7, "D6": 8, "Bull": 9,
    # odd doubles only chosen when no even-double finish is possible
    "D11": 50, "D13": 51, "D15": 52, "D17": 53, "D19": 54,
    "D9": 55, "D7": 56, "D5": 57, "D3": 58, "D1": 59,
}
IS_DOUBLE = {d[0]: d[2] for d in DARTS}

def path_sort_key(path):
    """path = list of dart labels, finishing double LAST already enforced."""
    finish = path[-1]
    setups = path[:-1]
    # A double/bull as a SETUP dart is never sound advice -> forbid via penalty.
    double_setups = sum(1 for s in setups if IS_DOUBLE[s]) * 10000
    fin = FINISH_PREF.get(finish, 99)
    # Then prefer a good (even) finishing double; among equals prefer the line
    # that scores most on the setup darts (T20 openers), then labels for order.
    return (double_setups, fin, tuple(-VALUE[x] for x in path), tuple(path))

def ordered_path(setups, finish):
    """Setups thrown big-first, then the finishing double."""
    s = sorted(setups, key=lambda l: (-VALUE[l], l))
    return s + [finish]

def find_paths(score):
    """All paths of 1..3 darts summing to score, ending on a double."""
    results = []
    all_labels = [d[0] for d in DARTS]
    # 1 dart: a single double equal to score
    for d in DOUBLES:
        if d[1] == score:
            results.append([d[0]])
    # 2 darts: one setup + finishing double
    for a in all_labels:
        va = VALUE[a]
        if va >= score:
            continue
        rem = score - va
        for d in DOUBLES:
            if d[1] == rem:
                results.append(ordered_path([a], d[0]))
    # 3 darts: two setups + finishing double
    for a in all_labels:
        va = VALUE[a]
        if va >= score:
            continue
        for b in all_labels:
            vb = VALUE[b]
            if va + vb >= score:
                continue
            rem = score - va - vb
            for d in DOUBLES:
                if d[1] == rem:
                    results.append(ordered_path([a, b], d[0]))
    # de-duplicate (same ordered path can be produced twice)
    uniq = []
    seen = set()
    for p in results:
        key = tuple(p)
        if key not in seen:
            seen.add(key)
            uniq.append(p)
    return uniq

def main():
    table = {}
    for score in range(2, 171):
        paths = find_paths(score)
        if not paths:
            table[str(score)] = {"bogey": True}
            continue
        min_darts = min(len(p) for p in paths)
        minimal = [p for p in paths if len(p) == min_darts]
        minimal.sort(key=path_sort_key)
        primary = minimal[0]
        # finishing-double -> best minimal path ending on it (for preferred-double)
        by_finish = {}
        for p in minimal:
            by_finish.setdefault(p[-1], p)
        table[str(score)] = {
            "darts": min_darts,
            "primary": primary,
            "byFinish": by_finish,
            "bogey": False,
        }
    with open("checkouts.json", "w") as f:
        json.dump(table, f, indent=1)

    # ---- Verification against well-known canonical checkouts ----------------
    expected = {
        170: ["T20", "T20", "Bull"],
        167: ["T20", "T19", "Bull"],
        164: ["T20", "T18", "Bull"],
        161: ["T20", "T17", "Bull"],
        160: ["T20", "T20", "D20"],
        100: ["T20", "D20"],
        50:  ["Bull"],
        40:  ["D20"],
        36:  ["D18"],
        32:  ["D16"],
        2:   ["D1"],
    }
    expected.update({
        60: ["20", "D20"], 66: ["T14", "D12"], 24: ["D12"], 20: ["D10"],
    })
    bogeys_expected = {169, 168, 166, 165, 163, 162, 159}
    ok = True
    for s, want in expected.items():
        got = table[str(s)].get("primary")
        mark = "OK " if got == want else "!! "
        if got != want:
            ok = False
        print(f"{mark}{s:3d}: got={got} want={want}")
    print("---- bogey check ----")
    for s in sorted(bogeys_expected):
        is_bogey = table[str(s)].get("bogey")
        mark = "OK " if is_bogey else "!! "
        if not is_bogey:
            ok = False
        print(f"{mark}{s}: bogey={is_bogey}")
    # Any unexpected bogeys <=170 that are NOT in the known set?
    extra_bogeys = [int(k) for k, v in table.items() if v.get("bogey") and int(k) not in bogeys_expected]
    print("extra bogeys (should only be none or >170 which we don't store):", sorted(extra_bogeys))
    # Validate EVERY stored checkout actually sums correctly and ends on a double
    bad = []
    for k, v in table.items():
        if v.get("bogey"):
            continue
        p = v["primary"]
        total = sum(VALUE[x] for x in p)
        last_is_double = p[-1] in FINISH_PREF
        if total != int(k) or not last_is_double:
            bad.append((k, p, total, last_is_double))
    print("invalid checkouts (should be empty):", bad[:10], "count=", len(bad))

    # Quality property 1: no primary uses a double/bull as a SETUP dart
    double_setup_bad = []
    for k, v in table.items():
        if v.get("bogey"):
            continue
        p = v["primary"]
        if any(IS_DOUBLE[x] for x in p[:-1]):
            double_setup_bad.append((k, p))
    print("primaries with a double as setup (should be empty):", double_setup_bad[:10],
          "count=", len(double_setup_bad))

    # Quality property 2: never finish on an odd double when an even-double
    # minimal finish exists
    odd_when_even_possible = []
    EVEN = {"D20", "D16", "D8", "D4", "D2", "D12", "D10", "D18", "D14", "D6", "Bull"}
    for k, v in table.items():
        if v.get("bogey"):
            continue
        finishes = set(v["byFinish"].keys())
        prim_finish = v["primary"][-1]
        if prim_finish not in EVEN and (finishes & EVEN):
            odd_when_even_possible.append((k, prim_finish, sorted(finishes & EVEN)))
    print("odd-double primaries with an even option (should be empty):",
          odd_when_even_possible[:10], "count=", len(odd_when_even_possible))

    all_ok = ok and not bad and not double_setup_bad and not odd_when_even_possible
    print("\nTOTAL scores stored:", len(table), " | VERIFICATION:",
          "PASS" if all_ok else "FAIL")

if __name__ == "__main__":
    main()
