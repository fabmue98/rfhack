#!/usr/bin/env bash
# Installiert cpu-fancontrol als systemd-Dienst.
#   sudo ./install.sh             installieren bzw. aktualisieren
#   sudo ./install.sh uninstall   entfernen (die Konfiguration bleibt erhalten)
set -euo pipefail

BIN=/usr/local/sbin/cpu-fancontrol
CONF=/etc/cpu-fancontrol.conf
UNIT=/etc/systemd/system/cpu-fancontrol.service
SRC=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $EUID -ne 0 ]]; then
    echo "Bitte mit Root-Rechten ausführen: sudo $0 $*" >&2
    exit 1
fi

case "${1:-}" in
    "") ;;
    uninstall)
        systemctl disable --now cpu-fancontrol.service 2>/dev/null || true
        rm -f "$BIN" "$UNIT"
        systemctl daemon-reload
        echo "cpu-fancontrol wurde entfernt. Die Konfiguration $CONF ist noch vorhanden."
        exit 0
        ;;
    *)
        echo "Aufruf: sudo $0 [uninstall]" >&2
        exit 1
        ;;
esac

if ! command -v python3 >/dev/null; then
    echo "python3 fehlt – installieren mit: sudo apt install python3" >&2
    exit 1
fi

install -m 0755 "$SRC/cpu-fancontrol" "$BIN"
install -m 0644 "$SRC/cpu-fancontrol.service" "$UNIT"
echo "• Programm nach $BIN und Dienst nach $UNIT installiert."
if [[ -e $CONF ]]; then
    echo "• $CONF existiert bereits und wurde nicht überschrieben."
else
    install -m 0644 "$SRC/cpu-fancontrol.conf" "$CONF"
    echo "• Beispielkonfiguration nach $CONF kopiert."
fi
systemctl daemon-reload

if systemctl is-active --quiet cpu-fancontrol.service; then
    systemctl restart cpu-fancontrol.service
    echo "• Laufender Dienst wurde mit der neuen Version neu gestartet."
fi

if systemctl is-active --quiet fancontrol.service; then
    echo
    echo "ACHTUNG: Der Dienst 'fancontrol' (Paket fancontrol) läuft ebenfalls und würde"
    echo "         dieselben Lüfter steuern. Deaktivieren: sudo systemctl disable --now fancontrol"
fi

if ! compgen -G "/sys/class/hwmon/hwmon*/pwm[0-9]*" >/dev/null &&
   ! compgen -G "/sys/class/hwmon/hwmon*/device/pwm[0-9]*" >/dev/null; then
    echo
    echo "HINWEIS: Noch keine PWM-Lüfterausgänge gefunden. Meist fehlt der Treiber für den"
    echo "         Lüfter-Chip des Mainboards:  sudo apt install lm-sensors && sudo sensors-detect"
    echo "         Details: README.md, Abschnitt \"Sensor-Treiber laden\"."
fi

cat <<'EOF'

Nächste Schritte:
  1. Sensoren und Lüfter anzeigen:     cpu-fancontrol list
  2. Ausgang des CPU-Lüfters finden:    sudo cpu-fancontrol test <chip>/pwmN
  3. Konfiguration anpassen:           sudo nano /etc/cpu-fancontrol.conf
  4. Konfiguration prüfen:             sudo cpu-fancontrol check
  5. Dienst aktivieren und starten:    sudo systemctl enable --now cpu-fancontrol
  6. Log verfolgen:                    journalctl -u cpu-fancontrol -f
EOF
