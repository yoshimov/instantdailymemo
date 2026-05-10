package info.yoshimov;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private LinearLayout listLayout;

    /**
     * Builds the settings screen and loads available calendar choices.
     *
     * @param savedInstanceState previously saved Activity state, if Android is recreating this Activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSystemBars();
        buildUi();
        loadCalendars();
    }

    /**
     * Makes status and navigation bars readable against the white settings background.
     */
    private void showSystemBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /**
     * Creates the settings UI with a header, calendar list, and close button.
     */
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18) + statusBarHeight(), dp(20), dp(12) + navigationBarHeight());
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText(R.string.destination_calendar);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(20);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText(R.string.destination_calendar_note);
        note.setTextColor(Color.rgb(107, 114, 128));
        note.setTextSize(13);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(6);
        root.addView(note, noteParams);

        ScrollView scrollView = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, dp(16), 0, dp(16));
        scrollView.addView(listLayout);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        Button closeButton = new Button(this);
        closeButton.setText(R.string.close);
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        setContentView(root);
    }

    /**
     * Loads writable Google calendars and renders them as selectable rows.
     */
    private void loadCalendars() {
        listLayout.removeAllViews();
        if (!hasCalendarPermission()) {
            addMessage(getString(R.string.calendar_permission_missing));
            return;
        }

        List<CalendarChoice> choices = findCalendarChoices();
        if (choices.isEmpty()) {
            addMessage(getString(R.string.writable_google_calendar_not_found));
            return;
        }

        long selectedId = selectedCalendarId();
        for (CalendarChoice choice : choices) {
            addCalendarRow(choice, selectedId == choice.id);
        }
    }

    /**
     * Checks whether calendar read and write permissions are available.
     *
     * @return true when Calendar Provider can be queried and updated.
     */
    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Finds writable Google calendars that can be used as memo destinations.
     *
     * @return ordered list of calendar choices.
     */
    private List<CalendarChoice> findCalendarChoices() {
        List<CalendarChoice> choices = new ArrayList<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };
        String selection = CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        String[] args = {"com.google", String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        String sort = CalendarContract.Calendars.IS_PRIMARY + " DESC, "
                + CalendarContract.Calendars.VISIBLE + " DESC, "
                + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC";

        try (Cursor cursor = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                sort)) {
            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(1);
                choices.add(new CalendarChoice(
                        cursor.getLong(0),
                        name == null ? getString(R.string.untitled_calendar) : name,
                        cursor.getString(2)));
            }
        }
        return choices;
    }

    /**
     * Adds one selectable calendar row.
     *
     * @param choice calendar choice to render.
     * @param checked true when this row is the currently saved destination.
     */
    private void addCalendarRow(CalendarChoice choice, boolean checked) {
        RadioButton row = new RadioButton(this);
        row.setText(choice.label());
        row.setTextSize(16);
        row.setTextColor(Color.rgb(17, 24, 39));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setChecked(checked);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnClickListener(v -> {
            saveSelectedCalendar(choice.id);
            Toast.makeText(this, R.string.destination_changed, Toast.LENGTH_SHORT).show();
            finish();
        });
        listLayout.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * Adds a plain status message to the calendar list area.
     *
     * @param text message to display.
     */
    private void addMessage(String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(15);
        message.setTextColor(Color.rgb(107, 114, 128));
        message.setPadding(0, dp(16), 0, dp(16));
        listLayout.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * Reads the currently selected calendar ID from app preferences.
     *
     * @return selected calendar ID, or -1 when no explicit destination is saved.
     */
    private long selectedCalendarId() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        return prefs.getLong(MainActivity.KEY_CALENDAR_ID, -1L);
    }

    /**
     * Saves the selected calendar ID into app preferences.
     *
     * @param calendarId calendar ID to use as the memo destination.
     */
    private void saveSelectedCalendar(long calendarId) {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(MainActivity.KEY_CALENDAR_ID, calendarId).apply();
    }

    /**
     * Converts density-independent pixels to physical pixels.
     *
     * @param value density-independent pixel value.
     * @return rounded physical pixel value for this device.
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Reads the system navigation bar height so the close button stays visible.
     *
     * @return navigation bar height in pixels, or 0 when unavailable.
     */
    private int navigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * Reads the system status bar height so the header avoids the camera and status area.
     *
     * @return status bar height in pixels, or 0 when unavailable.
     */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    /**
     * Calendar option shown in the settings list.
     */
    private static class CalendarChoice {
        final long id;
        final String name;
        final String account;

        /**
         * Creates one calendar choice.
         *
         * @param id Calendar Provider ID.
         * @param name display name shown by Calendar Provider.
         * @param account Google account name.
         */
        CalendarChoice(long id, String name, String account) {
            this.id = id;
            this.name = name;
            this.account = account == null ? "" : account;
        }

        /**
         * Formats the display label for the settings row.
         *
         * @return calendar name with account when available.
         */
        String label() {
            return account.isEmpty() ? name : name + "\n" + account;
        }
    }
}
