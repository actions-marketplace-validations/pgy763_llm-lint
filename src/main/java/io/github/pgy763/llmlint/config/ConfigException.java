package io.github.pgy763.llmlint.config;

/**
 * 配置文件自己的错：语法、未知的键、未知的规则编号、认不出的级别。
 *
 * <p>消息里一定带文件名和行号，而且可以原样打印给人看——配置写错时最烦的是
 * 「某某配置无效」，然后你得自己一行行找。
 */
public final class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }
}