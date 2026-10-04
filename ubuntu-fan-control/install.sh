#!/usr/bin/env bash
# Installiert cpu-fancontrol als systemd-Dienst.
#   sudo ./install.sh             installieren bzw. aktualisieren
#   sudo ./install.sh uninstall   entfernen (die Konfiguration bleibt erhalten)
set -euo pipefail

BIN=/usr/local/sbin/cpu-fancontrol
SHARE=/usr/local/share/cpu-fancontrol
CONF=/etc/cpu-fancontrol.conf
UNIT=/etc/systemd/system/cpu-fancontrol.service
APACHE_CONF=/etc/apache2/conf-available/cpu-fancontrol.conf
NGINX_SNIPPET=/etc/nginx/snippets/cpu-fancontrol.conf
SRC=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ $EUID -ne 0 ]]; then
    echo "Bitte mit Root-Rechten ausführen: sudo $0 $*" >&2
    exit 1
fi

# Fragt mit Vorgabe "ja"; ohne Terminal (z.B. per Skript) gilt "nein".
ask() {
    [[ -t 0 ]] || return 1
    local answer
    read -r -p "$1 [J/n] " answer
    [[ -z $answer || $answer =~ ^[JjYy] ]]
}

case "${1:-}" in
    "") ;;
    uninstall)
        systemctl disable --now cpu-fancontrol.service 2>/dev/null || true
        rm -f "$BIN" "$UNIT"
        rm -rf "$SHARE"
        if [[ -e $APACHE_CONF ]]; then
            a2disconf -q cpu-fancontrol 2>/dev/null || true
            rm -f "$APACHE_CONF"
            systemctl reload apache2 2>/dev/null || true
        fi
        if [[ -e $NGINX_SNIPPET ]]; then
            echo "Hinweis: $NGINX_SNIPPET bleibt bestehen, solange es noch per include eingebunden ist."
            echo "         Erst die include-Zeile entfernen, dann die Datei löschen."
        fi
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
install -D -m 0644 "$SRC/web/index.html" "$SHARE/index.html"
install -m 0644 "$SRC/cpu-fancontrol.service" "$UNIT"
echo "• Programm nach $BIN, Weboberfläche nach $SHARE und Dienst nach $UNIT installiert."
if [[ -e $CONF ]]; then
    echo "• $CONF existiert bereits und wurde nicht überschrieben."
    if ! grep -q '^\[web\]' "$CONF"; then
        printf '\n[web]\n# Weboberfläche, siehe README.md\nenabled = yes\nlisten = 127.0.0.1\nport = 8077\npath = /fan\n' >> "$CONF"
        echo "• Abschnitt [web] für die Weboberfläche an $CONF angehängt."
    fi
else
    install -m 0644 "$SRC/cpu-fancontrol.conf" "$CONF"
    echo "• Beispielkonfiguration nach $CONF kopiert."
fi
systemctl daemon-reload

if systemctl is-active --quiet cpu-fancontrol.service; then
    systemctl restart cpu-fancontrol.service
    echo "• Laufender Dienst wurde mit der neuen Version neu gestartet."
fi

# Weboberfläche unter /fan im vorhandenen Webserver einbinden
echo
if systemctl is-active --quiet apache2; then
    if [[ -e $APACHE_CONF ]]; then
        install -m 0644 "$SRC/web/apache-cpu-fancontrol.conf" "$APACHE_CONF"
        systemctl reload apache2
        echo "• Apache: /fan ist eingerichtet ($APACHE_CONF)."
    elif ask "Apache läuft. Weboberfläche dort unter /fan einbinden?"; then
        install -m 0644 "$SRC/web/apache-cpu-fancontrol.conf" "$APACHE_CONF"
        a2enmod -q proxy proxy_http
        a2enconf -q cpu-fancontrol
        if apache2ctl configtest 2>/dev/null; then
            systemctl reload apache2
            echo "• Apache: Weboberfläche unter http://<server>/fan/ eingerichtet."
        else
            a2disconf -q cpu-fancontrol
            echo "ACHTUNG: Apache-Konfigurationstest fehlgeschlagen – /fan wurde wieder deaktiviert." >&2
        fi
    else
        echo "• Apache: übersprungen. Später einbinden: siehe README.md, Abschnitt \"Weboberfläche\"."
    fi
elif systemctl is-active --quiet nginx; then
    install -D -m 0644 "$SRC/web/nginx-cpu-fancontrol.conf" "$NGINX_SNIPPET"
    if grep -rqs "snippets/cpu-fancontrol.conf" /etc/nginx/sites-enabled /etc/nginx/conf.d; then
        nginx -t 2>/dev/null && systemctl reload nginx
        echo "• nginx: /fan ist eingerichtet."
    else
        echo "• nginx: $NGINX_SNIPPET angelegt. Zum Einbinden im server-Block der Seite"
        echo "  (z.B. in /etc/nginx/sites-enabled/<seite>) diese Zeile ergänzen:"
        echo "      include snippets/cpu-fancontrol.conf;"
        echo "  danach: sudo nginx -t && sudo systemctl reload nginx"
    fi
else
    echo "• Kein laufender Apache oder nginx gefunden. Die Oberfläche ist lokal unter"
    echo "  http://127.0.0.1:8077/fan/ erreichbar; Einbindung siehe README.md, Abschnitt \"Weboberfläche\"."
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
  6. Passwort für die Weboberfläche:   sudo cpu-fancontrol passwd
  7. Weboberfläche öffnen:             http://<server>/fan/
EOF
