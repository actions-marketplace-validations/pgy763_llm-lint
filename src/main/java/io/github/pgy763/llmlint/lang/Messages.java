package io.github.pgy763.llmlint.lang;

import java.util.Locale;

/**
 * 一处一处地写「中文 / 英文」两版文案：{@code Messages.t("中文", "English")}。
 *
 * <p>没有用 resource bundle，是因为文案都贴着用法（规则消息里嵌着方法名、token 数），
 * 拆到 properties 里反而看不出上下文，key 也会慢慢对不上。
 *
 * <p>语言按线程存：并行扫描时每个线程自己设一次，免得一条规则里读到别的线程的值。
 */
public final class Messages {

    private static final ThreadLocal<Lang> CURRENT = ThreadLocal.withInitial(() -> Lang.ZH);

    private Messages() {
    }

    public static Lang lang() {
        return CURRENT.get();
    }

    public static void use(Lang lang) {
        CURRENT.set(lang);
    }

    /** 选当前语言的那一句。 */
    public static String t(String zh, String en) {
        return CURRENT.get() == Lang.EN ? en : zh;
    }

    /** 选当前语言的那一句，并按 {@link String#format} 填参数。 */
    public static String f(String zh, String en, Object... args) {
        String pattern = CURRENT.get() == Lang.EN ? en : zh;
        return String.format(Locale.ROOT, pattern, args);
    }
}