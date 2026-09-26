package com.roti5dao.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.common.security.PasswordPolicy;
import com.roti5dao.common.storage.ImageType;
import com.roti5dao.common.util.LikeUtils;
import com.roti5dao.common.util.PhoneUtils;
import org.junit.jupiter.api.Test;

class CommonUtilsTest {

    @Test
    void phoneNormalization() {
        assertThat(PhoneUtils.normalize("081-234-5678")).isEqualTo("0812345678");
        assertThat(PhoneUtils.normalize("+66812345678")).isEqualTo("0812345678");
        assertThat(PhoneUtils.normalize("66812345678")).isEqualTo("0812345678");
        assertThat(PhoneUtils.normalize("12345")).isNull();
        assertThat(PhoneUtils.normalize("08123abc78")).isNull();
        assertThat(PhoneUtils.mask("0812345678")).isEqualTo("081-xxx-5678");
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.check("Passw0rd")).isNull();
        assertThat(PasswordPolicy.check("short1")).isNotNull();
        assertThat(PasswordPolicy.check("onlyletters")).isNotNull();
        assertThat(PasswordPolicy.check("12345678")).isNotNull();
        assertThat(PasswordPolicy.check("ก".repeat(30) + "1")).isNotNull(); // > 72 bytes (BCrypt limit)
    }

    @Test
    void imageTypeByMagicBytes() {
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains(ImageType.JPEG);
        assertThat(ImageType.detect("RIFF0000WEBPVP8 ".getBytes())).contains(ImageType.WEBP);
        assertThat(ImageType.detect("<svg></svg>".getBytes())).isEmpty();
        assertThat(ImageType.detect("GIF89a".getBytes())).isEmpty();
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(LikeUtils.containsPattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
        assertThat(LikeUtils.containsPattern("  ")).isNull();
    }
}
