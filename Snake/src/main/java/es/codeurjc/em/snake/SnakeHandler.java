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

    private Lock lockRecords = new ReentrantLock(); //Lock para manejar el mapa de récords
    private Lock lockSnakeGames = new ReentrantLock(); //Lock para manejar el mapa de partidas
    private Lock lockConnectedPlayers = new ReentrantLock(); //Lock para conectar la lista de jugadores en línea
    
    private AtomicInteger snakeIds = new AtomicInteger(0); //Determina la Id de la próxima snake que se una
    private AtomicBoolean loadedRecords = new AtomicBoolean(false); //Verifica que se hayan cargado los records del fichero
    
    private List<WebSocketSession> lobbyPlayers = Collections.synchronizedList(new ArrayList<>()); //Lista con los jugadores que no están en ninguna partida
    private Set<String> connectedPlayers = Collections.synchronizedSet(new HashSet<>()); //Lista de los jugadores que están en línea
    private ConcurrentHashMap<String, Integer> playerScores = new ConcurrentHashMap<>(); //Mapa de puntuaciones de los jugadores
    private ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>(); //Mapa de sesiones conectadas al servidor
    private ConcurrentHashMap<String, SnakeGame> SnakeGames = new ConcurrentHashMap<>(); //Mapa de partidas existentes

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

    	System.out.println("Welcome Session: " + session.getId());
    	
        if (!loadedRecords.get()) //Se cargan los récords almacenados en el fichero una sola vez
        {
            readFile();
        	loadedRecords.set(true);
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

            s.sendMessage("{\"type\":\"roomsCreated\", \"rooms\":" + mapeado + "}"); //Se envía la lista de salas existentes al jugador que se conecta
        }
        else
        	lockSnakeGames.unlock();
        
        s.sendMessage("{\"type\":\"updateRecords\", \"records\":" + recordsToJSON() + "}"); //Se envía la lista de récords al jugador que se conecta
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
                case "Name": { //Recibe el nombre de jugador introducido
                    String n = node.get("value").asText();

                    lockConnectedPlayers.lock();
                    if (connectedPlayers.contains(n)) { //Solo acepta el nombre si no existe ya un jugador en línea con el mismo
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
                    
                case "Dir": { //Recibe direcciones
                    Direction d = Direction.valueOf(node.get("value").asText().toUpperCase());
                    s.setDirection(d);
                }
                break;

                case "GameName": { //Recibe el nombre de sala introducido al intentar crearla
                    String gn1 = node.get("value").asText();

                    if (SnakeGames.containsKey(gn1)) { //Solo acepta el nombre si no existe a la vez una sala con el mismo
                        s.sendMessage("{\"type\":\"gameNameNotValid\"}");
                    } else {
                        s.sendMessage("{\"type\":\"newRoomSettings\"}");
                    }
                }
                break;

                case "createGame": { //Recibe la configuración para crear una nueva sala
                    String gn2 = node.get("value").asText();

                    SnakeGames.put(gn2, new SnakeGame(node.get("dif").asInt(), session.getId(), node.get("gameMode").asInt()));

                    for (WebSocketSession participant : sessions.values()) {
                    	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                        spart.sendMessage("{\"type\":\"newRoom\", \"name\":\"" + gn2 + "\"}");
                    }
                    s.sendMessage("{\"type\":\"newRoomCreator\", \"name\":\"" + gn2 + "\"}");
                }
                break;

                case "JoinGame": { //Recibe un intento de un usuario de unirse a una sala en concreto
                    String gn3 = node.get("value").asText();
                    SnakeGame snGm = SnakeGames.get(gn3);

                    removeLobby(session);

                    if (snGm != null) {
	                    synchronized (snGm) {
	                        if (snGm.getSnakes().size() < 4) { //Permite al jugador unirse solo si aún no hay 4 jugadores
	                            joinGameConfirmed(session, gn3, snGm, s);
	                        } else {
	                            s.sendMessage("{\"type\":\"gameFull\", \"idRoom\":\"" + gn3 + "\"}");
	                        }
	                    }
                    }
                }
                break;

                case "tryToJoin": { //Recibe un intento de un usuario de unirse a una sala que está llena
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

                case "LeaveGame": { //Se recibe de un usuario que ha abandonado la partida
                    String gn = (String) session.getAttributes().get("snakeGame");
                    SnakeGame snGm = SnakeGames.get(gn);
                    
                    if (snGm != null) {
	                    synchronized (snGm) {
	
	                        if (session.getId().equals(snGm.getAdmin())) { //Se envía un mensaje especial si el usuario era el administrador de la partida
	                            s.sendMessage(String.format("{\"type\": \"kicked\"}"));
	                            snGm.removeSnake(s);
	                        } else if (snGm.empezada() && snGm.getSnakes().size() == 2) { //Si esta acción deja a otro usuario solo en la sala, este también será expulsado
	                            addLobby(session);
	                            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
	                            s.sendMessage(msg);
	                            s.sendMessage("{\"type\": \"endGame\" }");
	                            snGm.removeSnake(s);
	                            SnakeGames.remove(gn);
	                        } else { //Si no se da ninguno de los dos casos, continúa la partida con normalidad
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

                case "startGame": { //Recibe un intento de comenzar una partida
                    SnakeGame snGm = SnakeGames.get((String) session.getAttributes().get("snakeGame"));
                    if (snGm.getSnakes().size() > 1) { //Si hay al menos dos jugadores, la partida arranca
                        startGame(snGm);
                    } else {
                        s.sendMessage(String.format("{\"type\": \"notEnoughPlayers\"}"));
                    }
                }
                break;

                case "deleteRoomRequest": { //Recibe un mensaje para actualizar las puntuaciones tras el fin de una partida
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

                case "matchMaking": { //Recibe el mensaje de un usuario que ha pulsado el botón de matchmaking
                	int aux = 0;
                    String auxK = "";
                	
                    lockSnakeGames.lock();
                    //Se realiza una búsqueda dentro del mapa de partidas que devuelva la partida óptima (aquella con mayor número de jugadores menor que 4):
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
                        s.sendMessage(String.format("{\"type\":\"matchMakingError\"}")); //Si no se ha encontrado ninguna partida, se devuelve un error
                    }
                }
                break;
                    
                case "requestRoomData": { //Recibe un mensaje de un usuario que quiere entrar a una sala existente
                    requestRoomData(session, node.get("value").asText()); //Envía al usuario los settings de la sala
                }
                break;

                case "chat":{ //Recibe un mensaje de un usuario que ha escrito en el chat
                    String msg = "<b>" + s.getName() + ":</b> " + node.get("message").asText();

                    for (WebSocketSession participant : lobbyPlayers) {
                    	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                        spart.sendMessage("{\"type\":\"chat\", \"msg\":\"" + msg + "\"}"); //Reenvía el mensaje para escribirlo en la caja de chat de todos los usuarios que no se encuentran en ninguna partida
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

        if ((gn != null) && SnakeGames.containsKey(gn)) { //Si el usuario estaba en una partida...
            SnakeGame snGm = SnakeGames.get(gn);
            
            if (snGm != null) {
	            synchronized (snGm) {
	                snGm.removeSnake(s);
	                exitGame(session, snGm, gn, s); //Se notifica al resto de jugadores en la sala de que el usuario ha salido
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
        	//Se envía al usuario la configuración de la sala para mostrársela:
			s.sendMessage(String.format("{\"type\":\"joinConfirmed\", \"number\":" + g.getSnakes().size() + ", \"room\":\"" + rum + "\", \"difficulty\":\"" + dif + "\", \"gameMode\":\"" + mode + "\",\"players\":\"" + sb + "\"}"));
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public void joinGameConfirmed(WebSocketSession session, String gn3, SnakeGame snGm, Snake s) throws Exception { //Añade un jugador a una partida tras unirse exitosamente
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

        if (snGm.getSnakes().size() == 4 && !snGm.empezada()) { //Si se trata del cuarto jugador, la partida arranca automáticamente
            startGame(snGm);
        }
        
        snGm.getFoods(s);
    }

    public void startGame(SnakeGame snGm) throws Exception {
        snGm.startTimer();
        
        //Cuando la partida arranca, se oculta el botón de empezar de la pantalla del administrador y se muestran las comidas actualizadas en el mapa:
        int[] comida = snGm.newFood();
        snGm.broadcast("{\"type\": \"hideStartButton\"}");
        snGm.broadcast("{\"type\":\"updateFood\", \"id\":" + 0 + ", \"tru\" : true, \"pos\" : [" + comida[0] + "," + comida[1] + "]}");
    }

    public void exitGame(WebSocketSession session, SnakeGame snGm, String gn, Snake s) throws Exception {
        if (session.getId().equals(snGm.getAdmin())) { //Si el usuario que ha salido era el administrador de la sala, se expulsa a los demás
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

        } else if (snGm.empezada() && snGm.getSnakes().size() == 1) { //Si esta acción deja a otro usuario solo en la sala, este también será expulsado
            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
            
            snGm.broadcast(msg);
            snGm.stopTimer();
            snGm.broadcast(String.format("{\"type\": \"endGame\" }"));
            
            for (WebSocketSession participant : sessions.values()) {
            	Snake spart = (Snake) participant.getAttributes().get(SNAKE_ATT);
                spart.sendMessage("{\"type\":\"deleteRoom\", \"id\":\"" + gn + "\"}");
            }
            SnakeGames.remove(gn);
            
        } else {  //Si no se da ninguno de los dos casos, continúa la partida con normalidad
            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());
            snGm.broadcast(msg);
        }
    }

    public void addLobby(WebSocketSession session) { //Añade a un jugador a la lista de jugadores que no están en ninguna partida
        lobbyPlayers.add(session);
        refreshLobby();
    }

    public void removeLobby(WebSocketSession session) { //Elimina a un jugador de la lista de jugadores que no están en ninguna partida
        synchronized (lobbyPlayers) {
            if (lobbyPlayers.contains(session)) {
                lobbyPlayers.remove(session);
            }
        }
        refreshLobby();
    }

    public void refreshLobby() { //Actualiza la lista de jugadores que no están en ninguna partida que se muestra en el lobby
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

    public StringBuilder recordsToJSON() { //Parsea la lista de puntuaciones a JSON para almacenarlos en un fichero
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

    public StringBuilder writeFile() { //Escribe en un fichero la lista de puntuaciones
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

    public void readFile() { //Lee un fichero JSON y rellena la lista de puntuaciones a partir de él
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
