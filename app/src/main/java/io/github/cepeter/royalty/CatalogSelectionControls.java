package io.github.cepeter.royalty;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import io.github.cepeter.royalty.core.AccountInventory;
import io.github.cepeter.royalty.core.CatalogEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small native control group for the catalog's independent view options. */
final class CatalogSelectionControls extends LinearLayout {
    private final Spinner account;
    private final Switch hiddenOnly;
    private final TextView count;
    private final Button select;
    private final Button undo;
    private final List<Integer> slots = new ArrayList<>();
    private List<String> currentLabels = Collections.emptyList();
    private Runnable changed;
    private boolean updating;

    CatalogSelectionControls(Context context, Runnable changed, Runnable selectMatching, Runnable undoAction) {
        super(context);
        this.changed = changed;
        setOrientation(VERTICAL);
        account = new Spinner(context);
        addView(account, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        account.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!updating) CatalogSelectionControls.this.changed.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        hiddenOnly = new Switch(context);
        hiddenOnly.setText("Hidden only");
        hiddenOnly.setOnCheckedChangeListener((button, checked) -> changed.run());
        addView(hiddenOnly);
        count = new TextView(context);
        addView(count);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        select = new Button(context);
        select.setText("Select matching");
        select.setOnClickListener(view -> selectMatching.run());
        actions.addView(select, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        undo = new Button(context);
        undo.setText("Undo");
        undo.setOnClickListener(view -> undoAction.run());
        actions.addView(undo, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(actions);
    }
    int accountFilter() {
        int position = account.getSelectedItemPosition();
        return position >= 0 && position < slots.size() ? slots.get(position) : -1;
    }
    boolean hiddenOnly() { return hiddenOnly.isChecked(); }
    void update(AccountInventory inventory, List<CatalogEntry> catalog, int selected, int undoCount, boolean enabled) {
        List<Integer> nextSlots = new ArrayList<>();
        nextSlots.add(-1);
        List<String> labels = new ArrayList<>();
        labels.add("All accounts");
        List<Integer> sorted = new ArrayList<>(inventory.owners().keySet());
        for (CatalogEntry entry : catalog) if (!sorted.contains(entry.key().account()))
            sorted.add(entry.key().account());
        Collections.sort(sorted);
        for (int slot : sorted) {
            nextSlots.add(slot);
            AccountInventory.Owner owner = inventory.owner(slot);
            labels.add(owner == null ? "Account " + (slot + 1) + " · saved selection" : owner.label());
        }
        if (!labels.equals(currentLabels)) {
            int previous = accountFilter();
            updating = true;
            slots.clear();
            slots.addAll(nextSlots);
            currentLabels = labels;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            account.setAdapter(adapter);
            int selectedPosition = slots.indexOf(previous);
            account.setSelection(selectedPosition < 0 ? 0 : selectedPosition);
            updating = false;
        }
        count.setText(selected + " selected" + (inventory.complete() ? "" : " · account inventory incomplete"));
        select.setEnabled(enabled && inventory.complete());
        undo.setEnabled(enabled && undoCount > 0);
    }
}
