package es.codeurjc.em.snake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

public class SnakeHandler extends TextWebSocketHandler {

	private static final String SNAKE_ATT = "snake";

	private AtomicInteger snakeIds = new AtomicInteger(0);

	private ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, SnakeGame> SnakeGames = new ConcurrentHashMap<>(); 
	
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);

        int id = snakeIds.getAndIncrement();

        Snake s = new Snake(id, session);

        session.getAttributes().put(SNAKE_ATT, s);

        synchronized (SnakeGames) {
            if (!SnakeGames.isEmpty()) {
                Set<String> keys = SnakeGames.keySet();
                ObjectMapper mapper = new ObjectMapper();
                String mapeado = mapper.writeValueAsString(keys);
                System.out.println("" + mapeado + "");
	               //String[] lista = (String[]) keys.toArray();

                session.sendMessage(new TextMessage("{\"type\":\"roomsCreated\", \"rooms\":" + mapeado + "}"));
            }
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

            switch (node.get("op").asText()) {
                case "Name": {
                    String n = node.get("value").asText();
                    s.setName(n);

                }

                break;
                case "Dir": {
                    Direction d = Direction.valueOf(node.get("value").asText().toUpperCase());
                    s.setDirection(d);

                }
                break;

                case "GameName": {
                    String gn1 = node.get("value").asText();

                    synchronized (SnakeGames) {
                        if (SnakeGames.containsKey(gn1)) {
                            session.sendMessage(new TextMessage("{\"type\":\"gameNameNotValid\"}"));
                        } else {
                            session.sendMessage(new TextMessage("{\"type\":\"newRoomSettings\"}"));
                        }
                    }
                }

                break;

                case "createGame": {
                    String gn2 = node.get("value").asText();
                   
                    SnakeGames.put(gn2, new SnakeGame( node.get("dif").asInt()));
                    

                    for (WebSocketSession participant : sessions.values()) {
                        participant.sendMessage(new TextMessage("{\"type\":\"newRoom\", \"name\":\"" + gn2 + "\"}"));
                    }

                    session.sendMessage(new TextMessage("{\"type\":\"newRoomCreator\", \"name\":\"" + gn2 + "\"}"));
                }

                break;

                case "JoinGame": {
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);
                    synchronized (snGm) {

                        if (snGm.getSnakes().size() < 4) {
                            session.getAttributes().put("snakeGame", gn3);

                            snGm.addSnake(s);

                            StringBuilder sb = new StringBuilder();
                            for(Snake snake : snGm.getSnakes()) {
                                sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
                                sb.append(',');
                            }
                            sb.deleteCharAt(sb.length() - 1);
                            String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());

                            snGm.broadcast(msg);
                            if(snGm.getSnakes().size() ==4){
                                msg = String.format("{\"type\": \"hideStartButton\"}", sb.toString());
                                snGm.broadcast(msg);
                            }
                            
                        } else {
                            session.sendMessage(new TextMessage("{\"type\":\"gameFull\", \"idRoom\":\"" + gn3 + "\"}"));
                        }

                    }

                }
                break;
                case "tryToJoin":{ //CodigoCopiado de JoinGame, se puede hacer limpieza
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);
                    synchronized (snGm) {

                        if (snGm.getSnakes().size() < 4) {
                            session.getAttributes().put("snakeGame", gn3);
                            snGm.addSnake(s);
                            StringBuilder sb = new StringBuilder();
                            for (Snake snake : snGm.getSnakes()) {
                                sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
                                sb.append(',');
                            }
                            sb.deleteCharAt(sb.length() - 1);
                            String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());

                            snGm.broadcast(msg);
                        }
                    }
                }
                    
                    break;
                case "LeaveGame": {

                    boolean adminLeave = node.get("isAdmin").asBoolean();
                    String gn = (String) session.getAttributes().get("snakeGame");
                   
                    SnakeGame snGm = SnakeGames.get(gn);

                    synchronized (snGm) {

                        if (adminLeave) {
                            /*for (Snake snake : SnakeGames.get(gn).getSnakes()) {			
                             msg = String.format("{\"type\": \"kicked\", \"id\": %d}", snake.getId());
								
                             SnakeGames.get(gn).broadcast(msg);
								
                             SnakeGames.get(gn).removeSnake(snake);
                             }*/

                            snGm.broadcast(String.format("{\"type\": \"kicked\"}"));
                            for (Snake snake : snGm.getSnakes()) {
                                snGm.removeSnake(snake);
                            }
                            for (WebSocketSession participant : sessions.values()) {
                                participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                            }
                            SnakeGames.remove(gn);
                            
                        } else if (snGm.empezada() && snGm.getSnakes().size() == 2) {
                            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
                            snGm.broadcast(msg);
                            snGm.removeSnake(s);

                            snGm.stopTimer();
                            snGm.broadcast(String.format("{\"type\": \"endGame\" }"));
                            //\"id\":"+gn+"
                            for (WebSocketSession participant : sessions.values()) {
                                participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                            }
                            SnakeGames.remove(gn);
                        } else {//No ha sido el admin y la partida sigue. solo se va uno.
                            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());

                            snGm.broadcast(msg);
                            snGm.removeSnake(s);
                        }
                    }
                     //session.getAttributes().put("snakeGame", null);
                }
                break;

                //startGame
                case "startGame": {
                    SnakeGame snGm = SnakeGames.get((String) session.getAttributes().get("snakeGame"));
                    if (snGm.getSnakes().size() > 1) {
                        snGm.startTimer();
                        session.sendMessage(new TextMessage(String.format("{\"type\": \"enoughPlayers\"}")));
                    } else {
                        session.sendMessage(new TextMessage(String.format("{\"type\": \"notEnoughPlayers\"}")));
                    }
                }

                break;
                
                case "matchMaking":
                	synchronized(SnakeGames) {
                		int aux = 0;
                    	String auxK = "";
                         for(String k : SnakeGames.keySet()){
                        	 if((SnakeGames.get(k).getSnakes().size() < 4) && (SnakeGames.get(k).getSnakes().size() > aux)) {
                        		 aux = SnakeGames.get(k).getSnakes().size();
                        		 auxK = k;
                        		 if (aux == 3) {
                        			 break;
                        		 }
                        	 }
                         }
                         
                         if(auxK != "") {
                  		 	session.sendMessage(new TextMessage(String.format("{\"type\":\"matchMaking\", \"room\":\"" + auxK + "\"}")));
                         }else {
                     		session.sendMessage(new TextMessage(String.format("{\"type\":\"matchMakingError\"}")));
                         }
                	}

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
            synchronized (SnakeGames) {
                if (gn!= null && SnakeGames.contains(gn) ) {

                    SnakeGames.get(gn).removeSnake(s);
                    String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
                    SnakeGames.get(gn).broadcast(msg);
                }
            }
           
            sessions.remove(session.getId());
            
            
    }

}
