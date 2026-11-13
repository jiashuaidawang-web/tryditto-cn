package cn.aiditto.domain.copy;

/** Language of server-generated copy, from {@code Accept-Language} (F4). */
public enum Lang {
    ZH, EN;

    public static Lang fromTag(String tag) {
        return tag != null && tag.toLowerCase().startsWith("en") ? EN : ZH;
    }
}
