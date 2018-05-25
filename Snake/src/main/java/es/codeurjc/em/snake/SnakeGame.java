package es.codeurjc.em.snake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        
        private String admin;
        private ConcurrentHashMap<Integer, int[]> comidas = new ConcurrentHashMap<>();
        
        private Random rn;
        
	private final static long TICK_DELAY = 100;
        public int difficulty = 1;
        private  AtomicBoolean started = new AtomicBoolean(false);
	private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>();
	private AtomicInteger numSnakes = new AtomicInteger();
        private AtomicInteger numFoods = new AtomicInteger();
	private ScheduledExecutorService scheduler;
        
        
        public SnakeGame(int dif, String ad){
            difficulty = dif;
            admin = ad;
            rn = new Random();
        }
        public int [] newFood(){
           int comida [] = {rn.nextInt(Location.PLAYFIELD_WIDTH/10) * 10, rn.nextInt(Location.PLAYFIELD_HEIGHT/10) * 10};
           int count = numFoods.getAndIncrement();
           comidas.put(count, comida);
           return comida;
        }
       
        public synchronized String getAdmin(){
            return admin;
        }
        
        
        
	public void addSnake(Snake snake) {

		snakes.put(snake.getId(), snake);

		int count = numSnakes.getAndIncrement();
                
		if (count ==3 && !started.get()) {
			startTimer();
		}
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

    private synchronized void tick() {
         
            
            try {
                for (Snake snake : getSnakes()) {
                    snake.update(getSnakes());
                    synchronized(comidas){
                    int c = snake.handleCollisionsFoods(comidas);
                    if (c >= 0) {
                        comidas.remove(c);
                        broadcast("{\"type\":\"updateFood\", \"id\":" + c + ", \"tru\" : false}");

                        synchronized (numFoods) {

                            int count = numFoods.get();
                            int[] comida = newFood();
                            broadcast("{\"type\":\"updateFood\", \"id\":" + count + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
                        }
                        //broadcast("{\"type\":\"updateFood\", \"id\":" +0 + ", \"tru\" : true, \"pos\" : [50,70]}");
                    }
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (Snake snake : getSnakes()) {
                    sb.append(getLocationsJson(snake));
                    sb.append(',');
                }
                sb.deleteCharAt(sb.length() - 1);
                String msg = String.format("{\"type\": \"update\", \"data\" : [%s]}", sb.toString());

                broadcast(msg);

            } catch (Throwable ex) {
                System.err.println("Exception processing tick()");
                ex.printStackTrace(System.err);
            }
        
    }
    public void getFoods(WebSocketSession session) throws Exception{
      synchronized(comidas){
        
    
        for(int comida : comidas.keySet()){
                     session.sendMessage(new TextMessage("{\"type\":\"updateFood\", \"id\":" + comida + ", \"tru\" : true, \"pos\" : [" + comidas.get(comida)[0] + "," + comidas.get(comida)[1] + "]}"));
                    
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
        public boolean empezada(){
            return started.get();
        }
        public boolean empezada(boolean b){
            return started.getAndSet(b);
        }

	public void startTimer() {
                started.set(true);
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY/difficulty, TICK_DELAY/difficulty, TimeUnit.MILLISECONDS);
	}

	public void stopTimer() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
                started.set(false);
	}
}
