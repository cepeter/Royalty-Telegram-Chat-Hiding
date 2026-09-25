package io.github.cepeter.royalty;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import io.github.cepeter.royalty.catalog.CatalogProtocol;
import io.github.cepeter.royalty.catalog.CatalogRepository;
import io.github.cepeter.royalty.catalog.CatalogRequestClient;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.cepeter.royalty.config.XposedPreferenceService;
import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private final List<CatalogEntry> catalog = new ArrayList<>();
    private final List<CatalogEntry> visibleCatalog = new ArrayList<>();
    private final Set<DialogKey> selectedDialogs = new HashSet<>();
    private boolean preferencesAvailable;
    private boolean catalogUpdatesRegistered;
    private boolean catalogRequestTimedOut;
    private long requestExpiresAt;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final XposedPreferenceService.Listener preferenceListener = current ->
            mainHandler.post(() -> {
                preferences = current;
                renderCached();
            });
    private final Runnable catalogTimeout = () -> {
        if (requestExpiresAt != 0 && SystemClock.elapsedRealtime() >= requestExpiresAt) {
            catalogRequestTimedOut = true;
            requestExpiresAt = 0;
            renderCached();
        }
    };
    private final BroadcastReceiver catalogUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            catalogRequestTimedOut = false;
            requestExpiresAt = 0;
            mainHandler.removeCallbacks(catalogTimeout);
            renderCached();
        }
    };

    private CatalogRepository catalogRepository;
    private SharedPreferences preferences;
    private TextView frameworkStatusDot;
    private TextView frameworkStatusText;
    private TextView telegramStatusDot;
    private TextView telegramStatusText;
    private EditText searchInput;
    private ListView dialogList;
    private ArrayAdapter<String> dialogAdapter;
    private Switch notificationSwitch;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        catalogRepository = new CatalogRepository(this);
        setContentView(buildContentView());
        XposedPreferenceService.subscribe(preferenceListener);
    }

    @Override
    protected void onDestroy() {
        XposedPreferenceService.unsubscribe(preferenceListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerCatalogUpdates();
        renderCached();
        requestCatalog();
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(catalogTimeout);
        if (catalogUpdatesRegistered) {
            unregisterReceiver(catalogUpdatedReceiver);
            catalogUpdatesRegistered = false;
        }
        super.onPause();
    }

    private View buildContentView() {
        configureSystemBars();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.royalty_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        applySystemInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this);
        mark.setText("R");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(getColor(R.color.royalty_on_primary));
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setBackground(roundedDrawable(R.color.royalty_primary, 23, 0));
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        headingParams.setMarginStart(dp(14));

        TextView eyebrow = createText(
                R.string.dashboard_eyebrow, 12, R.color.royalty_primary, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        heading.addView(eyebrow, matchWrap());

        TextView title = createText(R.string.app_name, 30, R.color.royalty_text, Typeface.BOLD);
        heading.addView(title, matchWrap());

        TextView subtitle = createText(
                R.string.dashboard_subtitle, 14, R.color.royalty_text_muted, Typeface.NORMAL);
        heading.addView(subtitle, matchWrap());
        header.addView(heading, headingParams);
        root.addView(header, matchWrap());

        TextView scope = createText(
                R.string.supported_scope, 12, R.color.royalty_text_muted, Typeface.NORMAL);
        scope.setLineSpacing(0, 1.15f);
        root.addView(scope, withTopMargin(matchWrap(), 12));

        root.addView(createSectionLabel(R.string.status_section), withTopMargin(matchWrap(), 20));

        LinearLayout statusCard = createCard(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout statusRows = new LinearLayout(this);
        statusRows.setOrientation(LinearLayout.VERTICAL);
        frameworkStatusDot = createStatusDot();
        frameworkStatusText = createText(0, 14, R.color.royalty_text, Typeface.BOLD);
        statusRows.addView(createConnectionRow(
                frameworkStatusDot, frameworkStatusText), matchWrap());
        telegramStatusDot = createStatusDot();
        telegramStatusText = createText(0, 14, R.color.royalty_text, Typeface.BOLD);
        statusRows.addView(createConnectionRow(
                telegramStatusDot, telegramStatusText), withTopMargin(matchWrap(), 6));
        statusCard.addView(statusRows, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refreshButton = createSecondaryButton(R.string.refresh);
        refreshButton.setOnClickListener(view -> requestCatalog());
        statusCard.addView(refreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(statusCard, withTopMargin(matchWrap(), 8));

        LinearLayout notificationCard = createCard(LinearLayout.HORIZONTAL);
        notificationCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout notificationCopy = new LinearLayout(this);
        notificationCopy.setOrientation(LinearLayout.VERTICAL);
        notificationCopy.addView(createText(
                R.string.notifications_title, 16, R.color.royalty_text, Typeface.BOLD), matchWrap());
        notificationCopy.addView(createText(
                R.string.notifications_subtitle, 13, R.color.royalty_text_muted, Typeface.NORMAL),
                withTopMargin(matchWrap(), 3));
        notificationCard.addView(notificationCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        notificationSwitch = new Switch(this);
        notificationSwitch.setContentDescription(getString(R.string.suppress_notifications));
        notificationSwitch.setShowText(false);
        notificationSwitch.setMinimumHeight(dp(48));
        notificationCard.addView(notificationSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(notificationCard, withTopMargin(matchWrap(), 12));

        root.addView(createSectionLabel(R.string.hidden_chats_section), withTopMargin(matchWrap(), 20));
        TextView chatsSubtitle = createText(
                R.string.hidden_chats_subtitle, 13, R.color.royalty_text_muted, Typeface.NORMAL);
        root.addView(chatsSubtitle, withTopMargin(matchWrap(), 3));

        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_chats_hint);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextColor(getColor(R.color.royalty_text));
        searchInput.setHintTextColor(getColor(R.color.royalty_text_muted));
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setMinimumHeight(dp(48));
        searchInput.setBackground(roundedDrawable(R.color.royalty_surface, 16, 1));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                applyCatalogFilter(text.toString());
            }
        });
        root.addView(searchInput, withTopMargin(matchWrap(), 10));

        dialogList = new ListView(this);
        dialogList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        dialogList.setBackground(roundedDrawable(R.color.royalty_surface, 18, 1));
        dialogList.setDivider(new ColorDrawable(getColor(R.color.royalty_outline)));
        dialogList.setDividerHeight(dp(1));
        dialogList.setClipToOutline(true);
        dialogList.setPadding(0, dp(4), 0, dp(4));
        dialogList.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        dialogList.setNestedScrollingEnabled(true);
        dialogAdapter = createDialogAdapter();
        dialogList.setAdapter(dialogAdapter);
        dialogList.setOnItemClickListener((parent, view, position, id) -> {
            DialogKey key = visibleCatalog.get(position).key();
            if (dialogList.isItemChecked(position)) {
                selectedDialogs.add(key);
            } else {
                selectedDialogs.remove(key);
            }
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        listParams.topMargin = dp(8);
        root.addView(dialogList, listParams);

        saveButton = createPrimaryButton(R.string.save);
        saveButton.setOnClickListener(view -> saveConfiguration());
        root.addView(saveButton, withTopMargin(matchWrap(), 12));

        TextView hint = createText(
                R.string.refresh_hint, 12, R.color.royalty_text_muted, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hint, withTopMargin(matchWrap(), 8));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void registerCatalogUpdates() {
        if (catalogUpdatesRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(CatalogProtocol.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(catalogUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerLegacyCatalogReceiver(filter);
        }
        catalogUpdatesRegistered = true;
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyCatalogReceiver(IntentFilter filter) {
        registerReceiver(catalogUpdatedReceiver, filter);
    }

    private void requestCatalog() {
        catalogRequestTimedOut = false;
        renderCached();
        mainHandler.removeCallbacks(catalogTimeout);
        try {
            requestExpiresAt = CatalogRequestClient.request(this);
            long delay = Math.max(0, requestExpiresAt - SystemClock.elapsedRealtime());
            mainHandler.postDelayed(catalogTimeout, delay + 100);
        } catch (RuntimeException error) {
            requestExpiresAt = 0;
            catalogRequestTimedOut = true;
            renderCached();
        }
    }

    private void renderCached() {
        HiddenConfig config = HiddenConfig.empty();
        preferencesAvailable = preferences != null;
        if (preferencesAvailable) {
            try {
                config = ConfigStore.load(preferences);
            } catch (RuntimeException error) {
                preferencesAvailable = false;
            }
        }

        catalog.clear();
        catalog.addAll(catalogRepository.loadCatalog());
        addMissingSelections(config.hiddenDialogs());
        java.util.Collections.sort(catalog);

        selectedDialogs.clear();
        selectedDialogs.addAll(config.hiddenDialogs());
        applyCatalogFilter(searchInput.getText().toString());

        setSuppressNotifications(config.suppressNotifications());
        saveButton.setEnabled(preferencesAvailable);
        updateConnectionStatus(catalogRepository.loadHookStatuses());
    }

    private void addMissingSelections(Set<DialogKey> selected) {
        Set<DialogKey> present = new HashSet<>();
        for (CatalogEntry entry : catalog) {
            present.add(entry.key());
        }
        for (DialogKey key : selected) {
            if (!present.contains(key)) {
                catalog.add(new CatalogEntry(key, "Unavailable from current catalog"));
            }
        }
    }

    private void saveConfiguration() {
        try {
            boolean saved = ConfigStore.save(
                    preferences, new HashSet<>(selectedDialogs), notificationSwitch.isChecked());
            if (!saved) {
                showError(getString(R.string.save_failed));
                return;
            }
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            preferencesAvailable = false;
            saveButton.setEnabled(false);
            showError(getString(R.string.framework_inactive));
        }
    }

    private void setSuppressNotifications(boolean enabled) {
        notificationSwitch.setChecked(enabled);
    }

    private String formatEntry(CatalogEntry entry) {
        return entry.title() + "\nID " + entry.key().dialogId();
    }

    private void applyCatalogFilter(String rawQuery) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        visibleCatalog.clear();
        dialogAdapter.clear();
        for (CatalogEntry entry : catalog) {
            String title = entry.title().toLowerCase(Locale.ROOT);
            String dialogId = Long.toString(entry.key().dialogId());
            if (!query.isEmpty() && !title.contains(query) && !dialogId.contains(query)) {
                continue;
            }
            visibleCatalog.add(entry);
            dialogAdapter.add(formatEntry(entry));
        }
        dialogAdapter.notifyDataSetChanged();
        dialogList.clearChoices();
        for (int index = 0; index < visibleCatalog.size(); index++) {
            dialogList.setItemChecked(index, selectedDialogs.contains(visibleCatalog.get(index).key()));
        }
    }

    private void updateConnectionStatus(Map<String, String> statuses) {
        boolean telegramWorking = !catalogRequestTimedOut && !statuses.isEmpty();
        for (String status : statuses.values()) {
            if (!"installed".equals(status)) {
                telegramWorking = false;
                break;
            }
        }
        boolean unsupportedVersion = statuses.containsValue("unsupported_version");
        setConnectionStatus(
                frameworkStatusDot,
                frameworkStatusText,
                R.string.framework_connection,
                preferencesAvailable);
        setConnectionStatus(
                telegramStatusDot,
                telegramStatusText,
                unsupportedVersion ? R.string.telegram_unsupported : R.string.telegram_connection,
                telegramWorking);
    }

    private void setConnectionStatus(
            TextView dot, TextView label, int labelResource, boolean working) {
        dot.setTextColor(getColor(
                working ? R.color.royalty_success : R.color.royalty_error));
        label.setText(getString(labelResource)
                + " · "
                + getString(working ? R.string.connection_working : R.string.connection_not_working));
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    dp(20) + insets.getSystemWindowInsetLeft(),
                    dp(16) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(),
                    dp(16) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.royalty_background));
        getWindow().setNavigationBarColor(getColor(R.color.royalty_background));
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        boolean darkIcons = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (darkIcons) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private TextView createText(int stringResource, int sizeSp, int colorResource, int style) {
        TextView view = new TextView(this);
        if (stringResource != 0) {
            view.setText(stringResource);
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(getColor(colorResource));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private TextView createSectionLabel(int stringResource) {
        TextView label = createText(stringResource, 13, R.color.royalty_text, Typeface.BOLD);
        label.setLetterSpacing(0.04f);
        return label;
    }

    private TextView createStatusDot() {
        TextView dot = createText(0, 18, R.color.royalty_error, Typeface.BOLD);
        dot.setText("●");
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return dot;
    }

    private LinearLayout createConnectionRow(TextView dot, TextView label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(dot, new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout createCard(int orientation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(orientation);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedDrawable(R.color.royalty_surface, 18, 1));
        card.setElevation(dp(2));
        return card;
    }

    private Button createPrimaryButton(int stringResource) {
        Button button = createButton(stringResource);
        button.setTextColor(getColor(R.color.royalty_on_primary));
        button.setBackground(rippleBackground(R.color.royalty_primary, 16, 0));
        return button;
    }

    private Button createSecondaryButton(int stringResource) {
        Button button = createButton(stringResource);
        button.setTextColor(getColor(R.color.royalty_primary));
        button.setBackground(rippleBackground(R.color.royalty_surface_variant, 14, 0));
        return button;
    }

    private Button createButton(int stringResource) {
        Button button = new Button(this);
        button.setText(stringResource);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(20), 0, dp(20), 0);
        button.setStateListAnimator(null);
        return button;
    }

    private ArrayAdapter<String> createDialogAdapter() {
        ColorStateList checkColors = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {getColor(R.color.royalty_primary), getColor(R.color.royalty_text_muted)});
        return new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                row.setTextColor(getColor(R.color.royalty_text));
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(64));
                row.setPadding(dp(16), dp(8), dp(12), dp(8));
                row.setCheckMarkTintList(checkColors);
                row.setBackgroundColor(Color.TRANSPARENT);
                return row;
            }
        };
    }

    private Drawable rippleBackground(int colorResource, int radiusDp, int strokeDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.royalty_ripple)),
                roundedDrawable(colorResource, radiusDp, strokeDp),
                null);
    }

    private GradientDrawable roundedDrawable(int colorResource, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(getColor(colorResource));
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), getColor(R.color.royalty_outline));
        }
        return drawable;
    }

    private LinearLayout.LayoutParams withTopMargin(
            LinearLayout.LayoutParams params, int marginDp) {
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
