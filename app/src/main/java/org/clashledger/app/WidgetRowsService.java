package org.clashledger.app;

import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** A launcher-hosted scrolling collection keeps every upgrade accessible at any widget size. */
public final class WidgetRowsService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            final List<RemoteViews> rows = new ArrayList<>();
            @Override public void onCreate() { }
            @Override public void onDataSetChanged() {
                rows.clear(); rows.addAll(buildRows(WidgetRowsService.this));
            }
            @Override public void onDestroy() { rows.clear(); }
            @Override public int getCount() { return rows.size(); }
            @Override public RemoteViews getViewAt(int position) { return position >= 0 && position < rows.size() ? rows.get(position) : null; }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public long getItemId(int position) { return position; }
            @Override public boolean hasStableIds() { return false; }
        };
    }
    public static List<RemoteViews> buildRows(android.content.Context context) {
        List<RemoteViews> rows = new ArrayList<>();
        Ledger ledger = new Ledger(context);
        long now = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        String pkg = context.getPackageName();
        String style=context.getSharedPreferences("display",0).getString("timerStyle","compact");
        List<Upgrade> ordered = new ArrayList<>(ledger.upgrades);
        ordered.sort(java.util.Comparator.comparingLong(u -> u.wallRemaining(now, ledger.boostUntil)));
        String group = "Builders";
        RemoteViews section = new RemoteViews(pkg, R.layout.widget_section);
        section.setTextViewText(R.id.section, group.toUpperCase(java.util.Locale.ROOT) + "  " + ledger.busy(now) + "/" + ledger.builders);
        section.setOnClickFillInIntent(R.id.section, new Intent()); rows.add(section);
        boolean any = false;
        for (Upgrade u : ordered) {
            if (!u.group.equals(group)) continue;
            any = true;
            RemoteViews row = new RemoteViews(pkg, R.layout.widget_row);
            long left = u.wallRemaining(now, ledger.boostUntil);
            row.setTextViewText(R.id.name, u.name + (u.goblin ? " · G" : "")+(u.helperLevel>0?" · H"+u.helperLevel:""));
            row.setTextViewText(R.id.level, "→ " + u.level);
            row.setViewVisibility(R.id.artwork, View.GONE);
            row.setOnClickFillInIntent(R.id.widget_row, new Intent());
            row.setChronometerCountDown(R.id.countdown, true);
            row.setChronometer(R.id.countdown, elapsed + left, null, left > 0 && style.equals("clock"));
            if(!style.equals("clock")||left==0)row.setTextViewText(R.id.countdown,TimerFormat.format(left,style));
            row.setProgressBar(R.id.progress, 1000, u.progress(now, ledger.boostUntil), false);
            row.setOnClickFillInIntent(R.id.name, new Intent());
            row.setOnClickFillInIntent(R.id.countdown, new Intent());
            rows.add(row);
        }
        if (!any) {
            RemoteViews empty = new RemoteViews(pkg, R.layout.widget_section);
            empty.setTextViewText(R.id.section, "No builders · tap to add");
            empty.setOnClickFillInIntent(R.id.section, new Intent()); rows.add(empty);
        }
        return rows;
    }
}
