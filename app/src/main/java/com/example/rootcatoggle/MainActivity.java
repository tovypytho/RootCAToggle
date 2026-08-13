package com.example.rootcatoggle;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final List<TrustedCa> all = new ArrayList<>();
    private final List<TrustedCa> filtered = new ArrayList<>();

    private RootShell root;
    private RootTrustStore store;
    private CertificateRepository repository;
    private BulkOperation bulk;
    private boolean rootGranted;
    private boolean busy;
    private boolean integrityValid;

    private TextView rootStatus;
    private TextView summary;
    private EditText search;
    private Spinner vendorSpinner;
    private ListView list;
    private CaAdapter adapter;
    private Button requestRoot;
    private Button enableAll;
    private Button disableAll;
    private Button enableVendor;
    private Button disableVendor;
    private Button enableSelected;
    private Button disableSelected;
    private Button selectTommyPreset;
    private Button clearSelection;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        integrityValid = AppIntegrity.isValid(this);
        buildUi();
        if (!integrityValid) {
            rootStatus.setText("Integrity: INVALID SIGNATURE");
            summary.setText("This APK was re-signed or repackaged. Root write controls are locked.");
            requestRoot.setEnabled(false);
            setWriteControls(false);
            new AlertDialog.Builder(this)
                    .setTitle("App integrity check failed")
                    .setMessage("The installed APK signature does not match the signer embedded at build time. Use the original Tommy build.")
                    .setCancelable(false)
                    .setPositiveButton("Close", (d, w) -> finish())
                    .show();
            return;
        }
        requestRootAndLoad();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(10));
        header.setBackgroundColor(Color.rgb(38, 50, 56));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Root CA Toggle");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, 1);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView watermark = new TextView(this);
        watermark.setText("Tommy");
        watermark.setTextColor(Color.rgb(176, 190, 197));
        watermark.setTextSize(12);
        watermark.setTypeface(null, 1);
        watermark.setGravity(Gravity.END);
        watermark.setAlpha(0.78f);
        if (android.os.Build.VERSION.SDK_INT >= 21) watermark.setLetterSpacing(0.10f);
        titleRow.addView(watermark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(titleRow);

        rootStatus = new TextView(this);
        rootStatus.setText("Root: checking...");
        rootStatus.setTextColor(Color.LTGRAY);
        rootStatus.setTextSize(13);
        header.addView(rootStatus);
        rootLayout.addView(header);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(6));

        summary = new TextView(this);
        summary.setText("Loading system trusted credentials...");
        summary.setTextSize(14);
        summary.setPadding(dp(4), dp(2), dp(4), dp(6));
        controls.addView(summary);

        requestRoot = new Button(this);
        requestRoot.setText("REQUEST ROOT / RELOAD");
        requestRoot.setOnClickListener(v -> requestRootAndLoad());
        controls.addView(requestRoot, fullWidth());

        LinearLayout globalRow = horizontalRow();
        enableAll = button("ENABLE ALL", v -> performBulk("Enable all system CAs", new ArrayList<>(all), true));
        disableAll = button("DISABLE ALL", v -> performBulk("Disable all system CAs", new ArrayList<>(all), false));
        globalRow.addView(enableAll, weight());
        globalRow.addView(disableAll, weight());
        controls.addView(globalRow);

        search = new EditText(this);
        search.setHint("Search CN, organization, vendor, fingerprint...");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        controls.addView(search, fullWidth());

        vendorSpinner = new Spinner(this);
        vendorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { applyFilter(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        controls.addView(vendorSpinner, fullWidth());

        LinearLayout vendorRow = horizontalRow();
        enableVendor = button("ENABLE VENDOR", v -> performVendor(true));
        disableVendor = button("DISABLE VENDOR", v -> performVendor(false));
        vendorRow.addView(enableVendor, weight());
        vendorRow.addView(disableVendor, weight());
        controls.addView(vendorRow);

        LinearLayout presetRow = horizontalRow();
        selectTommyPreset = button("SELECT TOMMY PRESET", v -> selectTommyPreset());
        clearSelection = button("CLEAR SELECTION", v -> clearSelection());
        presetRow.addView(selectTommyPreset, weight());
        presetRow.addView(clearSelection, weight());
        controls.addView(presetRow);

        LinearLayout selectedRow = horizontalRow();
        enableSelected = button("ENABLE SELECTED", v -> performSelected(true));
        disableSelected = button("DISABLE SELECTED", v -> performSelected(false));
        selectedRow.addView(enableSelected, weight());
        selectedRow.addView(disableSelected, weight());
        controls.addView(selectedRow);

        rootLayout.addView(controls);

        list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new CaAdapter();
        list.setAdapter(adapter);
        rootLayout.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);
        setWriteControls(false);
    }

    private void requestRootAndLoad() {
        if (!integrityValid) return;
        setWriteControls(false);
        rootStatus.setText("Root: requesting/checking...");
        summary.setText("Reading Android system CA store...");
        worker.execute(() -> {
            try {
                if (root == null) root = new RootShell();
                boolean granted = root.requestRoot();
                if (!granted) throw new Exception("Root access was not granted by su");
                store = new RootTrustStore(this, root);
                repository = new CertificateRepository(store);
                bulk = new BulkOperation(store);
                List<TrustedCa> loaded = repository.loadSystemCertificates();
                main.post(() -> {
                    rootGranted = true;
                    rootStatus.setText("Root: GRANTED • Android " + android.os.Build.VERSION.RELEASE);
                    all.clear();
                    all.addAll(loaded);
                    rebuildVendorSpinner();
                    applyFilter();
                    setWriteControls(true);
                    Toast.makeText(this, "Loaded " + all.size() + " system CAs", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    rootGranted = false;
                    rootStatus.setText("Root: NOT AVAILABLE");
                    summary.setText("Cannot manage trust store: " + e.getMessage());
                    setWriteControls(false);
                });
            }
        });
    }

    private void setWriteControls(boolean enabled) {
        enableAll.setEnabled(enabled);
        disableAll.setEnabled(enabled);
        enableVendor.setEnabled(enabled);
        disableVendor.setEnabled(enabled);
        enableSelected.setEnabled(enabled);
        disableSelected.setEnabled(enabled);
        selectTommyPreset.setEnabled(enabled);
        clearSelection.setEnabled(enabled);
    }

    private void rebuildVendorSpinner() {
        String previous = selectedVendor();
        Set<String> unique = new LinkedHashSet<>();
        for (TrustedCa ca : all) unique.add(ca.vendor);
        List<String> vendors = new ArrayList<>(unique);
        Collections.sort(vendors, String.CASE_INSENSITIVE_ORDER);
        vendors.add(0, "All vendors");
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, vendors);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vendorSpinner.setAdapter(spinnerAdapter);
        if (previous != null) {
            int p = vendors.indexOf(previous);
            if (p >= 0) vendorSpinner.setSelection(p);
        }
    }

    private String selectedVendor() {
        Object value = vendorSpinner == null ? null : vendorSpinner.getSelectedItem();
        return value == null ? "All vendors" : value.toString();
    }

    private void applyFilter() {
        if (adapter == null) return;
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.US);
        String vendor = selectedVendor();
        filtered.clear();
        for (TrustedCa ca : all) {
            if (!"All vendors".equals(vendor) && !vendor.equals(ca.vendor)) continue;
            String haystack = (ca.title() + " " + ca.organization + " " + ca.vendor + " " + ca.fileName + " " + ca.sha256).toLowerCase(Locale.US);
            if (!q.isEmpty() && !haystack.contains(q)) continue;
            filtered.add(ca);
        }
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void updateSummary() {
        int enabled = 0;
        int selected = 0;
        for (TrustedCa ca : all) {
            if (ca.enabled) enabled++;
            if (ca.selected) selected++;
        }
        summary.setText(enabled + " / " + all.size() + " system CAs enabled • " + selected + " selected • showing " + filtered.size());
    }


    private void selectTommyPreset() {
        if (all.isEmpty()) {
            Toast.makeText(this, "No system certificates loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        TommyPreset.Result result = TommyPreset.apply(all);
        applyFilter();

        StringBuilder message = new StringBuilder();
        message.append("Selected ").append(result.selectedCount).append(" system CA(s).\n\n");
        message.append("This only changes the selection. Nothing has been disabled yet. ")
                .append("Review the checked entries, then press DISABLE SELECTED when ready.");

        if (!result.unmatchedRules.isEmpty()) {
            message.append("\n\nNot found on this ROM/image (no selection made for these rules):");
            int limit = Math.min(12, result.unmatchedRules.size());
            for (int i = 0; i < limit; i++) {
                message.append("\n• ").append(result.unmatchedRules.get(i));
            }
            if (result.unmatchedRules.size() > limit) {
                message.append("\n• … and ").append(result.unmatchedRules.size() - limit).append(" more");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Tommy preset selected")
                .setMessage(message.toString())
                .setPositiveButton("Review selection", null)
                .show();
    }

    private void clearSelection() {
        for (TrustedCa ca : all) ca.selected = false;
        applyFilter();
        Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show();
    }

    private void performSelected(boolean desiredEnabled) {
        List<TrustedCa> targets = new ArrayList<>();
        for (TrustedCa ca : all) if (ca.selected) targets.add(ca);
        if (targets.isEmpty()) {
            Toast.makeText(this, "No certificates selected", Toast.LENGTH_SHORT).show();
            return;
        }
        performBulk((desiredEnabled ? "Enable" : "Disable") + " selected CAs", targets, desiredEnabled);
    }

    private void performVendor(boolean desiredEnabled) {
        String vendor = selectedVendor();
        if ("All vendors".equals(vendor)) {
            Toast.makeText(this, "Choose a vendor first", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrustedCa> targets = new ArrayList<>();
        for (TrustedCa ca : all) if (vendor.equals(ca.vendor)) targets.add(ca);
        performBulk((desiredEnabled ? "Enable " : "Disable ") + vendor, targets, desiredEnabled);
    }

    private void performSingle(TrustedCa ca, boolean desiredEnabled) {
        List<TrustedCa> one = new ArrayList<>();
        one.add(ca);
        runOperation((desiredEnabled ? "Enabling " : "Disabling ") + ca.title(), one, desiredEnabled, false);
    }

    private void performBulk(String title, List<TrustedCa> targets, boolean desiredEnabled) {
        if (!rootGranted) {
            Toast.makeText(this, "Root access required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (targets.isEmpty()) {
            Toast.makeText(this, "Nothing to process", Toast.LENGTH_SHORT).show();
            return;
        }
        String warning = desiredEnabled
                ? "This will enable " + targets.size() + " system CA entries."
                : "This will disable " + targets.size() + " system CA entries without deleting their originals. HTTPS/TLS connections that rely on them may fail.";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(warning)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(desiredEnabled ? "Enable" : "Disable", (d, w) -> runOperation(title, targets, desiredEnabled, true))
                .show();
    }

    private void runOperation(String title, List<TrustedCa> targets, boolean desiredEnabled, boolean showProgress) {
        if (!rootGranted || bulk == null) return;
        final ProgressDialog progress = showProgress ? ProgressDialog.show(this, title, "Applying trust-store changes...", true, false) : null;
        busy = true;
        setWriteControls(false);
        adapter.notifyDataSetChanged();
        worker.execute(() -> {
            BulkOperation.Report report = bulk.apply(targets, desiredEnabled);
            main.post(() -> {
                if (progress != null && progress.isShowing()) progress.dismiss();
                busy = false;
                setWriteControls(rootGranted);
                applyFilter();
                if (report.ok()) {
                    Toast.makeText(this, "Changed " + report.changed + " certificate(s)", Toast.LENGTH_SHORT).show();
                } else {
                    adapter.notifyDataSetChanged();
                    new AlertDialog.Builder(this)
                            .setTitle("Operation failed")
                            .setMessage(report.error)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void showDetails(TrustedCa ca) {
        String text = "Status: " + (ca.enabled ? "ENABLED" : "DISABLED") +
                "\nVendor: " + ca.vendor +
                "\nFile: " + ca.fileName +
                "\nOrganization: " + safe(ca.organization) +
                "\nIssuer: " + ca.issuer +
                "\nSHA-256:\n" + ca.sha256 +
                "\n\nValid from: " + ca.certificate.getNotBefore() +
                "\nValid until: " + ca.certificate.getNotAfter();
        new AlertDialog.Builder(this)
                .setTitle(ca.title())
                .setMessage(text)
                .setPositiveButton("OK", null)
                .show();
    }

    private static String safe(String s) { return s == null ? "—" : s; }

    private final class CaAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public TrustedCa getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                row = createRow(parent);
                convertView = row.root;
                convertView.setTag(row);
            } else {
                row = (Row) convertView.getTag();
            }
            TrustedCa ca = getItem(position);
            row.title.setText(ca.title());
            row.detail.setText(ca.vendor + " • " + safe(ca.organization) + "\n" + ca.fileName + " • " + (ca.enabled ? "Enabled" : "Disabled"));
            row.check.setOnCheckedChangeListener(null);
            row.check.setChecked(ca.selected);
            row.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ca.selected = isChecked;
                updateSummary();
            });
            row.toggle.setOnCheckedChangeListener(null);
            row.toggle.setChecked(ca.enabled);
            row.toggle.setEnabled(rootGranted && !busy);
            row.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked == ca.enabled) return;
                performSingle(ca, isChecked);
                row.toggle.setOnCheckedChangeListener(null);
                row.toggle.setChecked(ca.enabled);
            });
            row.root.setOnClickListener(v -> showDetails(ca));
            return convertView;
        }

        private Row createRow(ViewGroup parent) {
            LinearLayout rootView = new LinearLayout(MainActivity.this);
            rootView.setOrientation(LinearLayout.HORIZONTAL);
            rootView.setGravity(Gravity.CENTER_VERTICAL);
            rootView.setPadding(dp(8), dp(7), dp(8), dp(7));

            CheckBox check = new CheckBox(MainActivity.this);
            rootView.addView(check, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout textBox = new LinearLayout(MainActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(MainActivity.this);
            title.setTextSize(15);
            title.setTextColor(Color.rgb(30, 30, 30));
            title.setTypeface(null, 1);
            TextView detail = new TextView(MainActivity.this);
            detail.setTextSize(11);
            detail.setTextColor(Color.DKGRAY);
            textBox.addView(title);
            textBox.addView(detail);
            rootView.addView(textBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Switch toggle = new Switch(MainActivity.this);
            rootView.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Row(rootView, check, title, detail, toggle);
        }
    }

    private static final class Row {
        final LinearLayout root;
        final CheckBox check;
        final TextView title;
        final TextView detail;
        final Switch toggle;
        Row(LinearLayout root, CheckBox check, TextView title, TextView detail, Switch toggle) {
            this.root = root;
            this.check = check;
            this.title = title;
            this.detail = detail;
            this.toggle = toggle;
        }
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
