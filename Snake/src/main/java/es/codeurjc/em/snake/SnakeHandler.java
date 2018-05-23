package es.codeurjc.em.snake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

public class SnakeHandler extends TextWebSocketHandler {

	private static final String SNAKE_ATT = "snake";

	private AtomicInteger snakeIds = new AtomicInteger(0);

	private ConcurrentHashMap<String, SnakeGame> SnakeGames = new ConcurrentHashMap<>(); 

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            int id = snakeIds.getAndIncrement();

            Snake s = new Snake(id, session);

            session.getAttributes().put(SNAKE_ATT, s);
              if (!SnakeGames.isEmpty()) {
                Set<String> keys = SnakeGames.keySet();
                ObjectMapper mapper = new ObjectMapper();
                String mapeado = mapper.writeValueAsString(keys);
                System.out.println(""+mapeado+"");
                //String[] lista = (String[]) keys.toArray();
                
                session.sendMessage(new TextMessage("{\"type\":\"roomsCreated\", \"rooms\":" +mapeado + "}"));
             }
             
        }

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

		try {

			String payload = message.getPayload();

			if (payload.equals("ping")) {
				return;
			}

			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(payload);
			
			Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
			
			switch (node.get("op").asText())
			{
				case "Name":
					String n = node.get("value").asText();
					s.setName(n);
					break;
					
				case "Dir":
					Direction d = Direction.valueOf(node.get("value").asText().toUpperCase());
					s.setDirection(d);
					break;
					
				case "GameName":
					String gn1 = node.get("value").asText();
					
					if (SnakeGames.containsKey(gn1)) {
						session.sendMessage(new TextMessage("{\"type\":\"gameNameValid\", \"data\":false}"));
					}
					else {
						SnakeGames.put(gn1, new SnakeGame());
						session.sendMessage(new TextMessage("{\"type\":\"gameNameValid\", \"data\":true, \"name\":\"" + gn1 + "\"}"));
					}
					
					break;
					
				case "JoinGame":
					
					String gn2 = node.get("value").asText();
					
					session.getAttributes().put("snakeGame", gn2);
					
					SnakeGames.get(gn2).addSnake(s);

					StringBuilder sb = new StringBuilder();
					for (Snake snake : SnakeGames.get(gn2).getSnakes()) {			
						sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
						sb.append(',');
					}
					sb.deleteCharAt(sb.length()-1);
					String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());
					
					SnakeGames.get(gn2).broadcast(msg);
					break;
					
				default:
					break;
			}
			
		} catch (Exception e) {
			System.err.println("Exception processing message " + message.getPayload());
			e.printStackTrace(System.err);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

		System.out.println("Connection closed. Session " + session.getId());

		Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
		String gn = (String) session.getAttributes().get("snakeGame");
		
		SnakeGames.get(gn).removeSnake(s);

		String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
		
		SnakeGames.get(gn).broadcast(msg);
	}

}
