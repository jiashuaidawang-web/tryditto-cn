package cn.aiditto.domain.copy;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.copy.BannedWords.Category;
import org.junit.jupiter.api.Test;

class BannedWordsTest {

    static Category cat(String s) {
        return BannedWords.find(s).map(BannedWords.Hit::category).orElse(null);
    }

    @Test
    void eachCategory() {
        assertThat(cat("颜值在线")).isEqualTo(Category.LOOKS);
        assertThat(cat("一夜情")).isEqualTo(Category.SEX);
        assertThat(cat("信仰相同")).isEqualTo(Category.RELIGION);
        assertThat(cat("政治立场")).isEqualTo(Category.POLITICS);
        assertThat(cat("有点焦虑症")).isEqualTo(Category.HEALTH);
        assertThat(cat("先转账")).isEqualTo(Category.MONEY);
        assertThat(cat("天生一对")).isEqualTo(Category.PROMISE);
        assertThat(cat("匹配度 90")).isEqualTo(Category.PERCENT);
        assertThat(cat("90 %")).isEqualTo(Category.PERCENT);
        assertThat(cat("90.5％")).isEqualTo(Category.PERCENT);
    }

    @Test
    void englishMatchesWholeWordsIgnoringCase() {
        assertThat(cat("Such a BODY")).isEqualTo(Category.LOOKS);
        assertThat(cat("nobody knows")).isNull();
        assertThat(cat("Oh my God")).isEqualTo(Category.RELIGION);
        assertThat(cat("goddess of pizza")).isNull();
        assertThat(cat("a hookup")).isEqualTo(Category.SEX);
        assertThat(cat("one night stand")).isEqualTo(Category.SEX);
        assertThat(cat("Meant To Be")).isEqualTo(Category.PROMISE);
    }

    @Test
    void cleanText() {
        assertThat(BannedWords.find("你们都喜欢「独立」音乐，见面的前十分钟不愁没话聊。")).isEmpty();
        assertThat(BannedWords.find("Ask why “晴天” is on repeat.")).isEmpty();
        assertThat(BannedWords.find(null)).isEmpty();
        assertThat(BannedWords.find("")).isEmpty();
    }
}
