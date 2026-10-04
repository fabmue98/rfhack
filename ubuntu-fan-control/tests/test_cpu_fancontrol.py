"""Tests für cpu-fancontrol mit einem nachgebildeten sysfs-Baum (kein root nötig).

Ausführen im Ordner ubuntu-fan-control:  python3 -m unittest discover -s tests -v
"""

import importlib.machinery
import importlib.util
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().parent.parent / "cpu-fancontrol"
_loader = importlib.machinery.SourceFileLoader("cpu_fancontrol", str(SCRIPT))
fc = importlib.util.module_from_spec(importlib.util.spec_from_loader("cpu_fancontrol", _loader))
_loader.exec_module(fc)

CONFIG = """\
[general]
interval = 0.5

[fan:cpu]
pwm = nct6798/pwm1
sensors = auto
fan = nct6798/fan1
curve = {curve}
min_pwm = 25
start_pwm = 50
hysteresis = 4
ramp_down_step = 5
average = 1
critical_temp = 90
"""


class FakeSysfs:
    """Baut /sys/class/hwmon in einem temporären Verzeichnis nach."""

    def __init__(self, base):
        self.root = Path(base) / "hwmon"
        self.root.mkdir()
        self.count = 0

    def chip(self, name, files, device=None, in_device_dir=False):
        hwmon = self.root / f"hwmon{self.count}"
        self.count += 1
        hwmon.mkdir()
        target = hwmon
        if device:
            device_dir = self.root.parent / "devices" / device
            device_dir.mkdir(parents=True)
            (hwmon / "device").symlink_to(device_dir)
            if in_device_dir:
                target = device_dir
        (target / "name").write_text(name + "\n")
        for attr, value in files.items():
            self.set(target, attr, value)
        return target

    @staticmethod
    def set(chip_dir, attr, value):
        (chip_dir / attr).write_text(f"{value}\n")

    @staticmethod
    def get(chip_dir, attr):
        return int((chip_dir / attr).read_text())


