package de.kalkoen.arbeitszeit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int SCREEN_TODAY = 0;
    private static final int SCREEN_OVERVIEW = 1;
    private static final int SCREEN_DETAIL = 2;

    private static final int REQ_CSV = 101;
    private static final int REQ_BACKUP_SAVE = 102;
    private static final int REQ_BACKUP_OPEN = 103;

    private static final long FUTURE_TOLERANCE_MS = 60000L;
    private static final long UNDO_TIMEOUT_MS = 7000L;
    private static final int MAX_IMPORT_BYTES = 20 * 1024 * 1024;

    private static final String STATE_SCREEN = "screen";
    private static final String STATE_DETAIL_KEY = "detail_key";
    private static final String STATE_YEAR = "shown_year";
    private static final String STATE_MONTH = "shown_month";

    private Store store;

    private TextView tvTopSubtitle;
    private Button btnTabToday;
    private Button btnTabOverview;
    private LinearLayout todaySection;
    private LinearLayout overviewSection;
    private LinearLayout detailSection;

    private TextView tvStatus;
    private TextView tvWorked;
    private TextView tvBreak;
    private TextView tvWageToday;
    private TextView tvHourlyWage;
    private TextView tvTodayNote;
    private Button btnStart;
    private Button btnPause;
    private Button btnStop;
    private LinearLayout workEntriesContainer;
    private LinearLayout breakEntriesContainer;

    private TextView tvMonthLabel;
    private Button btnMonthPrev;
    private Button btnMonthNext;
    private TextView tvWeekSummary;
    private TextView tvMonthSummary;
    private TextView tvMonthOpen;
    private TextView tvOpenWage;
    private Button btnMarkMonthPaid;
    private LinearLayout daysContainer;

    private TextView tvDetailDate;
    private TextView tvDetailPaidStatus;
    private TextView tvDetailWorked;
    private TextView tvDetailBreak;
    private TextView tvDetailWage;
    private TextView tvDetailRate;
    private TextView tvDetailNote;
    private Button btnDetailTogglePaid;
    private LinearLayout detailWorkEntriesContainer;
    private LinearLayout detailBreakEntriesContainer;

    private View undoBar;
    private TextView tvUndoText;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int screen = SCREEN_TODAY;
    private String selectedDayKey;
    private int shownYear;
    private int shownMonth;
    private int exportYear;
    private int exportMonth;
    private long seenRevision = -1L;

    private String undoKey;
    private DayRecord undoSnapshot;
    private long undoRevision = -1L;
    private final Runnable hideUndoRunnable = new Runnable() {
        @Override
        public void run() {
            hideUndo();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean dayChanged = store.ensureCurrentDay();
            if (dayChanged || store.revision != seenRevision) {
                refreshCurrentScreen();
            } else {
                tickLiveValues();
            }
            if (undoSnapshot != null && store.revision != undoRevision) hideUndo();
            handler.postDelayed(this, 1000L);
        }
    };

    // ------------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = Store.get(this);
        bindViews();
        setupListeners();

        long now = System.currentTimeMillis();
        shownYear = DayTime.yearOf(now);
        shownMonth = DayTime.monthOf(now);

        int restoredScreen = SCREEN_TODAY;
        String restoredKey = null;
        if (savedInstanceState != null) {
            restoredScreen = savedInstanceState.getInt(STATE_SCREEN, SCREEN_TODAY);
            restoredKey = savedInstanceState.getString(STATE_DETAIL_KEY);
            shownYear = savedInstanceState.getInt(STATE_YEAR, shownYear);
            shownMonth = savedInstanceState.getInt(STATE_MONTH, shownMonth);
        }
        if (restoredScreen == SCREEN_DETAIL && DayTime.isValidKey(restoredKey)) {
            showDetailScreen(restoredKey);
        } else if (restoredScreen == SCREEN_OVERVIEW) {
            showOverviewScreen();
        } else {
            showTodayScreen();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SCREEN, screen);
        outState.putString(STATE_DETAIL_KEY, selectedDayKey);
        outState.putInt(STATE_YEAR, shownYear);
        outState.putInt(STATE_MONTH, shownMonth);
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.ensureCurrentDay();
        refreshCurrentScreen();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onBackPressed() {
        if (screen == SCREEN_DETAIL) {
            showOverviewScreen();
        } else if (screen == SCREEN_OVERVIEW) {
            showTodayScreen();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ Views

    private void bindViews() {
        tvTopSubtitle = findViewById(R.id.tvTopSubtitle);
        btnTabToday = findViewById(R.id.btnTabToday);
        btnTabOverview = findViewById(R.id.btnTabOverview);
        todaySection = findViewById(R.id.todaySection);
        overviewSection = findViewById(R.id.overviewSection);
        detailSection = findViewById(R.id.detailSection);

        tvStatus = findViewById(R.id.tvStatus);
        tvWorked = findViewById(R.id.tvWorked);
        tvBreak = findViewById(R.id.tvBreak);
        tvWageToday = findViewById(R.id.tvWageToday);
        tvHourlyWage = findViewById(R.id.tvHourlyWage);
        tvTodayNote = findViewById(R.id.tvTodayNote);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        workEntriesContainer = findViewById(R.id.workEntriesContainer);
        breakEntriesContainer = findViewById(R.id.breakEntriesContainer);

        tvMonthLabel = findViewById(R.id.tvMonthLabel);
        btnMonthPrev = findViewById(R.id.btnMonthPrev);
        btnMonthNext = findViewById(R.id.btnMonthNext);
        tvWeekSummary = findViewById(R.id.tvWeekSummary);
        tvMonthSummary = findViewById(R.id.tvMonthSummary);
        tvMonthOpen = findViewById(R.id.tvMonthOpen);
        tvOpenWage = findViewById(R.id.tvOpenWage);
        btnMarkMonthPaid = findViewById(R.id.btnMarkMonthPaid);
        daysContainer = findViewById(R.id.daysContainer);

        tvDetailDate = findViewById(R.id.tvDetailDate);
        tvDetailPaidStatus = findViewById(R.id.tvDetailPaidStatus);
        tvDetailWorked = findViewById(R.id.tvDetailWorked);
        tvDetailBreak = findViewById(R.id.tvDetailBreak);
        tvDetailWage = findViewById(R.id.tvDetailWage);
        tvDetailRate = findViewById(R.id.tvDetailRate);
        tvDetailNote = findViewById(R.id.tvDetailNote);
        btnDetailTogglePaid = findViewById(R.id.btnDetailTogglePaid);
        detailWorkEntriesContainer = findViewById(R.id.detailWorkEntriesContainer);
        detailBreakEntriesContainer = findViewById(R.id.detailBreakEntriesContainer);

        undoBar = findViewById(R.id.undoBar);
        tvUndoText = findViewById(R.id.tvUndoText);
    }

    private void setupListeners() {
        btnTabToday.setOnClickListener(v -> showTodayScreen());
        btnTabOverview.setOnClickListener(v -> showOverviewScreen());
        findViewById(R.id.btnBackOverview).setOnClickListener(v -> showOverviewScreen());

        btnStart.setOnClickListener(v -> {
            store.startOrResume();
            refreshToday();
        });
        btnPause.setOnClickListener(v -> {
            if (!store.togglePause()) {
                Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            }
            refreshToday();
        });
        btnStop.setOnClickListener(v -> confirmStop());
        findViewById(R.id.btnEditWage).setOnClickListener(v -> showHourlyWageDialog());
        findViewById(R.id.btnAddWork).setOnClickListener(v -> showEntryDialog(true, null, store.currentDayKey));
        findViewById(R.id.btnAddBreak).setOnClickListener(v -> showEntryDialog(false, null, store.currentDayKey));
        findViewById(R.id.btnTodayNote).setOnClickListener(v -> showNoteDialog(store.currentDayKey));

        btnMonthPrev.setOnClickListener(v -> shiftMonth(-1));
        btnMonthNext.setOnClickListener(v -> shiftMonth(1));
        btnMarkMonthPaid.setOnClickListener(v -> confirmMarkMonthPaid());
        findViewById(R.id.btnAddPastDay).setOnClickListener(v -> showAddDayPicker());
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> startCsvExport());
        findViewById(R.id.btnBackupSave).setOnClickListener(v -> startBackupSave());
        findViewById(R.id.btnBackupRestore).setOnClickListener(v -> startBackupRestore());

        btnDetailTogglePaid.setOnClickListener(v -> toggleSelectedDayPaid());
        findViewById(R.id.btnDetailEditRate).setOnClickListener(v -> {
            if (selectedDayKey != null) showDayRateDialog(selectedDayKey);
        });
        findViewById(R.id.btnDetailNote).setOnClickListener(v -> {
            if (selectedDayKey != null) showNoteDialog(selectedDayKey);
        });
        findViewById(R.id.btnDetailAddWork).setOnClickListener(v -> {
            if (selectedDayKey != null) showEntryDialog(true, null, selectedDayKey);
        });
        findViewById(R.id.btnDetailAddBreak).setOnClickListener(v -> {
            if (selectedDayKey != null) showEntryDialog(false, null, selectedDayKey);
        });
        findViewById(R.id.btnUndo).setOnClickListener(v -> performUndo());
    }

    // ------------------------------------------------------------------ Navigation

    private void showTodayScreen() {
        screen = SCREEN_TODAY;
        selectedDayKey = null;
        todaySection.setVisibility(View.VISIBLE);
        overviewSection.setVisibility(View.GONE);
        detailSection.setVisibility(View.GONE);
        setTabState(true);
        refreshToday();
    }

    private void showOverviewScreen() {
        screen = SCREEN_OVERVIEW;
        selectedDayKey = null;
        todaySection.setVisibility(View.GONE);
        overviewSection.setVisibility(View.VISIBLE);
        detailSection.setVisibility(View.GONE);
        setTabState(false);
        refreshOverview();
    }

    private void showDetailScreen(String key) {
        if (key == null) return;
        screen = SCREEN_DETAIL;
        selectedDayKey = key;
        todaySection.setVisibility(View.GONE);
        overviewSection.setVisibility(View.GONE);
        detailSection.setVisibility(View.VISIBLE);
        setTabState(false);
        refreshDetail();
    }

    private void setTabState(boolean todaySelected) {
        btnTabToday.setBackgroundResource(todaySelected ? R.drawable.tab_selected : R.drawable.btn_neutral);
        btnTabToday.setTextColor(getColor(todaySelected ? R.color.on_accent : R.color.text));
        btnTabOverview.setBackgroundResource(todaySelected ? R.drawable.btn_neutral : R.drawable.tab_selected);
        btnTabOverview.setTextColor(getColor(todaySelected ? R.color.text : R.color.on_accent));
    }

    private void refreshCurrentScreen() {
        if (screen == SCREEN_OVERVIEW) {
            refreshOverview();
        } else if (screen == SCREEN_DETAIL) {
            refreshDetail();
        } else {
            refreshToday();
        }
    }

    /** Nur Zahlenfelder aktualisieren – Listen werden nicht neu gebaut, damit Taps nicht verloren gehen. */
    private void tickLiveValues() {
        if (screen == SCREEN_TODAY) {
            refreshTodaySummary(false);
        } else if (screen == SCREEN_DETAIL && store.currentDayKey.equals(selectedDayKey)) {
            refreshDetailSummary();
        }
    }

    // ------------------------------------------------------------------ Heute

    private void confirmStop() {
        DayRecord record = store.today();
        if (!record.isRunning()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_stop_title)
                .setMessage(R.string.dialog_stop_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.stop, (d, w) -> {
                    store.stop();
                    refreshToday();
                })
                .show();
    }

    private void refreshToday() {
        seenRevision = store.revision;
        refreshTodaySummary(true);
        DayRecord record = store.today();
        rebuildEntryContainer(workEntriesContainer, true, record.work, store.currentDayKey);
        rebuildEntryContainer(breakEntriesContainer, false, record.breaks, store.currentDayKey);
    }

    private void refreshTodaySummary(boolean refreshButtons) {
        DayRecord record = store.today();
        long now = System.currentTimeMillis();

        tvTopSubtitle.setText(formatFullDate(store.currentDayKey));
        tvWorked.setText(Fmt.duration(Intervals.net(record, now)));
        tvBreak.setText(Fmt.duration(Intervals.effectiveBreak(record, now)));
        tvWageToday.setText(Fmt.currency(Intervals.wage(record, store.hourlyWage, now)));
        tvHourlyWage.setText(getString(R.string.per_hour, Fmt.currency(store.hourlyWage)));

        if (DayRecord.STATE_WORKING.equals(record.state)) {
            tvStatus.setText(R.string.status_working);
            tvStatus.setTextColor(getColor(R.color.green));
        } else if (DayRecord.STATE_PAUSED.equals(record.state)) {
            tvStatus.setText(R.string.status_paused);
            tvStatus.setTextColor(getColor(R.color.orange));
        } else {
            tvStatus.setText(record.work.isEmpty() ? R.string.status_not_started : R.string.status_stopped);
            tvStatus.setTextColor(getColor(R.color.muted));
        }

        if (refreshButtons) {
            boolean working = DayRecord.STATE_WORKING.equals(record.state);
            boolean paused = DayRecord.STATE_PAUSED.equals(record.state);
            boolean stopped = DayRecord.STATE_STOPPED.equals(record.state);
            btnStart.setEnabled(!working);
            btnStart.setText((paused || (stopped && !record.work.isEmpty())) ? R.string.resume : R.string.start);
            btnPause.setEnabled(working || paused);
            btnPause.setText(paused ? R.string.pause_end : R.string.pause);
            btnStop.setEnabled(working || paused);
            setNoteText(tvTodayNote, record.note);
        }
    }

    private void setNoteText(TextView view, String note) {
        if (note == null || note.length() == 0) {
            view.setText(R.string.note_none);
            view.setTextColor(getColor(R.color.muted));
        } else {
            view.setText(note);
            view.setTextColor(getColor(R.color.text));
        }
    }

    // ------------------------------------------------------------------ Übersicht

    private boolean isCurrentMonthShown() {
        long now = System.currentTimeMillis();
        return shownYear == DayTime.yearOf(now) && shownMonth == DayTime.monthOf(now);
    }

    private void shiftMonth(int delta) {
        if (delta > 0 && isCurrentMonthShown()) return;
        shownMonth += delta;
        if (shownMonth < 0) {
            shownMonth = 11;
            shownYear--;
        } else if (shownMonth > 11) {
            shownMonth = 0;
            shownYear++;
        }
        refreshOverview();
    }

    private void refreshOverview() {
        seenRevision = store.revision;
        tvTopSubtitle.setText(R.string.subtitle_overview);
        long now = System.currentTimeMillis();
        long weekStart = DayTime.startOfWeek(now);
        String prefix = DayTime.monthPrefix(shownYear, shownMonth);

        long weekMs = 0L;
        long monthMs = 0L;
        double weekWage = 0.0;
        double monthWage = 0.0;
        double monthOpenWage = 0.0;
        double openWage = 0.0;
        boolean monthHasOpenStoppedDay = false;
        ArrayList<String> monthKeys = new ArrayList<String>();

        for (Map.Entry<String, DayRecord> item : store.days.entrySet()) {
            DayRecord record = item.getValue();
            if (!record.hasMeaningfulData()) continue;
            String key = item.getKey();
            long worked = Intervals.net(record, now);
            double wage = Intervals.wage(record, store.hourlyWage, now);
            if (DayTime.millisForDayKey(key) >= weekStart) {
                weekMs += worked;
                weekWage += wage;
            }
            if (key.startsWith(prefix)) {
                monthKeys.add(key);
                monthMs += worked;
                monthWage += wage;
                if (!record.paid) {
                    monthOpenWage += wage;
                    if (!record.isRunning()) monthHasOpenStoppedDay = true;
                }
            }
            if (!record.paid) openWage += wage;
        }

        tvWeekSummary.setText(Fmt.shortDuration(weekMs) + "  •  " + Fmt.currency(weekWage));
        tvMonthSummary.setText(Fmt.shortDuration(monthMs) + "  •  " + Fmt.currency(monthWage));
        tvMonthOpen.setText(getString(R.string.month_open, Fmt.currency(monthOpenWage)));
        tvOpenWage.setText(Fmt.currency(openWage));
        tvMonthLabel.setText(DayTime.monthLabel(shownYear, shownMonth));
        btnMonthNext.setEnabled(!isCurrentMonthShown());
        btnMarkMonthPaid.setEnabled(monthHasOpenStoppedDay);

        daysContainer.removeAllViews();
        Collections.sort(monthKeys, Collections.reverseOrder());
        if (monthKeys.isEmpty()) {
            daysContainer.addView(makeEmptyText(getString(R.string.empty_month)));
            return;
        }
        for (String key : monthKeys) {
            daysContainer.addView(makeDayRow(key));
        }
    }

    private View makeDayRow(final String key) {
        DayRecord record = store.ensureDay(key);
        long now = System.currentTimeMillis();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.card_bg);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showDetailScreen(key));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(7);
        card.setLayoutParams(cardLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView date = new TextView(this);
        date.setText(formatHistoryDate(key));
        date.setTextColor(getColor(R.color.text));
        date.setTextSize(16f);
        date.setTypeface(null, Typeface.BOLD);
        date.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView paid = new TextView(this);
        paid.setText(record.paid ? R.string.badge_paid : R.string.badge_open);
        paid.setTextColor(getColor(record.paid ? R.color.green : R.color.orange));
        paid.setTextSize(12f);
        paid.setTypeface(null, Typeface.BOLD);
        paid.setBackgroundResource(record.paid ? R.drawable.status_paid : R.drawable.status_open);

        top.addView(date);
        top.addView(paid);
        card.addView(top);

        TextView line = new TextView(this);
        String span = formatDaySpan(record, now);
        String text = getString(R.string.row_work_break,
                Fmt.shortDuration(Intervals.net(record, now)),
                Fmt.shortDuration(Intervals.effectiveBreak(record, now)))
                + "\n" + Fmt.currency(Intervals.wage(record, store.hourlyWage, now));
        if (span.length() > 0) text += "  •  " + span;
        if (record.note != null && record.note.length() > 0) text += "\n✎ " + firstLine(record.note, 60);
        line.setText(text);
        line.setTextColor(getColor(R.color.muted));
        line.setTextSize(13f);
        line.setPadding(0, dp(7), 0, 0);
        card.addView(line);
        return card;
    }

    private static String firstLine(String text, int max) {
        int nl = text.indexOf('\n');
        String line = nl >= 0 ? text.substring(0, nl) + "…" : text;
        return line.length() > max ? line.substring(0, max) + "…" : line;
    }

    private void confirmMarkMonthPaid() {
        final ArrayList<String> keys = new ArrayList<String>();
        double sum = 0.0;
        long now = System.currentTimeMillis();
        String prefix = DayTime.monthPrefix(shownYear, shownMonth);
        for (Map.Entry<String, DayRecord> e : store.days.entrySet()) {
            DayRecord r = e.getValue();
            if (!e.getKey().startsWith(prefix) || !r.hasMeaningfulData() || r.paid || r.isRunning()) continue;
            keys.add(e.getKey());
            sum += Intervals.wage(r, store.hourlyWage, now);
        }
        if (keys.isEmpty()) {
            Toast.makeText(this, R.string.toast_nothing_open, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_mark_month_title)
                .setMessage(getString(R.string.dialog_mark_month_message, keys.size(),
                        DayTime.monthLabel(shownYear, shownMonth), Fmt.currency(sum)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.mark_paid, (d, w) -> {
                    long when = System.currentTimeMillis();
                    for (String key : keys) store.ensureDay(key).markPaid(when);
                    store.save();
                    refreshOverview();
                })
                .show();
    }

    private void showAddDayPicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.clear();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    String key = DayTime.dayKey(selected.getTimeInMillis());
                    store.ensureDay(key);
                    store.save();
                    if (key.equals(store.currentDayKey)) showTodayScreen();
                    else showDetailScreen(key);
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.setTitle(R.string.dialog_pick_day);
        picker.show();
    }

    // ------------------------------------------------------------------ Detail

    private void refreshDetail() {
        if (selectedDayKey == null) return;
        seenRevision = store.revision;
        tvTopSubtitle.setText(R.string.subtitle_detail);
        refreshDetailSummary();
        DayRecord record = store.ensureDay(selectedDayKey);
        setNoteText(tvDetailNote, record.note);
        rebuildEntryContainer(detailWorkEntriesContainer, true, record.work, selectedDayKey);
        rebuildEntryContainer(detailBreakEntriesContainer, false, record.breaks, selectedDayKey);
    }

    private void refreshDetailSummary() {
        if (selectedDayKey == null) return;
        DayRecord record = store.ensureDay(selectedDayKey);
        long now = System.currentTimeMillis();

        tvDetailDate.setText(formatFullDate(selectedDayKey));
        tvDetailWorked.setText(Fmt.shortDuration(Intervals.net(record, now)));
        tvDetailBreak.setText(Fmt.shortDuration(Intervals.effectiveBreak(record, now)));
        tvDetailWage.setText(Fmt.currency(Intervals.wage(record, store.hourlyWage, now)));
        tvDetailRate.setText(getString(R.string.per_hour, Fmt.currency(record.rate)));

        if (record.paid) {
            tvDetailPaidStatus.setText(record.paidAt > 0L
                    ? getString(R.string.detail_paid_on, Fmt.date("dd.MM.yyyy", Fmt.DE, record.paidAt))
                    : getString(R.string.badge_paid));
        } else {
            tvDetailPaidStatus.setText(R.string.detail_open);
        }
        tvDetailPaidStatus.setTextColor(getColor(record.paid ? R.color.green : R.color.orange));
        tvDetailPaidStatus.setBackgroundResource(record.paid ? R.drawable.status_paid : R.drawable.status_open);

        boolean canMarkPaid = DayRecord.STATE_STOPPED.equals(record.state);
        btnDetailTogglePaid.setEnabled(canMarkPaid);
        if (!canMarkPaid) {
            btnDetailTogglePaid.setText(R.string.detail_stop_first);
        } else {
            btnDetailTogglePaid.setText(record.paid ? R.string.mark_open : R.string.mark_paid);
        }
    }

    private void toggleSelectedDayPaid() {
        if (selectedDayKey == null) return;
        DayRecord record = store.ensureDay(selectedDayKey);
        if (record.paid) record.markUnpaid();
        else record.markPaid(System.currentTimeMillis());
        store.save();
        refreshDetail();
    }

    // ------------------------------------------------------------------ Einträge-Listen

    private void rebuildEntryContainer(LinearLayout container, boolean workType,
                                       ArrayList<TimeEntry> entries, String key) {
        container.removeAllViews();
        if (entries.isEmpty()) {
            container.addView(makeEmptyText(getString(workType ? R.string.empty_work : R.string.empty_break)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            container.addView(makeEntryRow(workType, entries.get(i), i, key));
        }
    }

    private View makeEmptyText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.muted));
        tv.setTextSize(14f);
        tv.setPadding(dp(4), dp(8), dp(4), dp(8));
        return tv;
    }

    private View makeEntryRow(final boolean workType, final TimeEntry entry, int index, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        row.setBackgroundResource(R.drawable.card_bg);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);
        row.setLayoutParams(rowLp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(getString(workType ? R.string.entry_work_n : R.string.entry_break_n, index + 1));
        title.setTextColor(getColor(R.color.text));
        title.setTextSize(15f);
        title.setTypeface(null, Typeface.BOLD);

        TextView time = new TextView(this);
        String endText = entry.end == 0L ? getString(R.string.running) : Fmt.time(entry.end);
        long duration = (entry.end == 0L ? System.currentTimeMillis() : entry.end) - entry.start;
        time.setText(Fmt.time(entry.start) + " – " + endText + "   •   " + Fmt.shortDuration(duration));
        time.setTextColor(getColor(R.color.muted));
        time.setTextSize(13f);

        textBox.addView(title);
        textBox.addView(time);
        row.addView(textBox);

        Button edit = smallButton(getString(R.string.edit));
        edit.setOnClickListener(v -> showEntryDialog(workType, entry, key));
        row.addView(edit);

        Button delete = smallButton(getString(R.string.delete));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        deleteLp.leftMargin = dp(6);
        delete.setLayoutParams(deleteLp);
        delete.setOnClickListener(v -> deleteEntry(workType, entry, key));
        row.addView(delete);
        return row;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setTextColor(getColor(R.color.text));
        button.setBackgroundResource(R.drawable.btn_neutral);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return button;
    }

    // ------------------------------------------------------------------ Löschen + Rückgängig

    private void deleteEntry(boolean workType, TimeEntry entry, String key) {
        DayRecord record = store.ensureDay(key);
        DayRecord snapshot = record.copy();
        long now = System.currentTimeMillis();
        boolean active = entry.end == 0L;

        if (workType) {
            record.work.remove(entry);
            if (active) Intervals.closeActive(record.breaks, now);
            Intervals.cleanupBreaksToWork(record, now);
        } else {
            record.breaks.remove(entry);
            Intervals.sanitizeState(record, now);
        }
        record.markUnpaid();
        store.save();
        refreshCurrentScreen();
        showUndo(key, snapshot, workType ? R.string.undo_work_deleted : R.string.undo_break_deleted);
    }

    private void showUndo(String key, DayRecord snapshot, int messageRes) {
        undoKey = key;
        undoSnapshot = snapshot;
        undoRevision = store.revision;
        tvUndoText.setText(messageRes);
        undoBar.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideUndoRunnable);
        handler.postDelayed(hideUndoRunnable, UNDO_TIMEOUT_MS);
    }

    private void hideUndo() {
        handler.removeCallbacks(hideUndoRunnable);
        undoBar.setVisibility(View.GONE);
        undoSnapshot = null;
        undoKey = null;
    }

    private void performUndo() {
        if (undoSnapshot == null || undoKey == null) {
            hideUndo();
            return;
        }
        if (store.revision != undoRevision) {
            // Zwischenzeitlich wurde etwas anderes geändert – Wiederherstellen würde diese Änderung überschreiben.
            Toast.makeText(this, R.string.toast_undo_not_possible, Toast.LENGTH_SHORT).show();
            hideUndo();
            return;
        }
        store.days.put(undoKey, undoSnapshot);
        hideUndo();
        store.save();
        refreshCurrentScreen();
    }

    // ------------------------------------------------------------------ Dialoge: Lohn, Notiz

    private void showHourlyWageDialog() {
        showWageDialog(getString(R.string.dialog_default_wage_title), store.hourlyWage, value -> {
            store.hourlyWage = value;
            DayRecord current = store.today();
            if (!current.paid) current.rate = value;
            store.save();
            refreshToday();
        });
    }

    private void showDayRateDialog(final String key) {
        final DayRecord record = store.ensureDay(key);
        showWageDialog(getString(R.string.dialog_day_wage_title, formatShortDate(key)), record.rate, value -> {
            record.rate = value;
            record.markUnpaid();
            store.save();
            refreshDetail();
        });
    }

    private interface WageCallback {
        void apply(double value);
    }

    private void showWageDialog(String title, double initial, final WageCallback callback) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(Fmt.decimal(Math.max(0.0, initial)));
        input.setSelectAllOnFocus(true);
        input.setHint(R.string.wage_hint);
        input.setPadding(dp(8), dp(12), dp(8), dp(12));

        LinearLayout box = dialogBox();
        box.addView(dialogHint(getString(R.string.wage_dialog_hint)));
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = input.getText().toString().trim().replace(',', '.');
            try {
                double value = Double.parseDouble(raw);
                if (Double.isNaN(value) || value < 0 || value > 10000) {
                    input.setError(getString(R.string.error_wage_invalid));
                    return;
                }
                callback.apply(Math.round(value * 100.0) / 100.0);
                dialog.dismiss();
            } catch (NumberFormatException ex) {
                input.setError(getString(R.string.error_number_invalid));
            }
        }));
        dialog.show();
    }

    private void showNoteDialog(final String key) {
        final DayRecord record = store.ensureDay(key);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        input.setMinLines(2);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setText(record.note == null ? "" : record.note);
        input.setHint(R.string.note_hint);

        LinearLayout box = dialogBox();
        box.addView(dialogHint(getString(R.string.note_dialog_hint, formatShortDate(key))));
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.note_title)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    record.note = input.getText().toString().trim();
                    store.save();
                    refreshCurrentScreen();
                })
                .show();
    }

    // ------------------------------------------------------------------ Dialog: Zeiten

    /** Ein Teilstück eines Eintrags auf genau einem Kalendertag. */
    private static final class Part {
        final String key;
        final long start;
        final long end;

        Part(String key, long start, long end) {
            this.key = key;
            this.start = start;
            this.end = end;
        }
    }

    private void showEntryDialog(final boolean workType, final TimeEntry existing, final String key) {
        final boolean editing = existing != null;
        final boolean active = editing && existing.end == 0L;

        long now = System.currentTimeMillis();
        long defaultStart;
        long defaultEnd;
        if (editing) {
            defaultStart = existing.start;
            defaultEnd = existing.end == 0L ? now : existing.end;
        } else if (key.equals(store.currentDayKey)) {
            long dayStart = DayTime.startOfDay(now);
            defaultStart = Math.max(dayStart, now - (workType ? 60L : 30L) * 60L * 1000L);
            defaultEnd = now;
        } else {
            defaultStart = DayTime.timeOnDay(key, workType ? 8 : 12, 0);
            defaultEnd = DayTime.timeOnDay(key, workType ? 16 : 12, workType ? 0 : 30);
        }

        final EditText startInput = timeInput(Fmt.time(defaultStart), getString(R.string.time_hint));
        final EditText endInput = timeInput(active ? "" : Fmt.time(defaultEnd),
                active ? getString(R.string.running) : getString(R.string.time_hint));
        endInput.setEnabled(!active);

        LinearLayout box = dialogBox();
        int hintRes;
        if (workType) {
            hintRes = active ? R.string.hint_work_active : (editing ? R.string.hint_work_edit : R.string.hint_work_add);
        } else {
            hintRes = active ? R.string.hint_break_active : (editing ? R.string.hint_break_edit : R.string.hint_break_add);
        }
        box.addView(dialogHint(getString(hintRes, formatShortDate(key))));
        box.addView(fieldLabel(getString(R.string.field_start)));
        box.addView(startInput);
        box.addView(fieldLabel(getString(R.string.field_end)));
        box.addView(endInput);

        int titleRes;
        if (workType) titleRes = editing ? R.string.dialog_edit_work : R.string.dialog_add_work;
        else titleRes = editing ? R.string.dialog_edit_break : R.string.dialog_add_break;

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Long start = parseTimeForDay(key, startInput.getText().toString());
            if (start == null) {
                startInput.setError(getString(R.string.error_start_format));
                return;
            }
            long nowV = System.currentTimeMillis();
            if (start > nowV + FUTURE_TOLERANCE_MS) {
                startInput.setError(getString(R.string.error_start_future));
                return;
            }

            long end = 0L;
            boolean overnight = false;
            if (!active) {
                Long parsedEnd = parseTimeForDay(key, endInput.getText().toString());
                if (parsedEnd == null) {
                    endInput.setError(getString(R.string.error_end_format));
                    return;
                }
                end = parsedEnd;
                if (end == start) {
                    endInput.setError(getString(R.string.error_end_before_start));
                    return;
                }
                if (end < start) {
                    if (editing) {
                        endInput.setError(getString(R.string.error_end_before_start));
                        return;
                    }
                    overnight = true;
                }
            }

            ArrayList<Part> parts = new ArrayList<Part>();
            if (overnight) {
                long boundary = DayTime.startOfNextDay(start);
                long endNext = DayTime.addDays(end, 1);
                if (endNext > nowV + FUTURE_TOLERANCE_MS) {
                    endInput.setError(getString(R.string.error_end_future));
                    return;
                }
                parts.add(new Part(key, start, boundary));
                if (endNext > boundary) parts.add(new Part(DayTime.dayKey(boundary), boundary, endNext));
            } else {
                if (end != 0L && end > nowV + FUTURE_TOLERANCE_MS) {
                    endInput.setError(getString(R.string.error_end_future));
                    return;
                }
                parts.add(new Part(key, start, end));
            }

            // Erst alles prüfen, dann schreiben.
            for (Part p : parts) {
                DayRecord rec = store.ensureDay(p.key);
                long validationEnd = p.end == 0L ? nowV : p.end;
                if (workType) {
                    if (Intervals.hasOverlap(rec.work, p.start, validationEnd, existing, nowV)) {
                        startInput.setError(getString(R.string.error_work_overlap));
                        return;
                    }
                } else {
                    if (Intervals.hasOverlap(rec.breaks, p.start, validationEnd, existing, nowV)) {
                        startInput.setError(getString(R.string.error_break_overlap));
                        return;
                    }
                    if (!Intervals.isCoveredByWork(rec, p.start, validationEnd, nowV)) {
                        startInput.setError(getString(R.string.error_break_not_covered));
                        return;
                    }
                }
            }

            for (Part p : parts) {
                DayRecord rec = store.ensureDay(p.key);
                ArrayList<TimeEntry> list = workType ? rec.work : rec.breaks;
                if (editing) {
                    existing.start = p.start;
                    existing.end = p.end;
                } else {
                    list.add(new TimeEntry(p.start, p.end));
                }
                Intervals.sort(list);
                if (workType) Intervals.cleanupBreaksToWork(rec, nowV);
                rec.markUnpaid();
            }
            store.save();
            refreshCurrentScreen();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private EditText timeInput(String text, String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(text);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        return input;
    }

    /** Akzeptiert H:mm, HH:mm sowie H.mm. */
    private Long parseTimeForDay(String key, String value) {
        if (value == null) return null;
        String[] parts = value.trim().replace('.', ':').split(":");
        if (parts.length != 2) return null;
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return DayTime.timeOnDay(key, hour, minute);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Export / Backup

    private void startCsvExport() {
        long now = System.currentTimeMillis();
        String prefix = DayTime.monthPrefix(shownYear, shownMonth);
        boolean any = false;
        for (Map.Entry<String, DayRecord> e : store.days.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().hasMeaningfulData()) {
                any = true;
                break;
            }
        }
        if (!any) {
            Toast.makeText(this, R.string.toast_no_data_month, Toast.LENGTH_SHORT).show();
            return;
        }
        exportYear = shownYear;
        exportMonth = shownMonth;
        String name = "arbeitszeit-" + prefix.substring(0, prefix.length() - 1) + ".csv";
        launchCreateDocument("text/csv", name, REQ_CSV);
    }

    private void startBackupSave() {
        String name = "arbeitszeit-backup-" + DayTime.dayKey(System.currentTimeMillis()) + ".json";
        launchCreateDocument("application/json", name, REQ_BACKUP_SAVE);
    }

    private void startBackupRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_BACKUP_OPEN);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void launchCreateDocument(String mime, String name, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.toast_no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_CSV) {
                String csv = Export.csv(store.days, exportYear, exportMonth, store.hourlyWage,
                        System.currentTimeMillis());
                writeText(uri, "﻿" + csv);
                Toast.makeText(this, R.string.toast_csv_saved, Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_BACKUP_SAVE) {
                writeText(uri, store.backupJson());
                Toast.makeText(this, R.string.toast_backup_saved, Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_BACKUP_OPEN) {
                DayJson.Parsed parsed = DayJson.parse(readText(uri));
                if (parsed.days.isEmpty()) {
                    Toast.makeText(this, R.string.toast_backup_invalid, Toast.LENGTH_LONG).show();
                } else {
                    confirmImport(parsed);
                }
            }
        } catch (Exception ex) {
            Toast.makeText(this, requestCode == REQ_BACKUP_OPEN
                    ? R.string.toast_backup_invalid : R.string.toast_file_error, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmImport(final DayJson.Parsed parsed) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_restore_title)
                .setMessage(getString(R.string.dialog_restore_message, parsed.days.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> {
                    store.importBackup(parsed);
                    hideUndo();
                    Toast.makeText(this, R.string.toast_backup_restored, Toast.LENGTH_SHORT).show();
                    showTodayScreen();
                })
                .show();
    }

    private void writeText(Uri uri, String text) throws IOException {
        OutputStream out = getContentResolver().openOutputStream(uri, "wt");
        if (out == null) throw new IOException("no stream");
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
    }

    private String readText(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("no stream");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_IMPORT_BYTES) throw new IOException("too large");
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(4));
        return box;
    }

    private TextView dialogHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.muted));
        tv.setTextSize(13f);
        tv.setPadding(0, 0, 0, dp(12));
        return tv;
    }

    private TextView fieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.text));
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(8), 0, 0);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatFullDate(String key) {
        return Fmt.date("EEEE, dd.MM.yyyy", Fmt.DE, DayTime.millisForDayKey(key));
    }

    private String formatHistoryDate(String key) {
        if (key.equals(store.currentDayKey)) return getString(R.string.today_date, formatShortDate(key));
        return Fmt.date("EEE, dd.MM.yyyy", Fmt.DE, DayTime.millisForDayKey(key));
    }

    private String formatShortDate(String key) {
        return Fmt.date("dd.MM.yyyy", Fmt.DE, DayTime.millisForDayKey(key));
    }

    private String formatDaySpan(DayRecord record, long now) {
        if (record.work.isEmpty()) return "";
        long first = Long.MAX_VALUE;
        long last = 0L;
        boolean active = false;
        for (TimeEntry e : record.work) {
            first = Math.min(first, e.start);
            if (e.end == 0L) {
                active = true;
                last = Math.max(last, now);
            } else {
                last = Math.max(last, e.end);
            }
        }
        return Fmt.time(first) + "–" + (active ? getString(R.string.running) : Fmt.time(last));
    }
}
