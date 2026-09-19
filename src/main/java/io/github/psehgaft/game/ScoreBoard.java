package io.github.psehgaft.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** In-memory top ten; every method is synchronized so simultaneous requests cannot corrupt it. */
public class ScoreBoard {
    private final List<GameResource.Score> scores = new ArrayList<>();

    public synchronized List<GameResource.Score> topTen() {
        return List.copyOf(scores);
    }

    public synchronized GameResource.Score add(GameResource.Score score) {
        if (score == null || score.player() == null || score.player().isBlank()
                || score.player().strip().length() > 20 || score.points() < 0 || score.points() > 400) {
            throw new IllegalArgumentException("Nombre (1-20 caracteres) y puntos (0-400) requeridos");
        }
        GameResource.Score accepted = new GameResource.Score(score.player().strip(), score.points());
        scores.add(accepted);
        scores.sort(Comparator.comparingInt(GameResource.Score::points).reversed());
        if (scores.size() > 10) scores.subList(10, scores.size()).clear();
        return accepted;
    }
}
