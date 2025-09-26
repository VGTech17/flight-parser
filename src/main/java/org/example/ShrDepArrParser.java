package org.example;

import java.util.regex.*;
import java.util.ArrayList;

public class ShrDepArrParser {

    public static FlightRecord parse(String center, String shr, String dep, String arr) {
        FlightRecord r = new FlightRecord();
        r.sourceCenter = center;

        // 0) Сохраняем сырой текст (как есть) для аудита
        String shrRaw = shr == null ? "" : shr;
        String depRaw = dep == null ? "" : dep;
        String arrRaw = arr == null ? "" : arr;
        r.rawMessage = (shrRaw + " " + depRaw + " " + arrRaw).trim();

        // 1) Нормализуем для поиска меток (но номера/координаты читаем и по raw)
        String shrN = normalizeText(shrRaw);
        String depN = normalizeText(depRaw);
        String arrN = normalizeText(arrRaw);

        // --- SHR ---
        if (!shrN.isBlank()) {
            r.sid = match("SID/(\\d+)", shrN, r.sid);
            r.date = match("DOF/(\\d{6})", shrN, r.date);
            r.departureTime = match("ZZZZ(\\d{4})", shrN, r.departureTime);
            r.departureCoords = match("DEP/(\\S+)", shrN, r.departureCoords);
            r.arrivalCoords = match("DEST/(\\S+)", shrN, r.arrivalCoords);

            // Aircraft type/model: TYP/...
            String rawType = match("TYP/(\\S+)", shrN, null);
            if (rawType != null) {
                // тип = только буквы (BLA, AER, SHAR, ...)
                r.aircraftType = rawType.replaceAll("[^A-ZА-ЯЁ]", "");
                // модель из TYP, как правило, не informативна (BLA/AER/SHAR),
                // поэтому дополнительно поищем модели в общем тексте ниже
            }

            r.status = match("STS/(\\S+)", shrN, r.status);

            // Оператор напрямую из OPR/… (до телефона/следующего тега)
            r.operator = extractOperatorFromOPR(shrN, r.operator);

            // RMK — аккуратно ограничиваемся любым следующим полем или концом
            String rawRemarks = match(
                    "RMK/(.+?)(?=\\s+(?:SID/|\\-SID\\b|REG/|TYP/|STS/|DOF/|DEP/|DEST/|TEL/|TIP/|TYPE/|$))",
                    shrN, null
            );
            if (rawRemarks != null) {
                parseRemarks(rawRemarks, r); // здесь поймаем телефоны/операторов внутри RMK
            }
        }

        // --- DEP ---
        if (!depN.isBlank()) {
            r.sid = match("SID\\s+(\\d+)", depN, r.sid);
            r.date = match("ADD\\s+(\\d{6})", depN, r.date);
            r.departureTime = match("ATD\\s+(\\d{4})", depN, r.departureTime);
            r.departureCoords = match("ADEPZ\\s+(\\S+)", depN, r.departureCoords);
        }

        // --- ARR ---
        if (!arrN.isBlank()) {
            r.sid = match("SID\\s+(\\d+)", arrN, r.sid);
            r.date = match("ADA\\s+(\\d{6})", arrN, r.date);
            r.arrivalTime = match("ATA\\s+(\\d{4})", arrN, r.arrivalTime);
            r.arrivalCoords = match("ADARRZ\\s+(\\S+)", arrN, r.arrivalCoords);
        }

        // 2) Рег-номера — сканируем ВСЕ секции по raw (учёт «REG00725», «REG/00725», списков «REG/00724,REG00725»)
        extractRegNumbers(shrRaw, r);
        extractRegNumbers(depRaw, r);
        extractRegNumbers(arrRaw, r);

        // 3) Модель БВС — ищем по всему тексту (raw), потому что часто в RMK
        extractAircraftModel((shrRaw + " " + depRaw + " " + arrRaw), r);

        return r;
    }

    // ---------------- helpers ----------------