class SysfsTestCase(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.dir = Path(tmp.name)
        self.sys = FakeSysfs(self.dir)


class CurveTest(unittest.TestCase):
    def test_interpolate(self):
        curve = [(40, 20), (60, 60), (80, 100)]
        self.assertEqual(fc.interpolate(curve, 30), 20)
        self.assertEqual(fc.interpolate(curve, 50), 40)
        self.assertEqual(fc.interpolate(curve, 70), 80)
        self.assertEqual(fc.interpolate(curve, 95), 100)

    def test_parse_curve_accepts_units(self):
        self.assertEqual(fc.parse_curve("40°C:20%, 60 : 60", "x"), [(40, 20), (60, 60)])

    def test_parse_curve_rejects_invalid(self):
        for text in ("60:20, 40:30", "40:20, 60:10", "40:120", "40-20", " , "):
            with self.subTest(text=text), self.assertRaises(fc.FanControlError):
                fc.parse_curve(text, "x")


class ConfigTest(SysfsTestCase):
    def load(self, text):
        path = self.dir / "test.conf"
        path.write_text(text)
        return fc.load_config(path)

    def test_defaults_and_inline_comments(self):
        config = self.load("[fan:cpu]\npwm = nct6798/pwm2  # CPU_FAN\nsensors = auto\n")
        fan = config.fans[0]
        self.assertEqual((config.interval, config.on_exit), (2, "restore"))
        self.assertEqual((fan.name, fan.pwm, fan.sensors), ("cpu", "nct6798/pwm2", ["auto"]))
        self.assertEqual((fan.min_pwm, fan.start_pwm, fan.average), (20, 40, 3))

    def test_invalid_configs(self):
        fan = "[fan:cpu]\npwm = a/pwm1\nsensors = auto\n"
        for text in ("[general]\ninterval = 1\n",
                     "[fan:cpu]\nsensors = auto\n",
                     fan + "min_pmw = 10\n",
                     fan + "min_pwm = 120\n",
                     fan + "average = viele\n",
                     "[luefter]\npwm = a/pwm1\n",
                     "[general]\non_exit = vielleicht\n" + fan):
            with self.subTest(text=text), self.assertRaises(fc.FanControlError):
                self.load(text)

    def test_missing_file(self):
        with self.assertRaises(fc.FanControlError):
            fc.load_config(self.dir / "fehlt.conf")


class DiscoveryTest(SysfsTestCase):
    def test_resolve_by_number_label_and_device(self):
        self.sys.chip("coretemp", {"temp1_input": 45000, "temp1_label": "Package id 0",
                                   "temp2_input": 40000, "temp2_label": "Core 0"}, device="coretemp.0")
        self.sys.chip("nct6798", {"pwm2": 128, "pwm2_enable": 5, "fan2_input": 900}, device="nct6775.656")
        chips = fc.discover_chips(self.sys.root)
        self.assertEqual(fc.resolve(chips, "coretemp/temp2", "temp")[0], "coretemp/temp2")
        self.assertEqual(fc.resolve(chips, "coretemp/temp2_input", "temp")[0], "coretemp/temp2")
        self.assertEqual(fc.resolve(chips, "coretemp/package id 0", "temp")[0], "coretemp/temp1")
        self.assertEqual(fc.resolve(chips, "nct6775.656/pwm2", "pwm")[0], "nct6798/pwm2")
        for spec in ("nct6798/pwm3", "it8728/pwm1"):
            with self.subTest(spec=spec), self.assertRaises(fc.DeviceNotFound):
                fc.resolve(chips, spec, "pwm")
        with self.assertRaises(fc.FanControlError):
            fc.resolve(chips, "pwm2", "pwm")

    def test_duplicate_names_use_device_id(self):
        for i in range(2):
            self.sys.chip("coretemp", {"temp1_input": 50000, "temp1_label": f"Package id {i}",
                                       "temp2_input": 48000, "temp2_label": "Core 0"}, device=f"coretemp.{i}")
        chips = fc.discover_chips(self.sys.root)
        self.assertEqual([chip.ident for chip in chips], ["coretemp.0", "coretemp.1"])
        with self.assertRaises(fc.FanControlError) as ctx:
            fc.resolve(chips, "coretemp/temp1", "temp")
        self.assertNotIsInstance(ctx.exception, fc.DeviceNotFound)
        self.assertEqual(fc.resolve(chips, "coretemp.1/temp1", "temp")[0], "coretemp.1/temp1")
        self.assertEqual([s.name for s in fc.auto_cpu_sensors(chips)], ["coretemp.0/temp1", "coretemp.1/temp1"])

    def test_attributes_below_device_dir(self):
        self.sys.chip("it8728", {"pwm1": 255, "pwm1_enable": 2}, device="it87.2608", in_device_dir=True)
        chips = fc.discover_chips(self.sys.root)
        self.assertEqual(fc.resolve(chips, "it8728/pwm1", "pwm")[0], "it8728/pwm1")

    def test_auto_sensor_amd(self):
        self.sys.chip("k10temp", {"temp1_input": 50000, "temp1_label": "Tctl",
                                  "temp3_input": 45000, "temp3_label": "Tccd1"}, device="0000:00:18.3")
        chips = fc.discover_chips(self.sys.root)
        self.assertEqual([s.name for s in fc.auto_cpu_sensors(chips)], ["k10temp/temp1"])

    def test_auto_sensor_missing(self):
        self.sys.chip("nvme", {"temp1_input": 40000})
        with self.assertRaises(fc.DeviceNotFound):
            fc.auto_cpu_sensors(fc.discover_chips(self.sys.root))


class BoardTestCase(SysfsTestCase):
    """AMD-CPU (k10temp) und Mainboard-Chip nct6798 mit einem Lüfter an pwm1."""

    def setUp(self):
        super().setUp()
        self.cpu = self.sys.chip("k10temp", {"temp1_input": 40000, "temp1_label": "Tctl"}, device="0000:00:18.3")
        self.board = self.sys.chip("nct6798", {"pwm1": 255, "pwm1_enable": 5, "fan1_input": 1000},
                                   device="nct6775.656")
        self.state = self.dir / "state"

    def controller(self, curve="40:20, 60:60, 80:100"):
        path = self.dir / "cpu.conf"
        path.write_text(CONFIG.format(curve=curve))
        return fc.build_controllers(fc.load_config(path), fc.discover_chips(self.sys.root))[0]

    def temp(self, celsius):
        self.sys.set(self.cpu, "temp1_input", int(celsius * 1000))


class ControllerTest(BoardTestCase):
    def test_rises_immediately_and_falls_with_hysteresis(self):
        c = self.controller()
        self.temp(50)
        self.assertAlmostEqual(c.compute(), 40)
        self.temp(70)
        self.assertAlmostEqual(c.compute(), 80)
        self.temp(67)                              # innerhalb der Hysterese: bleibt
        self.assertAlmostEqual(c.compute(), 80)
        self.temp(60)                              # Ziel f(64) = 68, gebremst um 5 % je Schritt
        self.assertAlmostEqual(c.compute(), 75)
        self.assertAlmostEqual(c.compute(), 70)
        self.assertAlmostEqual(c.compute(), 68)
        self.assertAlmostEqual(c.compute(), 68)

    def test_min_and_start_pwm(self):
        c = self.controller(curve="30:0, 40:20, 60:60")
        fc.take_control([c], "restore", self.state)
        c.last_output = 0.0
        self.temp(25)
        self.assertEqual(c.step(), 0)
        self.temp(35)
        self.assertEqual(c.step(), 50)             # Anlaufdrehzahl
        self.assertEqual(c.step(), 25)             # danach Mindestdrehzahl
        self.assertEqual(self.sys.get(self.board, "pwm1"), 64)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)

    def test_critical_temperature(self):
        c = self.controller()
        self.temp(50)
        c.compute()
        self.temp(95)
        with self.assertLogs(fc.log, "WARNING"):
            self.assertEqual(c.compute(), 100)
        self.temp(88)                              # erst unter 90 - 4 = 86 °C wieder normal
        self.assertEqual(c.compute(), 100)
        self.temp(70)
        with self.assertLogs(fc.log, "INFO"):
            self.assertEqual(c.compute(), 95)

    def test_sensor_failure_means_full_speed(self):
        c = self.controller()
        self.temp(50)
        self.assertAlmostEqual(c.compute(), 40)
        (self.cpu / "temp1_input").unlink()
        with self.assertLogs(fc.log, "ERROR"):
            self.assertEqual(c.compute(), 100)
        self.temp(200)                             # unplausibel, Sensorfehler bleibt bestehen
        self.assertEqual(c.compute(), 100)
        self.temp(50)
        with self.assertLogs(fc.log, "INFO"):
            self.assertEqual(c.compute(), 95)

    def test_stall_warning(self):
        c = self.controller()
        self.temp(50)
        c.last_output = 40.0
        self.sys.set(self.board, "fan1_input", 0)
        with self.assertLogs(fc.log, "WARNING") as logs:
            for _ in range(fc.STALL_CYCLES):
                c.step()
        self.assertTrue(any("0 U/min" in line for line in logs.output))

    def test_external_mode_change_is_taken_back(self):
        c = self.controller()
        self.temp(50)
        fc.take_control([c], "restore", self.state)
        self.sys.set(self.board, "pwm1_enable", 2)
        with self.assertLogs(fc.log, "WARNING"):
            c.step()
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)

    def test_take_control_and_restore(self):
        c = self.controller()
        fc.take_control([c], "restore", self.state)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)
        self.assertTrue(self.state.exists())
        self.temp(50)
        c.step()
        self.assertEqual(self.sys.get(self.board, "pwm1"), 102)   # 40 %
        with self.assertLogs(fc.log, "INFO"):
            self.assertTrue(fc.release_control(self.state))
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 5)
        self.assertFalse(self.state.exists())
        self.assertIsNone(fc.release_control(self.state))

    def test_on_exit_full(self):
        c = self.controller()
        fc.take_control([c], "full", self.state)
        self.temp(50)
        c.step()
        with self.assertLogs(fc.log, "INFO"):
            fc.release_control(self.state)
        self.assertEqual(self.sys.get(self.board, "pwm1"), 255)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)

    def test_crash_recovery_keeps_original_mode(self):
        fc.take_control([self.controller()], "restore", self.state)
        # Absturz: Statusdatei bleibt liegen, pwm1_enable steht auf 1
        with self.assertLogs(fc.log, "WARNING"):
            fc.take_control([self.controller()], "restore", self.state)
        with self.assertLogs(fc.log, "INFO"):
            fc.release_control(self.state)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 5)

    def test_lock_prevents_second_instance(self):
        fd = fc.acquire_lock(self.state)
        self.addCleanup(os.close, fd)
        with self.assertRaises(fc.FanControlError):
            fc.acquire_lock(self.state)


