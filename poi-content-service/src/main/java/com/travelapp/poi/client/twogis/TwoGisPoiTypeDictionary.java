package com.travelapp.poi.client.twogis;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TwoGisPoiTypeDictionary {

    public record TypeRule(String code, String displayPhrase, List<String> keywords) {
        boolean matches(String text) {
            return containsAny(text, keywords);
        }
    }

    public record RequestRule(String requestType, List<String> queryKeywords, Set<String> acceptedTypes) {
        boolean matches(String text) {
            return containsAny(text, queryKeywords);
        }
    }

    private static final List<TypeRule> TYPE_RULES = List.of(
            new TypeRule("toilet", "Общественный туалет.", List.of(
                    "туалет", "уборная", "wc", "restroom", "санузел"
            )),
            new TypeRule("hotel", "Гостиница.", List.of(
                    "отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы",
                    "апартаменты", "жилье", "жильё", "гостевой дом", "мини-отель"
            )),
            new TypeRule("cafe", "Кафе.", List.of(
                    "кафе", "кофейня", "кофейни", "coffee", "кондитерская", "кондитерские",
                    "чайная", "пекарня", "булочная"
            )),
            new TypeRule("restaurant", "Ресторан.", List.of(
                    "ресторан", "рестораны", "бар", "паб", "столовая", "пиццерия", "бургер",
                    "фастфуд", "быстрое питание", "суши", "гриль", "хинкальная", "шаурма"
            )),
            new TypeRule("museum", "Музей.", List.of(
                    "музей", "музеи", "дом-музей", "дом музей", "квартира-музей", "квартира музей",
                    "выставочный зал", "галерея", "экспозиция", "музей-заповедник"
            )),
            new TypeRule("park", "Парк.", List.of(
                    "парк", "парки", "сквер", "сад", "ботанический сад", "парк культуры", "аллея"
            )),
            new TypeRule("landmark", "Достопримечательность.", List.of(
                    "достопримечательность", "достопримечательности", "памятник", "монумент",
                    "мемориал", "обелиск", "собор", "храм", "церковь", "мечеть", "часовня",
                    "театр", "усадьба", "монастырь", "площадь", "набережная", "историческое место",
                    "архитектура", "смотровая площадка", "культурный объект"
            )),
            new TypeRule("shop", "Магазин.", List.of("магазин", "торговый центр", "shop")),
            new TypeRule("pharmacy", "Аптека.", List.of("аптека", "аптечный пункт")),
            new TypeRule("hospital", "Медицинская организация.", List.of("больница", "клиника", "медицинский центр")),
            new TypeRule("school", "Образовательная организация.", List.of("школа", "университет", "институт", "колледж")),
            new TypeRule("atm", "Банкомат.", List.of("банкомат", "atm"))
    );

    private static final List<RequestRule> REQUEST_RULES = List.of(
            new RequestRule("toilet", List.of("туалет", "туалеты", "wc", "уборная", "санузел"), Set.of("toilet")),
            new RequestRule("food", List.of("еда", "где поесть", "питание", "кафе и рестораны", "рестораны и кафе"), Set.of("cafe", "restaurant")),
            new RequestRule("cafe", List.of("кафе", "кофейня", "кофейни", "кофе", "пекарня", "кондитерская"), Set.of("cafe")),
            new RequestRule("restaurant", List.of("ресторан", "рестораны", "бар", "паб", "пиццерия", "фастфуд", "столовая"), Set.of("restaurant")),
            new RequestRule("hotel", List.of("отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы", "апартаменты", "жилье", "жильё"), Set.of("hotel")),
            new RequestRule("museum", List.of("музей", "музеи", "галерея", "выставочный зал"), Set.of("museum")),
            new RequestRule("park", List.of("парк", "парки", "сквер", "сад", "аллея"), Set.of("park")),
            new RequestRule("tourism", List.of(
                    "достопримечательность", "достопримечательности", "что посмотреть", "куда сходить",
                    "интересные места", "туризм", "памятник", "памятники", "собор", "храм",
                    "театр", "площадь", "набережная", "культурные места"
            ), Set.of("landmark", "museum", "park"))
    );

    private static final Map<String, String> FALLBACK_PHRASES = Map.ofEntries(
            Map.entry("toilet", "Общественный туалет."),
            Map.entry("cafe", "Кафе."),
            Map.entry("restaurant", "Ресторан."),
            Map.entry("hotel", "Гостиница."),
            Map.entry("museum", "Музей."),
            Map.entry("park", "Парк."),
            Map.entry("landmark", "Достопримечательность."),
            Map.entry("shop", "Магазин."),
            Map.entry("pharmacy", "Аптека."),
            Map.entry("hospital", "Медицинская организация."),
            Map.entry("school", "Образовательная организация."),
            Map.entry("atm", "Банкомат.")
    );

    public String resolveType(String rawText) {
        String text = normalize(rawText);
        return TYPE_RULES.stream()
                .filter(rule -> rule.matches(text))
                .map(TypeRule::code)
                .findFirst()
                .orElse("landmark");
    }

    public String inferRequestType(String query) {
        String text = normalize(query);
        return REQUEST_RULES.stream()
                .filter(rule -> rule.matches(text))
                .map(RequestRule::requestType)
                .findFirst()
                .orElse(null);
    }

    public boolean accepts(String requestType, String resolvedType) {
        if (StringUtils.isBlank(requestType)) {
            return true;
        }

        String normalizedRequest = normalizeCode(requestType);
        String normalizedResolved = normalizeCode(resolvedType);

        return REQUEST_RULES.stream()
                .filter(rule -> StringUtils.equalsIgnoreCase(rule.requestType(), normalizedRequest))
                .findFirst()
                .map(rule -> rule.acceptedTypes().contains(normalizedResolved))
                .orElseGet(() -> StringUtils.equalsIgnoreCase(normalizedRequest, normalizedResolved));
    }

    public String displayPhrase(String code) {
        return FALLBACK_PHRASES.getOrDefault(normalizeCode(code), "Достопримечательность.");
    }

    public List<TypeRule> typeRules() {
        return TYPE_RULES;
    }

    private static String normalizeCode(String value) {
        return StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return StringUtils.defaultString(value)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, List<String> values) {
        if (StringUtils.isBlank(text)) {
            return false;
        }

        return values.stream()
                .filter(StringUtils::isNotBlank)
                .map(TwoGisPoiTypeDictionary::normalize)
                .anyMatch(text::contains);
    }
}
