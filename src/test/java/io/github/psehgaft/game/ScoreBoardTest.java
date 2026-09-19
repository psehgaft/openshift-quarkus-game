package io.github.psehgaft.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreBoardTest {
    @Test
    void sortsAndLimitsResultsToTen() {
        ScoreBoard board = new ScoreBoard();
        for (int points = 0; points < 12; points++)
            board.add(new GameResource.Score(" player ", points));
        assertEquals(10, board.topTen().size());
        assertEquals(11, board.topTen().get(0).points());
        assertEquals(2, board.topTen().get(9).points());
        assertEquals("player", board.topTen().get(0).player());
        assertThrows(UnsupportedOperationException.class,
                () -> board.topTen().add(new GameResource.Score("other", 1)));
    }

    @Test
    void rejectsInvalidInputWithoutChangingScores() {
        ScoreBoard board = new ScoreBoard();
        assertThrows(IllegalArgumentException.class, () -> board.add(null));
        assertThrows(IllegalArgumentException.class, () -> board.add(new GameResource.Score(" ", 2)));
        assertThrows(IllegalArgumentException.class, () -> board.add(new GameResource.Score("a".repeat(21), 2)));
        assertThrows(IllegalArgumentException.class, () -> board.add(new GameResource.Score("player", -1)));
        assertThrows(IllegalArgumentException.class, () -> board.add(new GameResource.Score("player", 401)));
        assertTrue(board.topTen().isEmpty());
    }
}