class CommandLineTest(BoardTestCase):
    def cli(self, *args, **kwargs):
        env = dict(os.environ, CPU_FANCONTROL_HWMON_ROOT=str(self.sys.root), PYTHONDONTWRITEBYTECODE="1")
        env.pop("JOURNAL_STREAM", None)
        return subprocess.Popen([sys.executable, str(SCRIPT), *args, "--state-file", str(self.state)],
                                env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, **kwargs)

    def write_config(self):
        path = self.dir / "cpu.conf"
        path.write_text(CONFIG.format(curve="40:20, 60:60, 80:100"))
        return path

    def test_list_and_check(self):
        out, err = self.cli("list").communicate(timeout=30)
        self.assertIn("nct6798/pwm1", out, err)
        self.assertIn("k10temp/temp1", out)
        self.temp(50)
        proc = self.cli("check", "-c", str(self.write_config()))
        out, err = proc.communicate(timeout=30)
        self.assertEqual(proc.returncode, 0, err)
        self.assertIn("ist gültig", out)
        self.assertIn("Sollwert   40 %", out)

    def test_check_reports_config_error(self):
        path = self.dir / "falsch.conf"
        path.write_text("[fan:cpu]\npwm = it8728/pwm1\nsensors = auto\n")
        proc = self.cli("check", "-c", str(path))
        _, err = proc.communicate(timeout=30)
        self.assertEqual(proc.returncode, 2)
        self.assertIn("it8728", err)

    def test_daemon_controls_and_restores_on_sigterm(self):
        self.temp(50)
        proc = self.cli("run", "-c", str(self.write_config()))
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and self.sys.get(self.board, "pwm1") != 102:
            time.sleep(0.1)
        self.assertEqual(self.sys.get(self.board, "pwm1"), 102)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)
        self.assertTrue(self.state.exists())
        proc.send_signal(signal.SIGTERM)
        _, err = proc.communicate(timeout=15)
        self.assertEqual(proc.returncode, 0, err)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 5)
        self.assertFalse(self.state.exists())
        self.assertIn("Steuerung an das Mainboard zurückgegeben", err)

    def test_restore_after_kill(self):
        self.temp(50)
        proc = self.cli("run", "-c", str(self.write_config()))
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline and self.sys.get(self.board, "pwm1_enable") != 1:
            time.sleep(0.1)
        proc.kill()                                # wie SIGKILL/OOM: kein Aufräumen möglich
        proc.communicate(timeout=15)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 1)
        restore = self.cli("restore")
        _, err = restore.communicate(timeout=30)
        self.assertEqual(restore.returncode, 0, err)
        self.assertEqual(self.sys.get(self.board, "pwm1_enable"), 5)


if __name__ == "__main__":
    unittest.main()