    private static String match(String regex, String text, String current) {
        if (text == null) return current;
        Matcher m = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text);
        String result = m.find() ? m.group(1).trim() : current;
        return normalizeText(result);
    }

    // Нормализация: схлопываем пробелы, фикс OCR «4->Ч» только внутри слов
    private static String normalizeText(String text) {
        if (text == null) return null;
        String t = text.replace("\n", " ");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("(?<=[А-ЯЁа-яё])4(?=[А-ЯЁа-яё])", "Ч");
        t = t.replaceAll("(?<=[А-ЯЁа-яё])4(?=\\b|[^0-9])", "Ч");
        t = t.replaceAll("(?<=\\b|[^0-9])4(?=[А-ЯЁа-яё])", "Ч");
        return t;
    }

    // Извлекаем оператора из OPR/… прямо из SHR
    private static String extractOperatorFromOPR(String text, String current) {
        if (text == null) return current;
        // Берём только буквы/пробелы/точки/дефисы; обрезаем на телефоне/следующем теге
        Pattern p = Pattern.compile(
                "OPR/\\s*([A-Za-zА-ЯЁа-яё\\-\\.\\s]+?)(?=\\s+(?:\\+?\\d|TEL\\b|TYP/|REG/|STS/|RMK/|SID/|DOF/|DEP/|DEST/|$))",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            // чищу повторяющиеся пробелы и одиночные служебные слова
            name = name.replaceAll("\\s+", " ").replaceAll("^(?:БВС|БПЛА)\\s+", "");
            return mergeSemi(current, name);
        }
        return current;
    }

    private static void parseRemarks(String text, FlightRecord r) {
        String t = normalizeText(text); // сюда приходит ТОЛЬКО то, что было после RMK/

        // 1) Телефоны (+7..., 8..., TEL/)
        Matcher phones = Pattern.compile("(?:\\+7|8)\\d{10}").matcher(t);
        while (phones.find()) r.phones.add(phones.group());
        Matcher telPhones = Pattern.compile("\\bTEL\\s*/?\\s*((?:\\+7|8)?\\d{10})", Pattern.CASE_INSENSITIVE).matcher(t);
        while (telPhones.find()) r.phones.add(telPhones.group(1));

        // 2) Оператор внутри RMK (варианты триггеров)
        extractOperatorsFromFreeText(t, r);

        // 3) Коды согласований (WR…, МР…)
        Matcher codes = Pattern.compile("\\b(?:WR|МР)\\d+\\b").matcher(t);
        while (codes.find()) {
            if (r.remarks == null) r.remarks = "";
            if (!r.remarks.contains(codes.group())) {
                r.remarks += (r.remarks.isEmpty() ? "" : "; ") + codes.group();
            }
        }

        // 4) Остаток RMK без телефонов/TEL — в remarksRaw
        String clean = t.replaceAll("(?:\\+7|8)\\d{10}", "")
                .replaceAll("\\bTEL\\s*/?\\s*((?:\\+7|8)?\\d{10})", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!clean.isEmpty()) r.remarksRaw = (r.remarksRaw == null || r.remarksRaw.isBlank())
                ? clean : (r.remarksRaw + " " + clean);
    }

    // Оператор в свободном тексте: OPR/FIO/ОПЕРАТОР/ПИЛОТ/OPERATOR
    private static void extractOperatorsFromFreeText(String t, FlightRecord r) {
        Pattern trigger = Pattern.compile("(?:\\bOPR/|\\bFIO/|\\bОПЕРАТОР\\b|\\bПИЛОТ\\b|\\bOPERATOR\\b)", Pattern.CASE_INSENSITIVE);
        Matcher m = trigger.matcher(t);
        while (m.find()) {
            int start = m.end();
            String tail = t.substring(start).trim();

            // обрезаем на первом следующем «поле»
            Pattern next = Pattern.compile("\\b(?:REG/?|TYP/|STS/|RMK/|SID/|DOF/|DEP/|DEST/|TEL/|TIP/|TYPE/|OPR/|FIO/|ОПЕРАТОР|ПИЛОТ|OPERATOR)\\b", Pattern.CASE_INSENSITIVE);
            Matcher n = next.matcher(tail);
            if (n.find()) tail = tail.substring(0, n.start()).trim();

            // чистим мусорные слова
            tail = tail.replaceAll("\\b(БВС|БПЛА|UAV|DRONE)\\b", " ").replaceAll("\\s+", " ").trim();

            // пробуем ФИО (кириллица)
            Matcher fioCyr = Pattern.compile("([А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+){0,2})").matcher(tail);
            boolean found = false;
            while (fioCyr.find()) {
                r.operator = mergeSemi(r.operator, fioCyr.group(1).trim());
                found = true;
            }
            // пробуем латиницу (напр. FIO/EVTIUSHKIN KIRILL VIACHESLAVOVICH)
            Matcher fioLat = Pattern.compile("([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2}|[A-Z]{2,}(?:\\s+[A-Z]{2,}){0,2})").matcher(tail);
            while (fioLat.find()) {
                r.operator = mergeSemi(r.operator, fioLat.group(1).trim());
                found = true;
            }
            // если ничего не нашли — не тащим хвост в remarksRaw (чтобы не засорять), достаточно rawMessage
        }
    }

    private static void extractRegNumbers(String raw, FlightRecord r) {
        if (raw == null || raw.isBlank()) return;

        // Нормализуем пробелы (включая NBSP, узкие и т.п.)
        String scan = raw
                .replace('\u00A0', ' ')  // NBSP
                .replace('\u2007', ' ')  // Figure space
                .replace('\u202F', ' ')  // Narrow NBSP
                .replaceAll("[ \\t\\x0B\\f\\r\\n]+", " ")
                .trim();

        // стоп-лист: слова с "4" внутри буквенных последовательностей
        Pattern stopPattern = Pattern.compile("[А-ЯЁA-Z]4[А-ЯЁA-Z]", Pattern.CASE_INSENSITIVE);

        // стоп-лист явных слов, которые не должны попадать в регномера
        java.util.Set<String> stopList = java.util.Set.of(
                "КРАВ4УК",
                "У4ЕТНЫЕ",
                "4УМАКОВ",
                "ПО4ИНОВ"
        );

        // helper для проверки стоп-листа
        java.util.function.Predicate<String> notBlocked = t ->
                !stopPattern.matcher(t).find() &&
                        !stopList.contains(t.toUpperCase());

        // 1) REG/AAA, REG AAA, REGAAA (+ списки через запятую)
        Pattern p1 = Pattern.compile("\\bREG\\s*/?\\s*([A-ZА-ЯЁ0-9\\-,]+)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(scan);
        while (m1.find()) {
            String blob = m1.group(1);
            for (String token : blob.split(",")) {
                String t = token.replaceFirst("^REG", "").replaceFirst("^/", "").trim();
                if (t.matches("[A-ZА-ЯЁ0-9\\-]{3,}") && looksLikeReg(t) && notBlocked.test(t)) {
                    addRegUnique(r, t);
                }
            }
        }

        // 2) номера рядом с БЛА/БПЛА
        Pattern p3 = Pattern.compile(
                "\\b(?:БЛА|БПЛА)\\b[ \\t\\x0B\\f\\r\\n\\u00A0\\u2007\\u202F]*" +
                        "(?:№|No\\.?|N\\.?|#)?[ \\t\\x0B\\f\\r\\n\\u00A0\\u2007\\u202F]*" +
                        "([A-ZА-ЯЁ0-9\\-]{3,})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
        );
        Matcher m3 = p3.matcher(scan);
        while (m3.find()) {
            String t = m3.group(1).trim();
            if (t.matches("[A-ZА-ЯЁ0-9\\-]{3,}") && looksLikeReg(t) && notBlocked.test(t)) {
                addRegUnique(r, t);
            }
        }

        // 3) редкие формы "REG00725" без пробела и слэша
        Pattern p2 = Pattern.compile("\\bREG([A-ZА-ЯЁ0-9\\-]{3,})\\b", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(scan);
        while (m2.find()) {
            String t = m2.group(1).trim();
            if (t.matches("[A-ZА-ЯЁ0-9\\-]{3,}") && looksLikeReg(t) && notBlocked.test(t)) {
                addRegUnique(r, t);
            }
        }

        // 4) fallback — 6–7 знаков (буквы+цифры) для редких форматов
        Pattern p4 = Pattern.compile("(?<!\\w)(?=[A-ZА-ЯЁ0-9]*[A-ZА-ЯЁ])(?=[A-ZА-ЯЁ0-9]*\\d)[A-ZА-ЯЁ0-9\\-]{6,7}(?!\\w)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
        Matcher m4 = p4.matcher(scan);
        while (m4.find()) {
            String t = m4.group().trim();
            if (looksLikeReg(t) && notBlocked.test(t)) {
                addRegUnique(r, t);
            }
        }
    }

    // Проверка регномер
    private static boolean looksLikeReg(String t) {
        String s = t.toUpperCase();

        // Отсев координат и мусора
        if (s.matches("\\d{5,}[A-ZА-ЯЁ]") || s.matches("\\d{6,}") || s.matches("[A-ZА-ЯЁ]{5,}")) return false;
        if (s.matches("\\d{2,6}[NS]\\d{2,6}[EW]")) return false;
        if (s.matches("[NS]\\d+|[EW]\\d+")) return false;
        if (s.matches("СЕРГЕЕВ|БОГДАНО|ОБЕСПЕЧ.*")) return false;

        // Известные хорошие шаблоны
        if (s.matches("RA-?\\d{3,4}[A-Z]")) return true;
        if (s.matches("[A-ZА-ЯЁ]{1,3}-?\\d{3,4}[A-ZА-ЯЁ]?")) return true;
        if (s.matches("[A-ZА-ЯЁ]\\d{4,}[A-ZА-ЯЁ]?")) return true;
        if (s.matches("\\d{3,}[A-ZА-ЯЁ]{1,2}")) return true;

        // fallback: смешанный набор с буквой и цифрой
        return s.length() >= 6 && s.length() <= 8 && s.matches("(?=.*[A-ZА-ЯЁ])(?=.*\\d)[A-ZА-ЯЁ0-9\\-]+");
    }

    private static void addRegUnique(FlightRecord r, String val) {
        if (val == null || val.isBlank()) return;
        if (!r.regNumbers.contains(val)) r.regNumbers.add(val);
    }

    private static void extractAircraftModel(String rawAll, FlightRecord r) {
        if (rawAll == null) return;

        // Нормализуем пробелы (включая спецсимволы и переносы строк)
        String s = rawAll
                .replace('\u00A0', ' ')  // NBSP
                .replace('\u2007', ' ')  // Figure space
                .replace('\u202F', ' ')  // Narrow NBSP
                .replaceAll("[ \\t\\x0B\\f\\r\\n]+", " ") // схлопываем все пробелы и \n
                .trim();
        s = normalizeText(s); // чиним OCR «4->Ч»

        // Пул паттернов по популярным моделям
        Pattern[] models = new Pattern[]{
                // DJI
                Pattern.compile("\\bDJI\\s+MAVIC\\s*AIR\\s*2S\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MAVIC\\s*3\\s*PRO\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MAVIC\\s*3\\s*CLASSIC\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MAVIC\\s*3\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MAVIC\\s*2\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MAVIC\\s*AIR\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+PHANTOM\\s*4\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MINI\\s*2\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MINI\\s*3\\s*PRO\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MINI\\s*3\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MINI\\s*4\\s*PRO\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+AIR\\s*2S\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+AVATA\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+FPV\\w*", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*30T\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*300\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*350\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*200\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*210\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+MATRICE\\s*600\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+INSPIRE\\s*1\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+INSPIRE\\s*2\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+AGRAS\\s*T10\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+AGRAS\\s*T30\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bDJI\\s+AGRAS\\s*T40\\b", Pattern.CASE_INSENSITIVE),

                // Autel
                Pattern.compile("\\bAUTEL\\s+EVO\\s*II\\b", Pattern.CASE_INSENSITIVE),

                // GEPRC
                Pattern.compile("\\bGEPRC\\s+CINEBOT30\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bGEPRC\\s+CINEBOT35\\b", Pattern.CASE_INSENSITIVE),

                // Parrot
                Pattern.compile("\\bPARROT\\s+ANAFI\\w*", Pattern.CASE_INSENSITIVE),

                // SJRC
                Pattern.compile("\\bSJRC\\s+F22\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bSJRC\\s+S20\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bSJRC\\s+S2\\s*PRO\\+\\b", Pattern.CASE_INSENSITIVE),

                // Other (S2 PRO+ без бренда)
                Pattern.compile("\\bS2\\s*PRO\\+\\b", Pattern.CASE_INSENSITIVE),

                // Geoscan (латиница + кириллица)
                Pattern.compile("\\bGEOSCAN\\s*101\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bGEOSCAN\\s*119\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bGEOSCAN\\s*201\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bGEOSCAN\\s*GEMINI\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bGEOSCAN\\s*2201\\b", Pattern.CASE_INSENSITIVE),

                Pattern.compile("\\bГЕОСКАН\\s*101\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bГЕОСКАН\\s*119\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bГЕОСКАН\\s*201\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bГЕОСКАН\\s*GEMINI\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bГЕОСКАН\\s*2201\\b", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern p : models) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                String mdl = m.group().replaceAll("\\s+", " ").trim(); // чиним пробелы
                r.aircraftModel = (r.aircraftModel == null || r.aircraftModel.isBlank()) ? mdl : r.aircraftModel;
                break; // нашли первую модель — выходим
            }
        }
    }

    private static String mergeSemi(String current, String add) {
        String val = normalizeText(add);
        if (val == null || val.isBlank()) return current;
        if (current == null || current.isBlank()) return val;
        // не дублируем
        for (String part : current.split("\\s*;\\s*")) {
            if (part.equalsIgnoreCase(val)) return current;
        }
        return current + "; " + val;
    }
}