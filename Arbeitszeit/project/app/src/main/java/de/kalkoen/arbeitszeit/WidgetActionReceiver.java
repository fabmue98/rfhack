package de.kalkoen.arbeitszeit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Nimmt die Widget-Tasten entgegen. Nicht exportiert: nur das Widget selbst kann es auslösen. */
public class WidgetActionReceiver extends BroadcastReceiver {
    static final String ACTION_START = "de.kalkoen.arbeitszeit.action.START";
    static final String ACTION_PAUSE = "de.kalkoen.arbeitszeit.action.PAUSE";
    static final String ACTION_STOP = "de.kalkoen.arbeitszeit.action.STOP";
    static final String ACTION_TICK = "de.kalkoen.arbeitszeit.action.TICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return;
        Store store = Store.get(context);
        if (ACTION_START.equals(action)) {
            store.startOrResume();
        } else if (ACTION_PAUSE.equals(action)) {
            store.startPause();
        } else if (ACTION_STOP.equals(action)) {
            store.stop();
        } else if (ACTION_TICK.equals(action)) {
            store.ensureCurrentDay();
        } else {
            return;
        }
        TimerWidgetProvider.refresh(context);
    }
}
