package org.example;

import java.util.regex.*;
import java.util.ArrayList;

public class ShrDepArrParser {

    public static FlightRecord parse(String center, String shr, String dep, String arr) {
        FlightRecord r = new FlightRecord();
        r.sourceCenter = center;


        shr = normalizeText(shr);
        dep = normalizeText(dep);
        arr = normalizeText(arr);

        if (shr != null) {
            r.sid = match("SID/(\\d+)", shr, r.sid);
            r.date = match("DOF/(\\d{6})", shr, r.date);
            r.departureTime = match("ZZZZ(\\d{4})", shr, r.departureTime);
            r.departureCoords = match("DEP/(\\S+)", shr, r.departureCoords);
            r.arrivalCoords = match("DEST/(\\S+)", shr, r.arrivalCoords);


            String rawType = match("TYP/(\\S+)", shr, null);
            if (rawType != null) {
                r.aircraftType = rawType.replaceAll("[^A-ZА-ЯЁ]", "");
                r.aircraftModel = rawType.replaceAll("[A-ZА-ЯЁ]", "");
            }

            r.status = match("STS/(\\S+)", shr, r.status);


            extractOperators(shr, r);


            String rawRemarks = match("RMK/(.+?)(SID/|$)", shr, null);
            if (rawRemarks != null) {
                parseRemarks(rawRemarks, r);
            }
        }

        if (dep != null) {
            r.sid = match("SID\\s+(\\d+)", dep, r.sid);
            r.date = match("ADD\\s+(\\d{6})", dep, r.date);
            r.departureTime = match("ATD\\s+(\\d{4})", dep, r.departureTime);
            r.departureCoords = match("ADEPZ\\s+(\\S+)", dep, r.departureCoords);
        }

        if (arr != null) {
            r.sid = match("SID\\s+(\\d+)", arr, r.sid);
            r.date = match("ADA\\s+(\\d{6})", arr, r.date);
            r.arrivalTime = match("ATA\\s+(\\d{4})", arr, r.arrivalTime);
            r.arrivalCoords = match("ADARRZ\\s+(\\S+)", arr, r.arrivalCoords);
        }

        return r;
    }

    private static String match(String regex, String text, String current) {
        if (text == null) return current;
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        String result = m.find() ? m.group(1).trim() : current;
        return normalizeText(result);
    }


    private static String normalizeText(String text) {
        if (text == null) return null;
        text = text.replace("\n", " ");
        text = text.replaceAll("\\s+", " ").trim();


        text = text.replaceAll("(?<=[А-ЯЁа-яё])4(?=[А-ЯЁа-яё])", "Ч");
        text = text.replaceAll("(?<=[А-ЯЁа-яё])4(?=\\b|[^0-9])", "Ч");
        text = text.replaceAll("(?<=\\b|[^0-9])4(?=[А-ЯЁа-яё])", "Ч");

        return text;
    }

    private static void parseRemarks(String text, FlightRecord r) {
        text = normalizeText(text);


        Matcher phones = Pattern.compile("(?:\\+7|8)\\d{10}").matcher(text);
        while (phones.find()) {
            r.phones.add(phones.group());
        }
        Matcher telPhones = Pattern.compile("TEL\\s*/?\\s*((?:\\+7|8)?\\d{10})").matcher(text);
        while (telPhones.find()) {
            r.phones.add(telPhones.group(1));
        }


        extractOperators(text, r);


        Matcher codes = Pattern.compile("(?:WR|МР)\\d+").matcher(text);
        while (codes.find()) {
            if (r.remarks == null) r.remarks = "";
            r.remarks += (r.remarks.isEmpty() ? "" : "; ") + codes.group();
        }


        String clean = text.replaceAll("(?:\\+7|8)\\d{10}", "")
                .replaceAll("TEL\\s*/?\\s*((?:\\+7|8)?\\d{10})", "")
                .trim();
        r.remarksRaw = normalizeText(clean);
    }


    private static void extractOperators(String text, FlightRecord r) {
        Pattern trig = Pattern.compile("(OPR/|FIO/|ОПЕРАТОР|ПИЛОТ|OPERATOR)", Pattern.CASE_INSENSITIVE);
        Matcher ops = trig.matcher(text);

        while (ops.find()) {
            int start = ops.end();
            String tail = text.substring(start).trim();

            Pattern nextField = Pattern.compile("\\b(?:REG/|TYP/|STS/|RMK/|SID/|TEL/|TIP/|TYPE/|FIO/|OPR/|OPERATOR|ОПЕРАТОР|ПИЛОТ)\\b");
            Matcher nf = nextField.matcher(tail);
            if (nf.find()) {
                tail = tail.substring(0, nf.start()).trim();
            }


            tail = tail.replaceAll("\\b(БВС|БПЛА|UAV|DRONE)\\b", " ").replaceAll("\\s+", " ").trim();

            boolean found = false;


            Pattern fioCyr = Pattern.compile("(?:[А-ЯЁ][а-яё]+|[А-ЯЁ]{2,})(?:\\s+(?:[А-ЯЁ][а-яё]+|[А-ЯЁ]{2,})){0,2}");
            Matcher mc = fioCyr.matcher(tail);
            while (mc.find()) {
                r.operator = mergeOperator(r.operator, mc.group().trim());
                found = true;
            }


            Pattern fioLat = Pattern.compile("(?:[A-Z][a-z]+|[A-Z]{2,})(?:\\s+(?:[A-Z][a-z]+|[A-Z]{2,})){0,2}");
            Matcher ml = fioLat.matcher(tail);
            while (ml.find()) {
                r.operator = mergeOperator(r.operator, ml.group().trim());
                found = true;
            }

            if (!found && !tail.isBlank()) {
                r.remarksRaw = (r.remarksRaw == null ? "" : r.remarksRaw + " ") + tail;
                r.remarksRaw = normalizeText(r.remarksRaw);
            }
        }
    }


    private static String mergeOperator(String current, String add) {
        String val = normalizeText(add);
        if (val == null || val.isBlank()) return current;
        if (current == null || current.isBlank()) return val;
        if (current.contains(val)) return current;
        return current + "; " + val;
    }
}