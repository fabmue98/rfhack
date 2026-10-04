package de.kalkoen.arbeitszeit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

/**
 * Homescreen-Widget: Netto-Arbeitszeit des Tages (läuft live per Chronometer), Verdienst
 * sowie Start/Pause/Stop. Der Verdienst wird bei jeder Aktion und – solange gearbeitet
 * wird – ungefähr einmal pro Minute aktualisiert.
 */
public class TimerWidgetProvider extends AppWidgetProvider {

    private static final long TICK_MS = 60L * 1000L;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        Store.get(context).ensureCurrentDay();
        RemoteViews views = build(context);
        for (int id : appWidgetIds) manager.updateAppWidget(id, views);
        scheduleTick(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelTick(context);
    }

    /** Alle platzierten Widgets neu zeichnen. */
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TimerWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            cancelTick(context);
            return;
        }
        manager.updateAppWidget(ids, build(context));
        scheduleTick(context);
    }

    private static DayRecord todayRecord(Store store, long now) {
        DayRecord record = store.days.get(DayTime.dayKey(now));
        return record != null ? record : new DayRecord();
    }

    static RemoteViews build(Context context) {
        Store store = Store.get(context);
        long now = System.currentTimeMillis();
        DayRecord record = todayRecord(store, now);
        long netMs = Intervals.net(record, now);
        boolean working = DayRecord.STATE_WORKING.equals(record.state);
        boolean paused = DayRecord.STATE_PAUSED.equals(record.state);

        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_timer);

        v.setChronometer(R.id.wChrono, SystemClock.elapsedRealtime() - netMs, null, working);
        v.setTextViewText(R.id.wWage, Fmt.currency(Intervals.wage(record, store.hourlyWage, now)));
        v.setTextViewText(R.id.wBreak, context.getString(R.string.widget_break,
                Fmt.hoursMinutes(Intervals.effectiveBreak(record, now))));

        int statusText;
        int statusColor;
        if (working) {
            statusText = R.string.status_working;
            statusColor = R.color.green;
        } else if (paused) {
            statusText = R.string.status_paused;
            statusColor = R.color.orange;
        } else {
            statusText = record.work.isEmpty() ? R.string.status_not_started : R.string.status_stopped_short;
            statusColor = R.color.muted;
        }
        v.setTextViewText(R.id.wStatus, context.getString(statusText));
        v.setTextColor(R.id.wStatus, context.getColor(statusColor));

        v.setTextViewText(R.id.wBtnPlay, context.getString(paused ? R.string.widget_resume : R.string.widget_start));
        v.setBoolean(R.id.wBtnPlay, "setEnabled", !working);
        v.setBoolean(R.id.wBtnPause, "setEnabled", working);
        v.setBoolean(R.id.wBtnStop, "setEnabled", working || paused);

        v.setOnClickPendingIntent(R.id.wBtnPlay, actionIntent(context, WidgetActionReceiver.ACTION_START, 1));
        v.setOnClickPendingIntent(R.id.wBtnPause, actionIntent(context, WidgetActionReceiver.ACTION_PAUSE, 2));
        v.setOnClickPendingIntent(R.id.wBtnStop, actionIntent(context, WidgetActionReceiver.ACTION_STOP, 3));

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.wInfo, PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static PendingIntent actionIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, WidgetActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleTick(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent tick = actionIntent(context, WidgetActionReceiver.ACTION_TICK, 4);
        long now = System.currentTimeMillis();
        DayRecord record = todayRecord(Store.get(context), now);
        if (DayRecord.STATE_WORKING.equals(record.state)) {
            // Ungenauer Alarm: braucht keine Sonderberechtigung und schont den Akku.
            alarms.set(AlarmManager.RTC, now + TICK_MS, tick);
        } else {
            alarms.cancel(tick);
        }
    }

    private static void cancelTick(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(actionIntent(context, WidgetActionReceiver.ACTION_TICK, 4));
    }
}
