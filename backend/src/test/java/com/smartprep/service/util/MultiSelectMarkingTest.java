package com.smartprep.service.util;

import com.smartprep.model.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Choose TWO letters, A-E" (V55). The task covers two question numbers, stored as two rows
 * with select_count = 2 and one correct letter each (here B and D). The candidate's choice is
 * recorded on both rows. Each right letter is worth one mark, in either order.
 */
class MultiSelectMarkingTest {

    private static int marks(String choice) {
        int marks = 0;
        for (String rowLetter : new String[] {"B", "D"}) {
            if (IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, rowLetter, choice, null, 2)) marks++;
        }
        return marks;
    }

    @Test
    @DisplayName("both right letters score both marks, in either order and however written")
    void bothRight() {
        assertThat(marks("B,D")).isEqualTo(2);
        assertThat(marks("D,B")).isEqualTo(2);
        assertThat(marks("d b")).isEqualTo(2);
        assertThat(marks("BD")).isEqualTo(2);
    }

    @Test
    @DisplayName("one right letter scores one mark")
    void oneRight() {
        assertThat(marks("B,E")).isEqualTo(1);
        assertThat(marks("B")).isEqualTo(1);
    }

    @Test
    @DisplayName("a right letter chosen twice still counts once")
    void repeatedLetter() {
        assertThat(marks("B,B")).isEqualTo(1);
    }

    @Test
    @DisplayName("choosing more letters than the task takes scores nothing")
    void tooManyLetters() {
        assertThat(marks("A,B,C,D,E")).isZero();
        assertThat(marks("B,C,D")).isZero();
    }

    @Test
    @DisplayName("an ordinary MCQ still takes exactly one letter")
    void singleSelectUnchanged() {
        assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, "B", "b", null, 1)).isTrue();
        assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, "B", "B,D", null, 1)).isFalse();
        assertThat(IeltsScoringUtils.isListeningCorrect("B", "A,B,C", "MCQ", null, 1)).isFalse();
    }

    @Test
    @DisplayName("Listening marks a Choose-TWO task the same way")
    void listening() {
        assertThat(IeltsScoringUtils.isListeningCorrect("D", "B,D", "MCQ", null, 2)).isTrue();
        assertThat(IeltsScoringUtils.isListeningCorrect("D", "A,B,C", "MCQ", null, 2)).isFalse();
    }
}
