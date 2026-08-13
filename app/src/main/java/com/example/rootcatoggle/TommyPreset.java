package com.example.rootcatoggle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Selection-only preset requested for the Tommy build.
 * Applying this preset NEVER changes trust state. It only updates TrustedCa.selected;
 * the user must still press ENABLE SELECTED or DISABLE SELECTED afterwards.
 */
final class TommyPreset {
    private TommyPreset() {}

    static final class Result {
        final int selectedCount;
        final List<String> unmatchedRules;
        final Map<String, Integer> groupCounts;

        Result(int selectedCount, List<String> unmatchedRules, Map<String, Integer> groupCounts) {
            this.selectedCount = selectedCount;
            this.unmatchedRules = unmatchedRules;
            this.groupCounts = groupCounts;
        }
    }

    private interface RuleMatcher {
        boolean matches(TrustedCa ca);
    }

    private static final class Rule {
        final String label;
        final boolean group;
        final RuleMatcher matcher;

        Rule(String label, boolean group, RuleMatcher matcher) {
            this.label = label;
            this.group = group;
            this.matcher = matcher;
        }
    }

    private static final List<Rule> RULES = buildRules();

    /**
     * Clears previous selections, then selects exactly the CAs matched by this preset.
     */
    static Result apply(List<TrustedCa> certificates) {
        for (TrustedCa ca : certificates) ca.selected = false;

        boolean[] matchedRule = new boolean[RULES.size()];
        Map<String, Integer> groupCounts = new LinkedHashMap<>();
        int selected = 0;

        for (TrustedCa ca : certificates) {
            boolean shouldSelect = false;
            for (int i = 0; i < RULES.size(); i++) {
                Rule rule = RULES.get(i);
                if (rule.matcher.matches(ca)) {
                    matchedRule[i] = true;
                    shouldSelect = true;
                    if (rule.group) {
                        Integer previous = groupCounts.get(rule.label);
                        groupCounts.put(rule.label, previous == null ? 1 : previous + 1);
                    }
                }
            }
            if (shouldSelect) {
                ca.selected = true;
                selected++;
            }
        }

        List<String> unmatched = new ArrayList<>();
        for (int i = 0; i < RULES.size(); i++) {
            Rule rule = RULES.get(i);
            if (!matchedRule[i]) unmatched.add(rule.label);
            if (rule.group && !groupCounts.containsKey(rule.label)) groupCounts.put(rule.label, 0);
        }
        return new Result(selected, unmatched, groupCounts);
    }

    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();

        // Whole-vendor groups explicitly requested by the user.
        rules.add(group("ALL AMAZON", ca -> containsAny(ca, "amazon")));
        rules.add(group("ALL GLOBALSIGN", ca -> containsAny(ca, "globalsign")));
        rules.add(group("ALL WISeKey", ca -> containsAny(ca, "wisekey", "wise key")));

        // Explicit certificate titles from the requested list.
        rules.add(exactCn("Baltimore CyberTrust Root"));

        rules.add(exactCn("AAA Certificate Services"));
        rules.add(exactCn("COMODO Certification Authority"));
        rules.add(exactCn("COMODO ECC Certification Authority"));
        rules.add(exactCn("COMODO RSA Certification Authority"));

        rules.add(exactCn("DigiCert Assured ID Root CA"));
        rules.add(exactCn("DigiCert Assured ID Root G2"));
        rules.add(exactCn("DigiCert Assured ID Root G3"));
        rules.add(exactCn("DigiCert Global Root CA"));
        rules.add(exactCn("DigiCert Global Root G2"));
        rules.add(exactCn("DigiCert Global Root G3"));
        rules.add(exactCn("DigiCert High Assurance EV Root CA"));
        rules.add(exactCn("DigiCert Trusted Root G4"));

        rules.add(exactCn("Entrust Root Certification Authority"));
        rules.add(exactCn("Entrust Root Certification Authority - EC1"));
        rules.add(exactCn("Entrust Root Certification Authority - G2"));
        rules.add(exactCn("Entrust.net Certification Authority (2048)"));

        rules.add(exactCn("Go Daddy Root Certificate Authority - G2"));

        rules.add(exactCn("SSL.com EV Root Certification Authority ECC"));
        rules.add(exactCn("SSL.com EV Root Certification Authority RSA R2"));
        rules.add(exactCn("SSL.com Root Certification Authority ECC"));
        rules.add(exactCn("SSL.com Root Certification Authority RSA"));

        // Android's Trusted Credentials UI can display either CN or O depending on the
        // certificate subject. These two rules intentionally require Dhimyotis plus the
        // requested Certigna identity to avoid broad selection of unrelated roots.
        rules.add(rule("Dhimyotis (Certigna)", ca ->
                contains(normalize(ca.organization), "dhimyotis") &&
                        equalsNormalized(ca.commonName, "Certigna")));
        rules.add(rule("Dhimyotis (Certigna Root CA)", ca ->
                contains(normalize(ca.organization), "dhimyotis") &&
                        equalsNormalized(ca.commonName, "Certigna Root CA")));

        rules.add(rule("Disig a.s. (CA Disig Root R2)", ca ->
                (contains(normalize(ca.organization), "disig") || contains(normalize(ca.title()), "disig")) &&
                        equalsNormalized(ca.commonName, "CA Disig Root R2")));

        return rules;
    }

    private static Rule group(String label, RuleMatcher matcher) {
        return new Rule(label, true, matcher);
    }

    private static Rule rule(String label, RuleMatcher matcher) {
        return new Rule(label, false, matcher);
    }

    private static Rule exactCn(String expected) {
        return rule(expected, ca -> equalsNormalized(ca.commonName, expected) || equalsNormalized(ca.title(), expected));
    }

    private static boolean containsAny(TrustedCa ca, String... needles) {
        String haystack = normalize((ca.commonName == null ? "" : ca.commonName) + " " +
                (ca.organization == null ? "" : ca.organization) + " " +
                (ca.vendor == null ? "" : ca.vendor));
        for (String needle : needles) {
            if (contains(haystack, normalize(needle))) return true;
        }
        return false;
    }

    private static boolean equalsNormalized(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && !needle.isEmpty() && haystack.contains(needle);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String s = value.toLowerCase(Locale.US).trim();
        // Keep letters/numbers but normalize punctuation/spacing commonly rendered
        // differently by ROM UIs or X.500 parsers.
        s = s.replace('–', '-').replace('—', '-');
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s.replaceAll("\\s+", " ");
    }
}
