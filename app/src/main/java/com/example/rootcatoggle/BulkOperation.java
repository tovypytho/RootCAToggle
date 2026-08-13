package com.example.rootcatoggle;

import java.util.ArrayList;
import java.util.List;

final class BulkOperation {
    static final class Report {
        final int changed;
        final int total;
        final String error;
        Report(int changed, int total, String error) {
            this.changed = changed;
            this.total = total;
            this.error = error;
        }
        boolean ok() { return error == null; }
    }

    private final RootTrustStore store;

    BulkOperation(RootTrustStore store) {
        this.store = store;
    }

    Report apply(List<TrustedCa> targets, boolean enabled) {
        List<TrustedCa> changed = new ArrayList<>();
        try {
            for (TrustedCa ca : targets) {
                if (ca.enabled == enabled) continue;
                store.setEnabled(ca.certificate, enabled);
                ca.enabled = enabled;
                changed.add(ca);
            }
            store.notifyTrustStoreChanged();
            return new Report(changed.size(), targets.size(), null);
        } catch (Exception failure) {
            String rollbackError = null;
            for (int i = changed.size() - 1; i >= 0; i--) {
                TrustedCa ca = changed.get(i);
                try {
                    store.setEnabled(ca.certificate, !enabled);
                    ca.enabled = !enabled;
                } catch (Exception rollbackFailure) {
                    rollbackError = rollbackFailure.getMessage();
                }
            }
            store.notifyTrustStoreChanged();
            String msg = failure.getMessage();
            if (rollbackError != null) msg += " | rollback problem: " + rollbackError;
            return new Report(changed.size(), targets.size(), msg);
        }
    }
}
