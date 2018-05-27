package es.codeurjc.em.snake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnakeHandler extends TextWebSocketHandler {

    private static final String SNAKE_ATT = "snake";

    private Lock lockRecords = new ReentrantLock();
    private Lock lockSnakeGames = new ReentrantLock();
    private Lock lockConnectedPlayers = new ReentrantLock();
    
    private AtomicInteger snakeIds = new AtomicInteger(0);
    private AtomicBoolean loadedRecords = new AtomicBoolean(false);
    
    private List<WebSocketSession> lobbyPlayers = Collections.synchronizedList(new ArrayList<>());
    private Set<String> connectedPlayers = Collections.synchronizedSet(new HashSet<>());
    private ConcurrentHashMap<String, Integer> playerScores = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, SnakeGame> SnakeGames = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    	System.out.println("Welcome Session: " + session.getId());
    	
        if (!loadedRecords.get())
        {
            readFile();
        	loadedRecords.set(true);;
        }

        sessions.put(session.getId(), session);

        int id = snakeIds.getAndIncrement();

        Snake s = new Snake(id, session);

        session.getAttributes().put(SNAKE_ATT, s);

        lockSnakeGames.lock();
        if (!SnakeGames.isEmpty()) {
            Set<String> keys = SnakeGames.keySet();
            lockSnakeGames.unlock();
            ObjectMapper mapper = new ObjectMapper();
            String mapeado = mapper.writeValueAsString(keys);
            System.out.println("" + mapeado + "");

            s.sendMessage("{\"type\":\"roomsCreated\", \"rooms\":" + mapeado + "}");
        }
        else
        	lockSnakeGames.unlock();
        
        s.sendMessage("{\"type\":\"updateRecords\", \"records\":" + recordsToJSON() + "}");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    	synchronized(session) {
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

                    lockConnectedPlayers.lock();
                    if (connectedPlayers.contains(n)) {
                    	lockConnectedPlayers.unlock();
                    	
                        s.sendMessage("{\"type\":\"userNameNotValid\"}");
                    } else {
                        connectedPlayers.add(n);
                        lockConnectedPlayers.unlock();
                        
                        s.setName(n);
                        addLobby(session);
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

                    if (SnakeGames.containsKey(gn1)) {
                        s.sendMessage("{\"type\":\"gameNameNotValid\"}");
                    } else {
                        s.sendMessage("{\"type\":\"newRoomSettings\"}");
                    }
                }
                break;

                case "createGame": {
                    String gn2 = node.get("value").asText();

                    SnakeGames.put(gn2, new SnakeGame(node.get("dif").asInt(), session.getId(), node.get("gameMode").asInt()));

                    for (WebSocketSession participant : sessions.values()) {
                    	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                        spart.sendMessage("{\"type\":\"newRoom\", \"name\":\"" + gn2 + "\"}");
                    }
                    s.sendMessage("{\"type\":\"newRoomCreator\", \"name\":\"" + gn2 + "\"}");
                }
                break;

                case "JoinGame": {
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);

                    removeLobby(session);

                    if (snGm != null) {
	                    synchronized (snGm) {
	                        if (snGm.getSnakes().size() < 4) {
	                            joinGameConfirmed(session, gn3, snGm, s);
	                        } else {
	                            s.sendMessage("{\"type\":\"gameFull\", \"idRoom\":\"" + gn3 + "\"}");
	                        }
	                    }
                    }
                }
                break;

                case "tryToJoin": {
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);
                    
                    if (snGm != null) {
	                    synchronized (snGm) {
	                        if (snGm.getSnakes().size() < 4) {
	                            joinGameConfirmed(session, gn3, snGm, s);
	                        }
	                    }
                    }
                }
                break;

                case "LeaveGame": {
                    String gn = (String) session.getAttributes().get("snakeGame");
                    SnakeGame snGm = SnakeGames.get(gn);
                    
                    if (snGm != null) {
	                    synchronized (snGm) {
	
	                        if (session.getId().equals(snGm.getAdmin())) {
	                            s.sendMessage(String.format("{\"type\": \"kicked\"}"));
	                            snGm.removeSnake(s);
	                        } else if (snGm.empezada() && snGm.getSnakes().size() == 2) {
	                            addLobby(session);
	                            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
	                            s.sendMessage(msg);
	                            s.sendMessage("{\"type\": \"endGame\" }");
	                            snGm.removeSnake(s);
	                            SnakeGames.remove(gn);
	                        } else {
	                            addLobby(session);
	                            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
	                            s.sendMessage(msg);
	                            snGm.removeSnake(s);
	                        }
	                        
	                        exitGame(session, snGm, gn, s);
	                    }
                    }
                }
                break;

                case "startGame": {
                    SnakeGame snGm = SnakeGames.get((String) session.getAttributes().get("snakeGame"));
                    if (snGm.getSnakes().size() > 1) {
                        startGame(snGm);
                    } else {
                        s.sendMessage(String.format("{\"type\": \"notEnoughPlayers\"}"));
                    }
                }
                break;

                case "deleteRoomRequest": {
                    String gn = (String) session.getAttributes().get("snakeGame");
                    SnakeGame snGm = SnakeGames.get(gn);
                    
                    if (snGm != null) {
	                    synchronized (snGm) {
		                    for (Snake snake : snGm.getSnakes()) {
		                        playerScores.putIfAbsent(snake.getName(), 0);
		                        int newScore = playerScores.get(snake.getName()) + snake.getScore();
		                        playerScores.put(snake.getName(), newScore);
		                        snGm.removeSnake(snake);
		                    }
	                    }
                    }
                    
                    for (WebSocketSession participant : sessions.values()) {
                    	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                        spart.sendMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}");
                        spart.sendMessage("{\"type\":\"updateRecords\", \"records\":" + writeFile() + "}");
                    }
                    SnakeGames.remove(gn);
                }
                break;

                case "matchMaking": {
                	int aux = 0;
                    String auxK = "";
                	
                    lockSnakeGames.lock();
                    for (String k : SnakeGames.keySet()) {
                        if ((SnakeGames.get(k).getSnakes().size() < 4) && (SnakeGames.get(k).getSnakes().size() > aux)) {
                            aux = SnakeGames.get(k).getSnakes().size();
                            auxK = k;
                            if (aux == 3) {
                                break;
                            }
                        }
                    }
                    lockSnakeGames.unlock();

                    if (!"".equals(auxK)) {
                        System.out.println("auxK " + auxK);
                        s.sendMessage(String.format("{\"type\":\"matchMaking\", \"room\":\"" + auxK + "\"}"));
                    } else {
                        s.sendMessage(String.format("{\"type\":\"matchMakingError\"}"));
                    }
                }
                break;
                    
                case "requestRoomData": {
                    requestRoomData(session, node.get("value").asText());
                }
                break;

                case "chat":{
                    String msg = "<b>" + s.getName() + ":</b> " + node.get("message").asText();

                    for (WebSocketSession participant : lobbyPlayers) {
                    	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                        spart.sendMessage("{\"type\":\"chat\", \"msg\":\"" + msg + "\"}");
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
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        System.out.println("Connection closed. Session " + session.getId());

        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
        
        String gn = (String) session.getAttributes().get("snakeGame");
        
        sessions.remove(session.getId());
        removeLobby(session);
        connectedPlayers.remove(s.getName());

        if ((gn != null) && SnakeGames.containsKey(gn)) { //Si estaba en una partida...
            SnakeGame snGm = SnakeGames.get(gn);
            
            if (snGm != null) {
	            synchronized (snGm) {
	                snGm.removeSnake(s);
	                exitGame(session, snGm, gn, s);
	            }
            }
        }
    }
    
    public void requestRoomData(WebSocketSession session, String rum) throws IOException {
        SnakeGame g = SnakeGames.get(rum);
        StringBuilder sb = new StringBuilder();
        for (Snake sn : g.getSnakes()) {
            sb.append(sn.getName());
            sb.append(", ");
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
        }

        String dif;
        if (g.difficulty == 1) {
            dif = "Easy";
        } else if (g.difficulty == 2) {
            dif = "Normal";
        } else {
            dif = "Hard";
        }

        String mode = "";
        if (g.gameMode == 1) {
            mode = "Max number of fruits";
        } else if (g.gameMode == 2) {
            mode = "Max length of snakes";
        }
        
        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
        try {
			s.sendMessage(String.format("{\"type\":\"joinConfirmed\", \"number\":" + g.getSnakes().size() + ", \"room\":\"" + rum + "\", \"difficulty\":\"" + dif + "\", \"gameMode\":\"" + mode + "\",\"players\":\"" + sb + "\"}"));
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public void joinGameConfirmed(WebSocketSession session, String gn3, SnakeGame snGm, Snake s) throws Exception {
        removeLobby(session);

        session.getAttributes().put("snakeGame", gn3);
        snGm.addSnake(s);
        StringBuilder sb = new StringBuilder();
        
        for (Snake snake : snGm.getSnakes()) {
            sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
            sb.append(',');
        }
        
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        
        String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());

        snGm.broadcast(msg);

        if (snGm.getSnakes().size() == 4 && !snGm.empezada()) {
            startGame(snGm);
        }
        
        snGm.getFoods(s);
    }

    public void startGame(SnakeGame snGm) throws Exception {
        snGm.startTimer();

        int[] comida = snGm.newFood();
        snGm.broadcast("{\"type\": \"hideStartButton\"}");
        snGm.broadcast("{\"type\":\"updateFood\", \"id\":" + 0 + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
    }

    public void exitGame(WebSocketSession session, SnakeGame snGm, String gn, Snake s) throws Exception {
        if (session.getId().equals(snGm.getAdmin())) {
            snGm.broadcast(String.format("{\"type\": \"kicked\"}"));
            
            for (Snake snake : snGm.getSnakes()) {
                addLobby(snake.getSession());
                snGm.removeSnake(snake);
            }
            
            for (WebSocketSession participant : sessions.values()) {
            	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                spart.sendMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}");
            }
            SnakeGames.remove(gn);

        } else if (snGm.empezada() && snGm.getSnakes().size() == 1) {
            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
            
            snGm.broadcast(msg);
            snGm.stopTimer();
            snGm.broadcast(String.format("{\"type\": \"endGame\" }"));
            
            for (WebSocketSession participant : sessions.values()) {
            	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                spart.sendMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}");
            }
            SnakeGames.remove(gn);
            
        } else { //No ha sido el admin y la partida sigue. solo se va uno.
            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
            snGm.broadcast(msg);
        }
    }

    public void addLobby(WebSocketSession session) {
        lobbyPlayers.add(session);
        refreshLobby();
    }

    public void removeLobby(WebSocketSession session) {
        synchronized (lobbyPlayers) {
            if (lobbyPlayers.contains(session)) {
                lobbyPlayers.remove(session);
            }
        }
        refreshLobby();
    }

    public void refreshLobby() {
        try {
        	synchronized(lobbyPlayers) {
	            StringBuilder sb = new StringBuilder();
	
	            for (WebSocketSession participant : lobbyPlayers) {
	                Snake s = (Snake) participant.getAttributes().get(SNAKE_ATT);
	
	                sb.append('"');
	                sb.append(s.getName());
	                sb.append('"');
	                sb.append(',');
	            }
	            
	            if (sb.length() > 0) {
	                sb.deleteCharAt(sb.length() - 1);
	            }
	            String msg = String.format("{\"type\": \"updateChatList\", \"names\" :[ " + sb + " ]}");
	
	            for (WebSocketSession participant : lobbyPlayers) {
	            	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
	                spart.sendMessage(msg);
	            }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
			e.printStackTrace();
		}
    }

    public StringBuilder recordsToJSON() {
    	lockRecords.lock();
        StringBuilder sb = new StringBuilder();

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
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]");
        }
            
    	lockRecords.unlock();
        
        return sb;
    }

    public StringBuilder writeFile() {
    	lockRecords.lock();
    	
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
        
    	lockRecords.unlock();
        
        return recs;
    }

    public void readFile() {
    	lockRecords.lock();
    	
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
        
            JSONArray jsonarray = new JSONArray(aBuffer);
            for (int i = 0; i < jsonarray.length(); i++) {
                JSONArray jug = jsonarray.getJSONArray(i);
                String name = jug.getString(0);
                int pun = Integer.parseInt(jug.getString(1));

                playerScores.put(name, pun);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    	lockRecords.unlock();
    }
}
