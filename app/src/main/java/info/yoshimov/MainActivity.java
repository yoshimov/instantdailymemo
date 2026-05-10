package info.yoshimov;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 10;
    private static final String EVENT_TITLE = "memo";
    static final String PREFS_NAME = "instant_daily_memo";
    static final String KEY_CALENDAR_ID = "calendar_id";

    private EditText memoEdit;
    private TextView statusText;
    private Button saveButton;
    private Button menuButton;
    private Button closeButton;
    private LinearLayout rootLayout;
    private View scrollThumb;
    private long loadedEventId = -1L;
    private long loadedCalendarId = -1L;
    private boolean loadingText;
    private boolean dirty;
    private boolean didInitialResume;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = new Runnable() {
        @Override public void run() {
            saveIfReady(false);
        }
    };

    /**
     * Initializes the fast memo editor, then loads today's memo after calendar permission is available.
     *
     * @param savedInstanceState previously saved Activity state, if Android is recreating this Activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        showSystemBars();
        buildUi();

        if (hasCalendarPermission()) {
            loadMemo();
        } else {
            statusText.setText("カレンダー権限が必要です");
            requestPermissions(new String[]{
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
            }, REQ_CALENDAR);
        }
    }

    /**
     * Reloads the memo when returning from settings, so the selected calendar is reflected immediately.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!didInitialResume) {
            didInitialResume = true;
            return;
        }
        if (memoEdit != null && hasCalendarPermission() && !dirty) {
            loadMemo();
        }
    }

    /**
     * Makes status and navigation bars visible on the app's white background.
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
     * Flushes any pending autosave before the app is backgrounded.
     */
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autosave);
        saveIfReady(false);
    }

    /**
     * Continues loading the memo once calendar permission is granted.
     *
     * @param requestCode request identifier passed to {@link #requestPermissions(String[], int)}.
     * @param permissions permissions returned by Android.
     * @param grantResults grant results returned by Android.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR && hasCalendarPermission()) {
            loadMemo();
        } else {
            statusText.setText("権限を許可すると Google Calendar に保存できます");
        }
    }

    /**
     * Builds the whole editor UI in code so the first screen can appear with minimal startup work.
     */
    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        updateRootPadding(0);
        rootLayout.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(todayLabel());
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setTextSize(18);
        title.setGravity(Gravity.START);
        title.setOnClickListener(v -> openCalendarForToday());
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f));

        menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(22);
        menuButton.setContentDescription("設定");
        menuButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(menuButton, new LinearLayout.LayoutParams(
                dp(48),
                dp(48)));

        rootLayout.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setText("読み込み中...");
        statusText.setTextColor(Color.rgb(107, 114, 128));
        statusText.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(4);
        rootLayout.addView(statusText, statusParams);

        memoEdit = new EditText(this);
        memoEdit.setHint("今日の日記");
        memoEdit.setTextSize(18);
        memoEdit.setGravity(Gravity.TOP | Gravity.START);
        memoEdit.setSingleLine(false);
        memoEdit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        memoEdit.setImeOptions(EditorInfo.IME_ACTION_NONE);
        memoEdit.setMinLines(12);
        memoEdit.setBackgroundColor(Color.TRANSPARENT);
        memoEdit.setPadding(0, dp(18), 0, 0);
        memoEdit.setVerticalScrollBarEnabled(false);
        memoEdit.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        memoEdit.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        memoEdit.setScrollContainer(true);
        memoEdit.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        memoEdit.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        memoEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loadingText) {
                    dirty = true;
                    statusText.setText("未保存");
                    handler.removeCallbacks(autosave);
                    handler.postDelayed(autosave, 900);
                    memoEdit.post(MainActivity.this::updateScrollThumb);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        memoEdit.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> updateScrollThumb());

        FrameLayout memoFrame = new FrameLayout(this);
        memoFrame.addView(memoEdit, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View scrollTrack = new View(this);
        scrollTrack.setBackground(new ColorDrawable(Color.rgb(229, 231, 235)));
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(
                dp(3),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT);
        trackParams.topMargin = dp(18);
        memoFrame.addView(scrollTrack, trackParams);

        scrollThumb = new View(this);
        scrollThumb.setBackground(new ColorDrawable(Color.rgb(37, 99, 235)));
        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(
                dp(3),
                dp(48),
                Gravity.RIGHT | Gravity.TOP);
        thumbParams.topMargin = dp(18);
        memoFrame.addView(scrollThumb, thumbParams);
        memoFrame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollThumb());

        rootLayout.addView(memoFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(8), 0, 0);

        saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setOnClickListener(v -> saveMemo(true, false, true));
        buttons.addView(saveButton, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f));

        closeButton = new Button(this);
        closeButton.setText("終了");
        closeButton.setOnClickListener(v -> saveMemo(false, true, true));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f);
        closeParams.leftMargin = dp(12);
        buttons.addView(closeButton, closeParams);

        rootLayout.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(rootLayout);
        watchKeyboardInsets();
        memoEdit.requestFocus();
        memoEdit.postDelayed(new Runnable() {
            @Override public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(memoEdit, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 120);
    }

    /**
     * Watches for IME visibility changes and keeps the editor controls above the keyboard.
     */
    private void watchKeyboardInsets() {
        final View content = getWindow().getDecorView().findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                Rect visibleFrame = new Rect();
                content.getWindowVisibleDisplayFrame(visibleFrame);
                int hiddenHeight = content.getRootView().getHeight() - visibleFrame.bottom;
                int keyboardHeight = hiddenHeight > dp(120) ? hiddenHeight : 0;
                updateRootPadding(keyboardHeight);
            }
        });
    }

    /**
     * Applies safe top and bottom padding, including extra space for the IME when it is visible.
     *
     * @param keyboardHeight current keyboard height in pixels, or 0 when hidden.
     */
    private void updateRootPadding(int keyboardHeight) {
        int bottomInset = keyboardHeight > 0 ? keyboardHeight : navigationBarHeight();
        rootLayout.setPadding(dp(20), dp(18) + statusBarHeight(), dp(20), dp(12) + bottomInset);
    }

    /**
     * Checks whether both read and write calendar permissions are currently available.
     *
     * @return true when the app can read and update Calendar Provider data.
     */
    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Loads today's memo event from Google calendars and displays its description.
     */
    private void loadMemo() {
        statusText.setText("読み込み中...");
        new AsyncTask<Void, Void, MemoRecord>() {
            @Override protected MemoRecord doInBackground(Void... voids) {
                List<Long> calendarIds = findGoogleCalendarIds();
                if (calendarIds.isEmpty()) {
                    return new MemoRecord(-1L, -1L, null, "Google Calendar が見つかりません");
                }
                calendarIds = prioritizeSelectedCalendar(calendarIds);
                MemoRecord record = findTodayMemo(calendarIds);
                if (record == null) {
                    return new MemoRecord(-1L, calendarIds.get(0), "", "新規メモ");
                }
                return record;
            }

            @Override protected void onPostExecute(MemoRecord record) {
                loadedEventId = record.eventId;
                loadedCalendarId = record.calendarId;
                if (dirty) {
                    statusText.setText("編集中");
                    handler.removeCallbacks(autosave);
                    handler.postDelayed(autosave, 900);
                    return;
                }
                loadingText = true;
                memoEdit.setText(record.description == null ? "" : record.description);
                memoEdit.setSelection(memoEdit.getText().length());
                loadingText = false;
                memoEdit.post(MainActivity.this::updateScrollThumb);
                dirty = false;
                statusText.setText(record.status);
            }
        }.execute();
    }

    /**
     * Places the saved destination calendar first, falling back to the provider's preferred order.
     *
     * @param calendarIds writable Google calendar IDs.
     * @return reordered calendar IDs with the selected destination first when it is still available.
     */
    private List<Long> prioritizeSelectedCalendar(List<Long> calendarIds) {
        long selectedId = selectedCalendarId();
        if (selectedId < 0 || !calendarIds.contains(selectedId)) {
            return calendarIds;
        }

        List<Long> ordered = new ArrayList<>();
        ordered.add(selectedId);
        for (Long id : calendarIds) {
            if (id != selectedId) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    /**
     * Reads the saved calendar destination from preferences.
     *
     * @return selected calendar ID, or -1 when the user has not chosen one.
     */
    private long selectedCalendarId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getLong(KEY_CALENDAR_ID, -1L);
    }

    /**
     * Updates the custom blue scroll thumb that sits on the right side of the memo editor.
     */
    private void updateScrollThumb() {
        if (memoEdit == null || scrollThumb == null || memoEdit.getHeight() <= 0) {
            return;
        }

        int visibleHeight = memoEdit.getHeight() - memoEdit.getPaddingTop() - memoEdit.getPaddingBottom();
        int contentHeight = Math.max(memoEdit.getLineCount() * memoEdit.getLineHeight(), visibleHeight);
        int scrollRange = Math.max(contentHeight - visibleHeight, 0);
        int trackHeight = Math.max(visibleHeight, dp(48));
        int thumbHeight = scrollRange == 0
                ? trackHeight
                : Math.max(dp(36), trackHeight * visibleHeight / contentHeight);
        int maxThumbOffset = Math.max(trackHeight - thumbHeight, 0);
        int thumbOffset = scrollRange == 0
                ? 0
                : Math.min(maxThumbOffset, maxThumbOffset * memoEdit.getScrollY() / scrollRange);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) scrollThumb.getLayoutParams();
        params.height = thumbHeight;
        params.topMargin = dp(18) + thumbOffset;
        scrollThumb.setLayoutParams(params);
        scrollThumb.setVisibility(View.VISIBLE);
    }

    /**
     * Opens the device's default calendar app at today's date.
     */
    private void openCalendarForToday() {
        long today = Calendar.getInstance().getTimeInMillis();
        Uri uri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(String.valueOf(today))
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "カレンダーアプリが見つかりません", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Finds writable Google calendar IDs, preferring primary and visible calendars first.
     *
     * @return ordered list of calendar IDs that can hold the memo event.
     */
    private List<Long> findGoogleCalendarIds() {
        List<Long> ids = new ArrayList<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE
        };
        String selection = CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        String[] args = {"com.google", String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        String sort = CalendarContract.Calendars.IS_PRIMARY + " DESC, "
                + CalendarContract.Calendars.VISIBLE + " DESC";

        try (Cursor cursor = getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                sort)) {
            while (cursor != null && cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }

    /**
     * Finds today's event titled {@value #EVENT_TITLE} in one of the available Google calendars.
     *
     * @param calendarIds Google calendar IDs that should be considered.
     * @return matching memo event, or null when none exists for today.
     */
    private MemoRecord findTodayMemo(List<Long> calendarIds) {
        long[] localRange = todayRange();
        long[] allDayUtcRange = todayAllDayUtcRange();
        String[] projection = {
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.ALL_DAY
        };
        Set<Long> googleCalendarIds = new HashSet<>(calendarIds);
        String selection = CalendarContract.Events.TITLE + "=? AND (("
                + CalendarContract.Events.ALL_DAY + "=1 AND "
                + CalendarContract.Events.DTSTART + ">=? AND "
                + CalendarContract.Events.DTSTART + "<?) OR (("
                + CalendarContract.Events.ALL_DAY + " IS NULL OR "
                + CalendarContract.Events.ALL_DAY + "=0) AND "
                + CalendarContract.Events.DTSTART + ">=? AND "
                + CalendarContract.Events.DTSTART + "<?))";
        String[] args = {
                EVENT_TITLE,
                String.valueOf(allDayUtcRange[0]),
                String.valueOf(allDayUtcRange[1]),
                String.valueOf(localRange[0]),
                String.valueOf(localRange[1])
        };

        MemoRecord bestRecord = null;
        int bestRank = Integer.MAX_VALUE;
        try (Cursor cursor = getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                CalendarContract.Events.DTSTART + " ASC")) {
            while (cursor != null && cursor.moveToNext()) {
                long calendarId = cursor.getLong(1);
                long start = cursor.getLong(3);
                boolean allDay = cursor.getInt(4) == 1;
                boolean today = allDay
                        ? start >= allDayUtcRange[0] && start < allDayUtcRange[1]
                        : start >= localRange[0] && start < localRange[1];

                if (today && googleCalendarIds.contains(calendarId)) {
                    int rank = calendarIds.indexOf(calendarId);
                    if (rank >= 0 && rank < bestRank) {
                        bestRank = rank;
                        bestRecord = new MemoRecord(
                            cursor.getLong(0),
                            calendarId,
                            cursor.getString(2),
                            "読み込み済み");
                    }
                }
            }
        }
        return bestRecord;
    }

    /**
     * Saves the current text only when there are unsaved changes.
     *
     * @param showToast true to show a saved message after a successful manual save.
     */
    private void saveIfReady(final boolean showToast) {
        saveMemo(showToast, false, false);
    }

    /**
     * Saves the editor text into today's memo event, creating the event when necessary.
     *
     * @param showToast true to show a confirmation toast after a successful save.
     * @param finishAfterSave true to close the Activity once saving succeeds or no save is needed.
     * @param force true to save even when the editor is not marked dirty.
     */
    private void saveMemo(final boolean showToast, final boolean finishAfterSave, boolean force) {
        if ((!dirty && !force) || !hasCalendarPermission() || loadedCalendarId < 0) {
            if (finishAfterSave) {
                finish();
            } else if (showToast) {
                Toast.makeText(MainActivity.this, "保存済みです", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        final String text = memoEdit.getText().toString();
        dirty = false;
        statusText.setText("保存中...");
        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... voids) {
                if (loadedEventId >= 0) {
                    ContentValues values = new ContentValues();
                    values.put(CalendarContract.Events.DESCRIPTION, text);
                    Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, loadedEventId);
                    return getContentResolver().update(uri, values, null, null) > 0;
                }

                long[] range = todayAllDayUtcRange();
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.CALENDAR_ID, loadedCalendarId);
                values.put(CalendarContract.Events.TITLE, EVENT_TITLE);
                values.put(CalendarContract.Events.DESCRIPTION, text);
                values.put(CalendarContract.Events.DTSTART, range[0]);
                values.put(CalendarContract.Events.DTEND, range[1]);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                values.put(CalendarContract.Events.ALL_DAY, 1);
                Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                if (uri == null) {
                    return false;
                }
                loadedEventId = ContentUris.parseId(uri);
                return true;
            }

            @Override protected void onPostExecute(Boolean ok) {
                statusText.setText(ok ? "保存済み" : "保存に失敗しました");
                dirty = !ok;
                if (showToast && ok) {
                    Toast.makeText(MainActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
                }
                if (finishAfterSave && ok) {
                    finish();
                }
            }
        }.execute();
    }

    /**
     * Calculates the local device-time range for today.
     *
     * @return two-element array containing start-inclusive and end-exclusive epoch milliseconds.
     */
    private long[] todayRange() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    /**
     * Calculates the UTC date range required by Calendar Provider for all-day events.
     *
     * @return two-element array containing today's all-day UTC start and tomorrow's UTC start.
     */
    private long[] todayAllDayUtcRange() {
        Calendar localToday = Calendar.getInstance();
        Calendar start = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        start.clear();
        start.set(
                localToday.get(Calendar.YEAR),
                localToday.get(Calendar.MONTH),
                localToday.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    /**
     * Formats today's date for the editor header using the device locale.
     *
     * @return localized full date label.
     */
    private String todayLabel() {
        DateFormat format = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
        return format.format(Calendar.getInstance().getTime());
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
     * Reads the system navigation bar height so buttons do not overlap it.
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
     * Holds the calendar event data needed to populate and later update the memo.
     */
    private static class MemoRecord {
        final long eventId;
        final long calendarId;
        final String description;
        final String status;

        /**
         * Creates a memo record loaded from Calendar Provider or a placeholder for a new memo.
         *
         * @param eventId existing event ID, or -1 when the memo has not been created yet.
         * @param calendarId calendar ID that owns or should own the memo.
         * @param description event description to show in the editor.
         * @param status short status text for the UI.
         */
        MemoRecord(long eventId, long calendarId, String description, String status) {
            this.eventId = eventId;
            this.calendarId = calendarId;
            this.description = description;
            this.status = status;
        }
    }
}
