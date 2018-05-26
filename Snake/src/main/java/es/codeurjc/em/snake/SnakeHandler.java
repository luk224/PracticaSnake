	package es.codeurjc.em.snake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnakeHandler extends TextWebSocketHandler {

    private static final String SNAKE_ATT = "snake";

    private AtomicInteger snakeIds = new AtomicInteger(0);

    private List<WebSocketSession> lobbyPlayers = Collections.synchronizedList(new ArrayList<>());
    private Set<String> connectedPlayers = Collections.synchronizedSet(new HashSet<>());
    private ConcurrentHashMap<String, Integer> playerScores = new ConcurrentHashMap<>();
    
    private ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, SnakeGame> SnakeGames = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    	
    	if(session.getId().equals("0"))
            readFile();
    	
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
        
        session.sendMessage(new TextMessage("{\"type\":\"updateRecords\", \"records\":" + recordsToJSON() + "}"));
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
                    
                    synchronized (connectedPlayers) {
	                    if (connectedPlayers.contains(n)) {
	                    	session.sendMessage(new TextMessage("{\"type\":\"userNameNotValid\"}"));
	                    }
	                    else {
	                    	connectedPlayers.add(n);
	                    	s.setName(n);
	                        addLobby(session);
	                    }
                    }
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

                    SnakeGames.put(gn2, new SnakeGame(node.get("dif").asInt(), session.getId(), node.get("gameMode").asInt()));

                    for (WebSocketSession participant : sessions.values()) {
                        participant.sendMessage(new TextMessage("{\"type\":\"newRoom\", \"name\":\"" + gn2 + "\"}"));
                    }

                    session.sendMessage(new TextMessage("{\"type\":\"newRoomCreator\", \"name\":\"" + gn2 + "\"}"));
                }

                break;

                case "JoinGame": {
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);

                    removeLobby(session);
                    
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
                            if (snGm.getSnakes().size() == 4 && !snGm.empezada()) {
                                int[] comida = snGm.newFood();
                                snGm.broadcast("{\"type\":\"updateFood\", \"id\":" + 0 + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
                                
                                msg = String.format("{\"type\": \"hideStartButton\"}", sb.toString());
                                snGm.broadcast(msg);
                                snGm.startTimer();

                            }
                            snGm.getFoods(session);

                        } else {
                            session.sendMessage(new TextMessage("{\"type\":\"gameFull\", \"idRoom\":\"" + gn3 + "\"}"));
                        }

                    }

                }
                break;
                case "tryToJoin": { //CodigoCopiado de JoinGame, se puede hacer limpieza
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);
                    
                    synchronized (snGm) {

                        if (snGm.getSnakes().size() < 4) {
                            removeLobby(session);
                        	
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
                            
                            if (snGm.getSnakes().size() == 4 && !snGm.empezada()) {
                                int[] comida = snGm.newFood();
                                snGm.broadcast("{\"type\":\"updateFood\", \"id\":" + 0 + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
                                
                                msg = String.format("{\"type\": \"hideStartButton\"}", sb.toString());
                                snGm.broadcast(msg);
                                snGm.startTimer();
                            }
                            snGm.getFoods(session);
                        }
                    }
                }

                break;
                case "LeaveGame": {

                    boolean adminLeave = node.get("isAdmin").asBoolean();
                    String gn = (String) session.getAttributes().get("snakeGame");

                    SnakeGame snGm = SnakeGames.get(gn);
                    
                    synchronized (snGm) {

                        if (session.getId().equals(snGm.getAdmin())) {
                            /*for (Snake snake : SnakeGames.get(gn).getSnakes()) {			
                             msg = String.format("{\"type\": \"kicked\", \"id\": %d}", snake.getId());
								
                             SnakeGames.get(gn).broadcast(msg);
								
                             SnakeGames.get(gn).removeSnake(snake);
                             }*/

                            snGm.broadcast(String.format("{\"type\": \"kicked\"}"));
                            for (Snake snake : snGm.getSnakes()) {
                            	addLobby(snake.getSession());
                                snGm.removeSnake(snake);
                            }
                            for (WebSocketSession participant : sessions.values()) {
                                participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                            }
                            SnakeGames.remove(gn);

                        } else if (snGm.empezada() && snGm.getSnakes().size() == 2) {
                        	addLobby(session);
                        	
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
                        	addLobby(session);
                        	
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

                        int[] comida = snGm.newFood();
                        snGm.broadcast("{\"type\":\"updateFood\", \"id\":" + 0 + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
                     
                        session.sendMessage(new TextMessage(String.format("{\"type\": \"enoughPlayers\"}")));
                    } else {
                        session.sendMessage(new TextMessage(String.format("{\"type\": \"notEnoughPlayers\"}")));
                    }
                }

                break;
                
                case "deleteRoomRequest": {
                    String gn = (String) session.getAttributes().get("snakeGame");
                    SnakeGame snGm = SnakeGames.get(gn);
                    
                    for (Snake snake : snGm.getSnakes()) {
                        playerScores.putIfAbsent(snake.getName(), 0);
                        int newScore = playerScores.get(snake.getName()) + snake.getScore();
                        playerScores.put(snake.getName(), newScore);
                        snGm.removeSnake(snake);
                    }

                    for (WebSocketSession participant : sessions.values()) {
                        participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                        participant.sendMessage(new TextMessage("{\"type\":\"updateRecords\", \"records\":" + writeFile() + "}"));
                    }
                     SnakeGames.remove(gn);
                }
                break;

                case "matchMaking":{
                    synchronized (SnakeGames) {
                        int aux = 0;
                        String auxK = "";
                        for (String k : SnakeGames.keySet()) {
                            if ((SnakeGames.get(k).getSnakes().size() < 4) && (SnakeGames.get(k).getSnakes().size() > aux)) {
                                aux = SnakeGames.get(k).getSnakes().size();
                                auxK = k;

                                if (aux == 3) {
                                    break;
                                }
                            }
                        }

                        if (auxK != "") {
                            System.out.println("auxK " + auxK);
                            session.sendMessage(new TextMessage(String.format("{\"type\":\"matchMaking\", \"room\":\"" + auxK + "\"}")));
                        } else {
                            session.sendMessage(new TextMessage(String.format("{\"type\":\"matchMakingError\"}")));
                        }
                    }
                }
                    break;
                case "requestRoomData":{
                    SnakeGame g = SnakeGames.get(node.get("value").asText());
                    
                    StringBuilder sb = new StringBuilder();
                        for (Snake sn : g.getSnakes()) {
                            sb.append(sn.getName());
                            sb.append(", ");
                        }
                        sb.deleteCharAt(sb.length() - 1);
                        sb.deleteCharAt(sb.length() - 1);
                        
                        String dif;
                        if(g.difficulty == 1){
                            dif = "Easy";
                        }else if(g.difficulty == 2){
                            dif = "Normal";
                        }else{
                            dif = "Hard";
                        }
                        
                        String mode = "";
                        if(g.gameMode == 1){
                            mode = "Max number of fruits";
                        }else if(g.gameMode == 2){
                            mode = "Max length of snakes";
                        }
                        
                    session.sendMessage(new TextMessage(String.format("{\"type\":\"joinConfirmed\", \"number\":" + g.getSnakes().size() + ", \"room\":\"" + node.get("value").asText() + "\", \"difficulty\":\"" + dif + "\", \"gameMode\":\"" + mode + "\",\"players\":\""+ sb +"\"}")));
                }                    
                break;
                
                case "chat":
                	String msg = "<b>" + s.getName() + ":</b> " + node.get("message").asText();
                	
                	for (WebSocketSession participant : lobbyPlayers) {
                			participant.sendMessage(new TextMessage("{\"type\":\"chat\", \"msg\":\"" + msg + "\"}"));
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
    /*
     @Override
     public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

     System.out.println("Connection closed. Session " + session.getId());

     Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
     String gn = (String) session.getAttributes().get("snakeGame");
     synchronized (SnakeGames) {
     if (gn != null && SnakeGames.contains(gn)) {

     SnakeGames.get(gn).removeSnake(s);
     String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
     SnakeGames.get(gn).broadcast(msg);
     }
     }

     sessions.remove(session.getId());

     }*/

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        System.out.println("Connection closed. Session " + session.getId());

        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
        String gn = (String) session.getAttributes().get("snakeGame");
        sessions.remove(session.getId());
        
        removeLobby(session);
        
        connectedPlayers.remove(s.getName());

        if ((gn != null) && SnakeGames.containsKey(gn)) {
            System.out.println("Print 1");
            SnakeGame snGm = SnakeGames.get(gn);
            synchronized (snGm) {

                snGm.removeSnake(s);
                if (session.getId().equals(snGm.getAdmin())) {
                    System.out.println("If");
                    snGm.broadcast(String.format("{\"type\": \"kicked\"}"));
                    for (Snake snake : snGm.getSnakes()) {
                        snGm.removeSnake(snake);
                    }
                    for (WebSocketSession participant : sessions.values()) {
                        participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                    }
                    SnakeGames.remove(gn);

                } else if (snGm.empezada() && snGm.getSnakes().size() == 1) {
                    System.out.println("Else if  ");
                    String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
                    snGm.broadcast(msg);
                    snGm.stopTimer();
                    snGm.broadcast(String.format("{\"type\": \"endGame\" }"));
                    for (WebSocketSession participant : sessions.values()) {
                        participant.sendMessage(new TextMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}"));
                    }
                    SnakeGames.remove(gn);
                } else {//No ha sido el admin y la partida sigue. solo se va uno.
                    System.out.println("Else");
                    String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());

                    snGm.broadcast(msg);
                }

            }
        }
    }
    
    public void addLobby(WebSocketSession session)
    {
    	lobbyPlayers.add(session);
    	
    	refreshLobby();
    }
    
    public void removeLobby(WebSocketSession session)
    {
    	synchronized (lobbyPlayers)
    	{
	    	if (lobbyPlayers.contains(session))
	    	{
	    		lobbyPlayers.remove(session);
	    	}
    	}
    	
    	refreshLobby();
    }
    
    public void refreshLobby()
    {
    	try {
    		
	        StringBuilder sb = new StringBuilder();
	        
	        for (WebSocketSession participant : lobbyPlayers) {
	        	Snake s = (Snake) participant.getAttributes().get(SNAKE_ATT);
	        	
	            sb.append('"');
	            sb.append(s.getName());
	            sb.append('"');
	            sb.append(',');
	        }
	        
	        sb.deleteCharAt(sb.length() - 1);
	
	        String msg = String.format("{\"type\": \"updateChatList\", \"names\" :[ " + sb + " ]}");
	
	    	for (WebSocketSession participant : lobbyPlayers) {
					participant.sendMessage(new TextMessage(msg));
	    	}
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    public StringBuilder recordsToJSON() {
        StringBuilder sb = new StringBuilder();
        
        synchronized (playerScores) {
            List<Entry<String, Integer>> list = new ArrayList<>(playerScores.entrySet());
            list.sort(Entry.comparingByValue());

            if (list.size() <= 0) {
                sb.append("[]");
            } else {
                sb.append("[");
                for (int i = list.size() - 1; i > list.size() - 11 && i >= 0; i--) {
                    sb.append("[\"" + list.get(i).getKey() + "\",");
                    sb.append(list.get(i).getValue().toString() + "] ,");
                }
                sb.deleteCharAt(sb.length() - 1);
                sb.append("]");
            }
        }
        
        return sb;
    }
    
    public StringBuilder writeFile()
    {
        String fileName = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main" 
        + File.separator + "resources" + File.separator + "static" + File.separator + "Records.json";
        
        StringBuilder recs = recordsToJSON();
        
        // Escritura en el archivo
        File myFile = new File(fileName);

        try {
            myFile.createNewFile();
            
            FileOutputStream fOut = new FileOutputStream(myFile);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            
            myOutWriter.append(recs);
            
            myOutWriter.close();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return recs;
    }
    
    public void readFile() {
        String fileName = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main"
                + File.separator + "resources" + File.separator + "static" + File.separator + "Records.json";

        try {
            File myFile2 = new File(fileName);
            FileInputStream fIn;
            fIn = new FileInputStream(myFile2);
            BufferedReader myReader = new BufferedReader(new InputStreamReader(fIn));
            String aDataRow = "";
            String aBuffer = "";
            while ((aDataRow = myReader.readLine()) != null) {
                aBuffer += aDataRow;
            }
            myReader.close();
            System.out.println("aBuffer: "+ aBuffer);
            
            JSONArray jsonarray = new JSONArray(aBuffer);
            for (int i = 0; i < jsonarray.length(); i++) {
                JSONArray jug = jsonarray.getJSONArray(i);
                String name =  jug.getString(0);
                int pun =  Integer.parseInt(jug.getString(1));
                
                playerScores.put(name, pun);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
