package egovframework.haengjeongnuli.cmm.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void 휴대전화는_하이픈_띄어쓰기_상관없이_같은_꼴로() {
        assertThat(PhoneNumbers.mobile("01012345678")).isEqualTo("010-1234-5678");
        assertThat(PhoneNumbers.mobile("010 1234 5678")).isEqualTo("010-1234-5678");
        assertThat(PhoneNumbers.mobile("010.1234.5678")).isEqualTo("010-1234-5678");
        assertThat(PhoneNumbers.mobile("011-123-4567")).isEqualTo("011-123-4567");
        assertThat(PhoneNumbers.mobile("02-123-4567")).isNull();   // 휴대전화 칸에 집전화
        assertThat(PhoneNumbers.mobile("010-1234-567")).isNull();
        assertThat(PhoneNumbers.mobile("abc")).isNull();
        assertThat(PhoneNumbers.mobile(null)).isNull();
    }

    @Test
    void 집전화_팩스는_지역번호_070_050x_대표번호() {
        assertThat(PhoneNumbers.phone("021234567")).isEqualTo("02-123-4567");
        assertThat(PhoneNumbers.phone("02-1234-5678")).isEqualTo("02-1234-5678");
        assertThat(PhoneNumbers.phone("031 123 4567")).isEqualTo("031-123-4567");
        assertThat(PhoneNumbers.phone("064-1234-5678")).isEqualTo("064-1234-5678");
        assertThat(PhoneNumbers.phone("07012345678")).isEqualTo("070-1234-5678");
        assertThat(PhoneNumbers.phone("0505-123-4567")).isEqualTo("0505-123-4567");
        assertThat(PhoneNumbers.phone("050412345678")).isEqualTo("0504-1234-5678");
        assertThat(PhoneNumbers.phone("1588-1234")).isEqualTo("1588-1234");
        assertThat(PhoneNumbers.phone("010-1234-5678")).isEqualTo("010-1234-5678");
        assertThat(PhoneNumbers.phone("039-123-4567")).isNull(); // 없는 지역번호
        assertThat(PhoneNumbers.phone("02-12")).isNull();
        assertThat(PhoneNumbers.phone("+82-10-1234-5678")).isNull();
        assertThat(PhoneNumbers.phone("02-123-4567; DROP")).isNull();
    }
}
