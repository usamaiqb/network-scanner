import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Prints each Android resource locale qualifier with its language's own name, one per
 * line as "qualifier<TAB>name": de -> Deutsch, zh-rCN -> 中文 (中国).
 *
 * Run as a single-file source program, no compile step:
 *   java scripts/LanguageNames.java de zh-rCN
 *
 * The names come from the JDK's CLDR locale data, the same source Android's
 * per-app language page draws on, so the README names a language the way the
 * phone does and nobody keeps a list.
 */
public class LanguageNames {

    public static void main(String[] args) {
        // The console code page on Windows would turn non-Latin names into '?'.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        for (String qualifier : args) {
            Locale locale = Locale.forLanguageTag(toLanguageTag(qualifier));
            out.println(qualifier + "\t" + capitalize(locale.getDisplayName(locale), locale));
        }
    }

    /** values-de -> de, values-zh-rCN -> zh-CN, values-b+sr+Latn -> sr-Latn. */
    private static String toLanguageTag(String qualifier) {
        if (qualifier.startsWith("b+")) {
            return qualifier.substring(2).replace('+', '-');
        }
        return qualifier.replace("-r", "-");
    }

    /** CLDR lowercases some endonyms (français, español); a table row starts capitalised. */
    private static String capitalize(String name, Locale locale) {
        if (name.isEmpty()) {
            return name;
        }
        int first = name.offsetByCodePoints(0, 1);
        return name.substring(0, first).toUpperCase(locale) + name.substring(first);
    }
}
