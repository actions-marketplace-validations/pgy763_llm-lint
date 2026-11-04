package io.github.pgy763.llmlint.lang;

import java.util.Locale;

/** 输出语言。只影响人看的文字，JSON / SARIF 的字段和值不变。 */
public enum Lang {

    ZH("zh"),
    EN("en");

    private final String code;

    Lang(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 解析 --lang / LLM_LINT_LANG 的值。
     *
     * @throws IllegalArgumentException 不认识的语言
     */
    public static Lang of(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (v.isEmpty()) {
            return ZH;
        }
        if (v.equals("zh") || v.equals("cn") || v.equals("chinese") || v.startsWith("zh-")) {
            return ZH;
        }
        if (v.equals("en") || v.equals("english") || v.startsWith("en-")) {
            return EN;
        }
        throw new IllegalArgumentException(value);
    }
}