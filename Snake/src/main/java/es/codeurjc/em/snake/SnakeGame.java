package es.codeurjc.em.snake;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SnakeGame {

	private final static long TICK_DELAY = 100;
        private  AtomicBoolean started = new AtomicBoolean(false);
	private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>();
	private AtomicInteger numSnakes = new AtomicInteger();

	private ScheduledExecutorService scheduler;

	public void addSnake(Snake snake) {

		snakes.put(snake.getId(), snake);

		int count = numSnakes.getAndIncrement();

		if (count ==3) {
			startTimer();
		}
	}

	public Collection<Snake> getSnakes() {
		return snakes.values();
	}

	public void removeSnake(Snake snake) {

		snakes.remove(Integer.valueOf(snake.getId()));

		int count = numSnakes.decrementAndGet();

		if (count == 0) {
			stopTimer();
		}
	}

	private void tick() {

		try {

			for (Snake snake : getSnakes()) {
				snake.update(getSnakes());
			}

			StringBuilder sb = new StringBuilder();
			for (Snake snake : getSnakes()) {
				sb.append(getLocationsJson(snake));
				sb.append(',');
			}
			sb.deleteCharAt(sb.length()-1);
			String msg = String.format("{\"type\": \"update\", \"data\" : [%s]}", sb.toString());

			broadcast(msg);

		} catch (Throwable ex) {
			System.err.println("Exception processing tick()");
			ex.printStackTrace(System.err);
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
				System.err.println("Execption sending message to snake " + snake.getId());
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
		scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY, TICK_DELAY, TimeUnit.MILLISECONDS);
	}

	public void stopTimer() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
                started.set(false);
	}
}
