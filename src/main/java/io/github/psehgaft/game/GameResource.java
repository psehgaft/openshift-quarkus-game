package io.github.psehgaft.game;

import java.util.List;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/game")
@Produces(MediaType.APPLICATION_JSON)
public class GameResource {
    private final ScoreBoard board = new ScoreBoard();

    public record Info(String name, String message, int boardSize, List<String> controls) {}
    public record Score(String player, int points) {}

    @GET
    public Info info() {
        return new Info("Quarkus Snake", "¡Come las frutas y evita chocar!", 20,
                List.of("Flechas o WASD: mover", "Espacio: pausa", "R: reiniciar"));
    }

    @GET
    @Path("/scores")
    public List<Score> scores() {
        return board.topTen();
    }

    @POST
    @Path("/scores")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(Score score) {
        try {
            Score accepted = board.add(score);
            return Response.status(Response.Status.CREATED).entity(accepted).build();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), 400);
        }
    }
}
