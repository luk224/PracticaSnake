package es.codeurjc.em.snake;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnakeTest {

	@BeforeClass
	public static void startServer(){
		Application.main(new String[]{ "--server.port=9000" });
	}
		
	@Test
	public void testConnection() throws Exception {
		
		WebSocketClient wsc = new WebSocketClient();
		wsc.connect("ws://127.0.0.1:9000/snake");
        wsc.disconnect();		
	}
	
	/*
	@Test
	public void testJoin() throws Exception {
		
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		
		WebSocketClient wsc = new WebSocketClient();
		wsc.onMessage((session, msg) -> {
			System.out.println("TestMessage: " + msg);
			firstMsg.compareAndSet(null, msg);
		});
		
        wsc.connect("ws://127.0.0.1:9000/snake");
        
        System.out.println("Connected");
		
        Thread.sleep(1000);
        
        String msg = firstMsg.get();
        
		assertTrue("The fist message should contain 'join', but it is " + msg, msg.contains("join"));
		
        wsc.disconnect();		
	}
	 */

	@Test
	public void testAutoStartFourPlayers() throws Exception { //Test que evalúa que la partida comience automáticamente cuando haya 4 jugadores en la sala
		
		AtomicReference<Boolean> failedUpdateFood1 = new AtomicReference<Boolean>(true); //Define si se ha hecho una actualización de las comidas correctamente
		AtomicReference<Boolean> failedUpdate1 = new AtomicReference<Boolean>(true); //Define si se ha recibido actualización de la partida correctamente
		AtomicReference<Boolean> failedStartGame1 = new AtomicReference<Boolean>(true); //Define si se ha arrancado la partida correctamente
		AtomicReference<Boolean> failedHideButton1 = new AtomicReference<Boolean>(true); //Define si se ha escondido el botón de comenzar partida del administrador correctamente
		AtomicReference<Boolean> failedRoomAdmin1 = new AtomicReference<Boolean>(true); //Define si se ha enviado un mensaje especial de administrador a otro usuario

		AtomicReference<Boolean> startedGame1 = new AtomicReference<Boolean>(false); //Define si se ha comenzado la partida
		
		AtomicReference<Integer> n1 = new AtomicReference<Integer>(0); //Contador
		
		WebSocketClient players[] = new WebSocketClient[4];
		
		for (int i = 0; i < 4; i++)
		{
			WebSocketClient w = new WebSocketClient();
			
			w.onMessage((session, msg) -> {
				//println("TestMessage: " + msg);
				
				if (n1.get() == 4 && !startedGame1.get()) { //Cuando se han unido cuatro jugadores y la partida aún no ha comenzado
					failedStartGame1.set(false); //La partida comienza
					startedGame1.set(true);
					
					try {
						players[0].disconnect(); //Sale el administrador para que termine la partida
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				if (msg.contains("\"newRoom\"")) //Cuando se haya creado una sala, los usuarios entran automáticamente
				{
					try {
						w.sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala1\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if (msg.contains("\"newRoomCreator\""))
				{
					failedRoomAdmin1.set(!session.getId().equals(players[0].getSession().getId())); //Se comprueba el mensaje del administrador
				}
				else if (msg.contains("\"join\"") && (msg.length() - msg.replaceAll("id","").length())/2 == 4)
				{
					w.join = true;
				}
				else if (msg.contains("\"hideStartButton\"") && !w.hideStartButton)
				{
					failedHideButton1.set(!w.join);
					w.hideStartButton = true;
				}
				else if (msg.contains("\"updateFood\"") && !w.updateFood)
				{
					failedUpdateFood1.set(!w.hideStartButton);
					w.updateFood = true;
				}
				else if (msg.contains("\"update\"") && !w.update) {
					failedUpdate1.set(!w.updateFood);
					w.update = true;
					
					int aux = n1.get() + 1;
					n1.set(aux);
				}
			});
			
			w.connect("ws://127.0.0.1:9000/snake");
			players[i] = w;
		}
		
		players[0].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"Sala1\", \"dif\" : 1, \"gameMode\" : 1}");

        sleep(1000);
        
        assertTrue("The message should be received by the admin, but it is being received by another player", !failedRoomAdmin1.get());
        assertTrue("First message should be hideStartButton", !failedHideButton1.get());
        assertTrue("First message should be UpdateFood", !failedUpdateFood1.get());
        assertTrue("First message should be Update", !failedUpdate1.get());
        assertTrue("Game should have started (not all players did an update)", !failedStartGame1.get());
	}
		
	@Test
	public void testManualStart() throws Exception { //Test que evalúa que el administrador pueda empezar la partida antes de ser 4 siempre que haya dos o más
		
		AtomicReference<Boolean> failedUpdateFood0 = new AtomicReference<Boolean>(true);
		AtomicReference<Boolean> failedUpdate0 = new AtomicReference<Boolean>(true);
		AtomicReference<Boolean> failedStartGame0 = new AtomicReference<Boolean>(true);
		AtomicReference<Boolean> failedHideButton0 = new AtomicReference<Boolean>(true);
		AtomicReference<Boolean> failedRoomAdmin0 = new AtomicReference<Boolean>(true);

		AtomicReference<Boolean> startedGame0 = new AtomicReference<Boolean>(false);
		
		AtomicReference<Integer> n0 = new AtomicReference<Integer>(0);
		
		int nPlayers = 2;
		
		WebSocketClient players[] = new WebSocketClient[nPlayers];
		
		for (int i = 0; i < nPlayers; i++)
		{
			WebSocketClient w = new WebSocketClient();
			
			w.onMessage((session, msg) -> {
				//println("TestMessage: " + msg);
				
				if (n0.get() == 2 && !startedGame0.get()) { //Cuando hay dos jugadores y aún no ha empezado la partida, se arranca
					failedStartGame0.set(false);
					startedGame0.set(true);
					
					try {
						players[0].disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				if (msg.contains("\"newRoomCreator\""))
				{
					failedRoomAdmin0.set(!session.getId().equals(players[0].getSession().getId()));
					try {
						w.sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala2\"}");
					} 
					catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if (msg.contains("\"join\"") && !w.join)
				{
					w.join = true;
				}
				else if (msg.contains("\"hideStartButton\"") && !w.hideStartButton)
				{
					failedHideButton0.set(!w.join);
					w.hideStartButton = true;
				}
				else if (msg.contains("\"updateFood\"") && !w.updateFood)
				{
					failedUpdateFood0.set(!w.hideStartButton);
					w.updateFood = true;
				}
				else if (msg.contains("\"update\"") && !w.update) {
					failedUpdate0.set(!w.updateFood);
					w.update = true;
					
					int aux = n0.get() + 1;
					n0.set(aux);
				}
				else if (msg.contains("\"notEnoughPlayers\"")) //Si solo hay un jugador, la partida aún no puede comenzar
				{
					failedStartGame0.set(true);
				}
			});
			
			players[i] = w;
		}
		
		players[0].connect("ws://127.0.0.1:9000/snake");
		players[0].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"Sala2\", \"dif\" : 1, \"gameMode\" : 1}");

        sleep(1000);
        
        assertTrue("The message should be received by the admin, but it is being received by another player", !failedRoomAdmin0.get());
        
        players[0].sendMessage("{\"op\" : \"startGame\"}"); //Se intenta empezar la partida cuando solo hay un jugador en la sala
        
        sleep(1000);
        
        assertTrue("Game shouldn't have started yet (not enough players)", failedStartGame0.get()); //La partida no debería comenzar
        
        players[1].connect("ws://127.0.0.1:9000/snake");
        players[1].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala2\"}"); //Se une otro jugador a la sala
        
        Thread.sleep(1000);
        
        assertTrue("Game shouldn't have started yet (Start Button not pressed)", failedStartGame0.get());
        
        players[0].sendMessage("{\"op\" : \"startGame\"}"); //Se vuelve a intentar empezar la partida, ahora que hay dos
        
        Thread.sleep(1000);
        
        assertTrue("First message should be hideStartButton", !failedHideButton0.get());
        assertTrue("First message should be UpdateFood", !failedUpdateFood0.get());
        assertTrue("First message should be Update", !failedUpdate0.get());
        assertTrue("Game should have started (not all players did an update)", !failedStartGame0.get()); //La partida debería haber comenzado correctamente
	}
	
	@Test
	public void testWaitJoin() throws Exception { //Test que evalúa que cuando una sala está llena y un usuario se intenta unir, se quede a la espera intentándolo
		
		AtomicReference<Boolean> failedRoomAdmin2 = new AtomicReference<Boolean>(true);
		
		int nPlayers = 5;
		
		WebSocketClient players[] = new WebSocketClient[nPlayers];
		
		for (int i = 0; i < nPlayers; i++)
		{
			WebSocketClient w = new WebSocketClient();
			
			w.onMessage((session, msg) -> {
				//println("TestMessage: " + msg);
				
				if (msg.contains("\"newRoom\""))
				{					
					try {
						w.sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala3\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if (msg.contains("\"newRoomCreator\""))
				{
					failedRoomAdmin2.set(!session.getId().equals(players[0].getSession().getId()));
				}
				else if (msg.contains("\"join\"") && !w.join)
				{
					w.join = true;
				}
				else if (msg.contains("\"gameFull\"")) //Si la sala está llena, se programa una tarea que trata de conectarse periódicamente
				{
					ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			        scheduler.scheduleAtFixedRate(() -> {
			        	if (!w.join) {
							try {
								println("Trying to join");
								w.sendMessage("{\"op\" : \"tryToJoin\" , \"value\" : \"Sala3\"}");
							} 
			        		catch (IOException e) {
								e.printStackTrace();
							}
			        	}
			        	else {
			        		scheduler.shutdown();
			        		try {
								players[0].disconnect();
							} catch (IOException e) {
								e.printStackTrace();
							}
			        	}
			        }, 500, 500, TimeUnit.MILLISECONDS);
				}
			});
			
			players[i] = w;
			
			if (i < 4)
				players[i].connect("ws://127.0.0.1:9000/snake");
		}
		
		players[0].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"Sala3\", \"dif\" : 1, \"gameMode\" : 1}");

        sleep(1000);
        
        assertTrue("The message should be received by the admin, but it is being received by another player", !failedRoomAdmin2.get());
       
        players[4].connect("ws://127.0.0.1:9000/snake");
        players[4].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala3\"}"); //El quinto usuario intenta conectarse a una sala llena
        
        sleep(1000);
        
        assertTrue("The player shouldn't have joined (Room is full)", !players[4].join); //El usuario no debería haber podido unirse

        players[1].sendMessage("{\"op\" : \"LeaveGame\"}"); //Un usuario (distinto del administrador) abandona la partida
        
        sleep(1000);
        
        assertTrue("The player should have joined", players[4].join); //El usuario debería haber logrado unirse
	}
    
    @Test
    public void testEndGameOnePlayer() throws Exception { //Test que evalúa que se termine una partida automáticamente cuando solo quede un jugador en la sala
		AtomicReference<Boolean> failedRoomAdmin3 = new AtomicReference<Boolean>(true);
		AtomicReference<Boolean> finishedGame3 = new AtomicReference<Boolean>(false); //Define si se ha terminado la partida
    	
    	int nPlayers = 4;
		
		WebSocketClient players[] = new WebSocketClient[nPlayers];
		
		for (int i = 0; i < nPlayers; i++)
		{
			WebSocketClient w = new WebSocketClient();
			
			w.onMessage((session, msg) -> {
				//println("TestMessage: " + msg);
				
				if (msg.contains("\"newRoom\""))
				{
					try {
						w.sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala4\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if (msg.contains("\"newRoomCreator\""))
				{
					failedRoomAdmin3.set(!session.getId().equals(players[0].getSession().getId()));
				}
				else if (msg.contains("\"endGame\"")) //Recibe un mensaje si ha terminado la partida
				{
					try {
						finishedGame3.set(true);
						players[0].disconnect();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			
			players[i] = w;
			players[i].connect("ws://127.0.0.1:9000/snake");
		}
		
		players[0].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"Sala4\", \"dif\" : 1, \"gameMode\" : 1}");

        sleep(1000);
        
        assertTrue("The message should be received by the admin, but it is being received by another player", !failedRoomAdmin3.get());

        players[1].sendMessage("{\"op\" : \"LeaveGame\"}");
        
        sleep(1000);
        
        assertTrue("The game should not have ended (more than 2 players remaining)", !finishedGame3.get());

        players[2].sendMessage("{\"op\" : \"LeaveGame\"}");
        
        sleep(1000);
        
        assertTrue("The game should not have ended (more than 2 players remaining)", !finishedGame3.get()); //Tras abandonar dos jugadores de cuatro, la partida aún no debería haber terminado

        players[3].sendMessage("{\"op\" : \"LeaveGame\"}");
        
        sleep(1000);
        
        assertTrue("The game should have ended (less than 2 players remaining)", finishedGame3.get()); //Tras abandonar la penúltima persona, la partida debería haber terminado
    }
    
	@Test
	public void testLoad() throws Exception { //Test de carga
		int nPlayers = 10;
		
		WebSocketClient players[] = new WebSocketClient[nPlayers];
		
		for (int i = 0; i < nPlayers; i++)
		{
			WebSocketClient w = new WebSocketClient();
			
			w.onMessage((session, msg) -> {
				//println("TestMessage: " + msg);
				
				if (msg.contains("\"newRoomCreator\""))
				{
					try {
						w.created = true;
						w.sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"Sala_" + session.getId() + "\"}");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else if (msg.contains("\"leave\"") || msg.contains("\"kicked\"") || msg.contains("\"endGame\"")) //Comprueba si el jugador ha abandonado la partida de cualquiera de las maneras posibles
				{
					w.left = true;
					w.created = false;
				}
				else if (msg.contains("\"join\"") && !w.join)
				{
					w.join = true;
					w.tryingJoin = false;
				}
				else if (msg.contains("\"gameFull\"")) //Si un usuario intenta acceder a una sala llena, se quedará intentándolo de nuevo
				{
					ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			        scheduler.scheduleAtFixedRate(() -> {
			        	if (!w.join) {
							try {
								ObjectMapper mapper = new ObjectMapper();
					            JsonNode node = mapper.readTree(msg);
					            String gn = node.get("value").asText();

								println("Trying to join");
								w.sendMessage("{\"op\" : \"tryToJoin\" , \"value\" : \"" + gn + "\"}");
								w.tryingJoin = true;
							} 
			        		catch (IOException e) {
								e.printStackTrace();
							}
			        	}
			        	else
			        		scheduler.shutdown();
			        }, 500, 500, TimeUnit.MILLISECONDS);
				}
				else if (msg.contains("\"updateRecords\"")) //Al recibir la lista de récords, se parsea y se añade a una lista local de cada cliente
				{
					Pattern p = Pattern.compile(",-?\\d+");
					Matcher m = p.matcher(msg);
					
					w.records = Collections.synchronizedList(new ArrayList<>());
					
					while (m.find()) {
						String g = m.group().replaceAll(",","");
						
						w.records.add(Integer.parseInt(g));
					}
				}
			});
			
			players[i] = w;
			players[i].connect("ws://127.0.0.1:9000/snake");
			players[i].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"Sala_" + players[i].getSession().getId() + "\", \"dif\" : 1, \"gameMode\" : 1}"); //Cada jugador crea una sala
		}
		
		sleep(1000);
		
		for (int i = 0; i < nPlayers; i++)
		{
			assertTrue("Room should have been created", players[i].created); //Se comprueba que se han creado las 10 salas
			assertTrue("Player " + i + " shouldn't have left the room yet", !players[i].left);
		}
			
		sleep(2000);
			
		for (int i = 0; i < nPlayers; i++)
		{
			players[i].sendMessage("{\"op\" : \"LeaveGame\"}"); //Tras dos segundos, todos los usuarios abandonan sus salas
		}

		sleep(1000);
        
        for (int i = 0; i < nPlayers; i++)
		{
        	assertTrue("Player " + i + " should have left the room", players[i].left); //Se comprueba que todos han salido
		}
                
        sleep(1000);

        
        //El primer y el sexto jugador crean las salas SalaA y SalaB:
        players[0].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"SalaA\", \"dif\" : 1, \"gameMode\" : 1}");
        players[5].sendMessage("{\"op\" : \"createGame\" , \"value\" : \"SalaB\", \"dif\" : 1, \"gameMode\" : 1}");
        
        sleep(1000);
        
        assertTrue("Players should have created rooms A and B", players[0].created && players[5].created); //Se comprueba que se han creado las salas correctamente
        
        sleep(1000);
        
        //Se unen o intentan unir los demás jugadores:
        players[1].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaA\"}");
        players[2].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaA\"}");
        players[3].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaA\"}");
        players[4].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaA\"}");
        
        players[6].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaB\"}");
        players[7].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaB\"}");
        players[8].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaB\"}");
        players[9].sendMessage("{\"op\" : \"JoinGame\" , \"value\" : \"SalaB\"}");
        
        sleep(1000);
        
        for (int i = 0; i < nPlayers; i++)
		{
        	assertTrue("Player " + i + " should have have joined or be trying to join", players[i].join || players[i].tryingJoin);
		}
        
        sleep(10000);
        
        for (int i = 0; i < nPlayers; i++)
		{
        	if (players[i].join)
        		players[i].sendMessage("{\"op\" : \"LeaveGame\"}"); //Tras diez segundos, todos los usuarios abandonan las salas
		}
        
        sleep(1000);
        
        for (int i = 0; i < nPlayers; i++)
		{
        	assertTrue("Player " + i + " should have left the room", players[i].left); //Se comprueba que todos han abandonado las salas correctamente
		}
        
        sleep(1000);
        
        
        //Se comprueba que todas las listas de récords de los clientes sean iguales:
        for (int i = 1; i < nPlayers; i++)
        {
        	if (!players[i-1].records.equals(players[i].records))
        		Assert.fail("Records are not equal");
        }
        
        sleep(1000);
        
        //Se comprueba que la lista de récords está ordenada de manera decreciente
        for (int i = 0; i < players[0].records.size()-1; i++)
        {
        	//println("" + players[0].records.get(i));
        	
        	if (!(players[0].records.get(i).compareTo(players[0].records.get(i + 1)) >= 0))
        		Assert.fail("Records are not ordered");
        }
	}	
	
	private static void println(String mensaje) 
	{
		System.out.println(Thread.currentThread().getName() + ": " + mensaje);
	}

	private static void sleep(long millis) 
	{
		try 
		{
			Thread.sleep(millis);
		} 
		catch (InterruptedException e) 
		{
			System.out.println(Thread.currentThread().getName() + ": interruptedMidSleepException");
		}
	}
}
