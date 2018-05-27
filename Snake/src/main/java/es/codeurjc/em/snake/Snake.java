package es.codeurjc.em.snake;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class Snake {
	
    private static final int DEFAULT_LENGTH = 5;

    private final int id;

    private Location head;
    private final Deque<Location> tail = new ArrayDeque<>();
    private int length = DEFAULT_LENGTH;

    private int score = 0;

    private final String hexColor;
    private String name;
    private Direction direction;

    private final WebSocketSession session;

    public Snake(int id, WebSocketSession session) {
        this.id = id;
        this.session = session;
        this.hexColor = SnakeUtils.getRandomHexColor();
        resetState();
    }

    public synchronized void resetState() {
        this.direction = Direction.NONE;
        this.head = SnakeUtils.getRandomLocation();
        this.tail.clear();
        this.length = DEFAULT_LENGTH;
    }

    private synchronized void kill() throws Exception {
        resetState();

        if (score > 0) {
            this.score--; //Si te da otra serpiente pierdes un punto
        }
        sendMessage("{\"type\": \"dead\"}");
    }

    private synchronized void reward() throws Exception {
        this.length++;
        this.score++; //Darle a una serpiente suma un punto
        sendMessage("{\"type\": \"kill\"}");
    }

    private synchronized void rewardFood() throws Exception {
        this.length++;
        this.score++; //Coger comida suma un punto
        sendMessage("{\"type\": \"rewardFood\"}");
    }

    protected synchronized void sendMessage(String msg) throws Exception {
        this.session.sendMessage(new TextMessage(msg));
    }

    public synchronized void update(Collection<Snake> snakes) throws Exception {
        Location nextLocation = this.head.getAdjacentLocation(this.direction);
        //Esto hace que aprezca por los bordes.
        if (nextLocation.x >= Location.PLAYFIELD_WIDTH) {
            nextLocation.x = 0;
        }
        if (nextLocation.y >= Location.PLAYFIELD_HEIGHT) {
            nextLocation.y = 0;
        }
        if (nextLocation.x < 0) {
            nextLocation.x = Location.PLAYFIELD_WIDTH;
        }
        if (nextLocation.y < 0) {
            nextLocation.y = Location.PLAYFIELD_HEIGHT;
        }
        //fin de bordes
        if (this.direction != Direction.NONE) {
            this.tail.addFirst(this.head);
            if (this.tail.size() > this.length) {
                this.tail.removeLast();
            }
            this.head = nextLocation;
        }
    }

    public boolean handleCollisions(Collection<Snake> snakes) throws Exception {

        boolean b = false;
        for (Snake snake : snakes) {

            boolean headCollision = this.id != snake.id && snake.getHead().equals(this.head);

            boolean tailCollision = snake.getTail().contains(this.head);

            if (headCollision || tailCollision) {
                kill();
                if (this.id != snake.id) {
                    snake.reward();
                }
                b = true;
            }
        }
        return b;
    }

    public int handleCollisionsFoods(ConcurrentHashMap<Integer, int[]> foods) throws Exception {
        for (int comida : foods.keySet()) {
            boolean headCollision = (this.getHead().x == foods.get(comida)[0] && this.getHead().y == foods.get(comida)[1]);
            if (headCollision) {
                this.rewardFood();
                return comida;
            }
        }
        return -1;
    }

    public synchronized Location getHead() {
        return this.head;
    }

    public synchronized Collection<Location> getTail() {
        return this.tail;
    }

    public synchronized void setDirection(Direction direction) {
        this.direction = direction;
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getHexColor() {
        return this.hexColor;
    }

    public int getLength() {
        return this.length;
    }

    public int getScore() {
        return score;
    }

    public void reestartScore() {
        score = 0;
    }
}
