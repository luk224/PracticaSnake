package es.codeurjc.em.snake;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class SnakeGame {

    private String admin; //Almacena la Id de la sesión del administrador de la partida
    private ConcurrentHashMap<Integer, int[]> comidas = new ConcurrentHashMap<>(); //Mapa de comidas (clave: id; valor: posición [x,y])
    private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>(); //Mapa de snakes (clave: id; valor: objeto Snake)
    private static Random rn;

    //Valores para comprobar el final de la partida:
    private final static int MAX_FOODS = 10; 
    private final static int MAX_LENGTH = 10;

    public int difficulty = 1; //1: fácil; 2: medio; 4: difícil
    public int gameMode; //1: máx comidas; 2: máx longitud 
    
    private final static long TICK_DELAY = 100;
    
    private AtomicBoolean started = new AtomicBoolean(false); //Determina si la partida ha arrancado
    private AtomicInteger numSnakes = new AtomicInteger(); //Determina el número de snakes que contiene la partida
    private AtomicInteger numFoods = new AtomicInteger(); //Determina el índice de la comida a generar
    private ScheduledExecutorService scheduler;

    public SnakeGame(int dif, String ad, int gm) {
        difficulty = dif;
        gameMode = gm;
        admin = ad;
        rn = new Random();
    }

    public int[] newFood() { //Genera una nueva comida
        int comida[] = {rn.nextInt(Location.PLAYFIELD_WIDTH / 10) * 10, rn.nextInt(Location.PLAYFIELD_HEIGHT / 10) * 10};
        int count = numFoods.getAndIncrement();
        comidas.put(count, comida);
        return comida;
    }

    public void addSnake(Snake snake) {
        snakes.put(snake.getId(), snake);
        snake.reestartScore();
        numSnakes.getAndIncrement();
        updateLegend();
    }

    public Collection<Snake> getSnakes() {
        return snakes.values();
    }

    public void removeSnake(Snake snake) {
        snakes.remove(Integer.valueOf(snake.getId()));
        snake.resetState();
        int count = numSnakes.decrementAndGet();
        if (count == 0) {
            stopTimer();
        }
    }

    public void updateLegend() { //Actualiza las puntuaciones en la lista de récords de SnakeHandler cuando se ha terminado una partida
        try {
            StringBuilder sb = new StringBuilder();
            StringBuilder colors = new StringBuilder();
            StringBuilder scores = new StringBuilder();
            for (Snake snake : getSnakes()) {
                sb.append('"');
                sb.append(snake.getName());
                sb.append('"');
                sb.append(',');

                colors.append('"');
                colors.append(snake.getHexColor());
                colors.append('"');
                colors.append(',');

                scores.append('"');
                scores.append(snake.getScore());
                scores.append('"');
                scores.append(',');
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            if (colors.length() > 0) {
                colors.deleteCharAt(colors.length() - 1);
            }
            if (scores.length() > 0) {
                scores.deleteCharAt(scores.length() - 1);
            }
            //Se notifica de los nuevos records a los clientes:
            String msg = String.format("{\"type\": \"updateLegend\", \"names\" :[ " + sb.toString() + " ], \"colors\" :[" + colors.toString() + "], \"scores\" : [" + scores.toString() + "]}");
            broadcast(msg);
        } catch (Throwable ex) {
            System.err.println("Exception processing legend");
            ex.printStackTrace(System.err);
        }
    }

    private synchronized void tick() {
        try {
            if (checkGameFinished()) { //Si se ha cumplido la condición de final de juego, se para el contador y se manda un mensaje de final de partida
                broadcast(String.format("{\"type\": \"endGame\" }"));
                stopTimer();
            } else { //Si no, se actualiza el juego: movimientos, colisiones, número de comidas, etcétera.
                for (Snake snake : getSnakes()) {
                    snake.update(getSnakes());
                  
                    if (snake.handleCollisions(getSnakes())) {
                        updateLegend();
                    }
                    
                    synchronized (comidas) {
                        int c = snake.handleCollisionsFoods(comidas);
                        if (c >= 0) {
                            updateLegend();
                            comidas.remove(c);
                            broadcast("{\"type\":\"updateFood\", \"id\":" + c + ", \"tru\" : false}");

                            int count = numFoods.get();
                            int[] comida = newFood();
                            broadcast("{\"type\":\"updateFood\", \"id\":" + count + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
                        }
                    }
                }
                
                StringBuilder sb = new StringBuilder();
                for (Snake snake : getSnakes()) {
                    sb.append(getLocationsJson(snake));
                    sb.append(',');
                }
                
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                }
                
                String msg = String.format("{\"type\": \"update\", \"data\" : [%s]}", sb.toString()); //Se manda la información actualizada de la partida a los jugadores
                broadcast(msg);
            }
        } catch (Throwable ex) {
            System.err.println("Exception processing tick()");
            ex.printStackTrace(System.err);
        }
    }

    private boolean checkGameFinished() {
        boolean resultado = false;

        switch (gameMode) {
            case 1: //Se acaba la partida al comer un número máximo de comidas
                if (numFoods.get() > MAX_FOODS * difficulty) {
                    resultado = true;
                }
                break;
            case 2: //Se acaba la partida al alcanzar una longitud máxima de snake
                for (Snake s : snakes.values()) {
                    if (s.getLength() > MAX_LENGTH * difficulty) {
                        resultado = true;
                    }
                }
                break;
            default:
                break;
        }
        return resultado;
    }

    public void getFoods(Snake s) throws Exception { //Actualiza las comidas presentes en el mapa
        synchronized (comidas) {
        	
            for (int comida : comidas.keySet()) {
                s.sendMessage("{\"type\":\"updateFood\", \"id\":" + comida + ", \"tru\" : true, \"pos\" : [" + comidas.get(comida)[0] + "," + comidas.get(comida)[1] + "]}");
            }
        }
    }

    private String getLocationsJson(Snake snake) {
        synchronized (snake) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"x\": %d, \"y\": %d}", snake.getHead().x, snake.getHead().y));
            for (Location location : snake.getTail()) {
                sb.append(",");
                sb.append(String.format("{\"x\": %d, \"y\": %d}", location.x, location.y));
            }
            return String.format("{\"id\":%d,\"body\":[%s]}", snake.getId(), sb.toString());
        }
    }

    public void broadcast(String message) throws Exception {
        for (Snake snake : getSnakes()) {
            try {
                System.out.println("Sending message " + message + " to " + snake.getId());
                snake.sendMessage(message);
            } catch (Throwable ex) {
                System.err.println("Exception sending message to snake " + snake.getId());
                ex.printStackTrace(System.err);
                removeSnake(snake);
            }
        }
    }

    public boolean empezada() {
        return started.get();
    }

    public boolean empezada(boolean b) {
        return started.getAndSet(b);
    }

    public void startTimer() {
        started.set(true);
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY / difficulty, TICK_DELAY / difficulty, TimeUnit.MILLISECONDS); //La velocidad del juego se modifica en función de la dificultad
    }

    public void stopTimer() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        started.set(false);
    }

    public synchronized String getAdmin() {
        return admin;
    }
}
