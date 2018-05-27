package es.codeurjc.em.snake;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

public class WebSocketClient {

	public interface MessageHandler {
		void onMessage(Session session, String msg);
	}

	public interface CloseHandler {
		void onClose(Session session, CloseReason closeReason);
	}

	public interface OpenHandler {
		void onOpen(Session session);
	}

	@ClientEndpoint
	public class InternalClient {
		
		@OnOpen
		public void onOpen(Session session) {
			openHandler.onOpen(session);
		}

		@OnClose
		public void onClose(Session session, CloseReason closeReason) {
			closeHandler.onClose(session, closeReason);
		}

		@OnMessage
		public void onMessage(Session session, String msg) {
			messageHandler.onMessage(session, msg);
		}
	}

	public boolean join = false;
	public boolean tryingJoin = false;
	public boolean hideStartButton = false;
	public boolean updateFood = false;
	public boolean update = false;
	public boolean left = false;
	public boolean created = false;
	
	public List<Integer> records = Collections.synchronizedList(new ArrayList<>());
	
	private Session session;
	private OpenHandler openHandler = s -> {};
	private CloseHandler closeHandler = (s, r) -> {};
	private MessageHandler messageHandler = (s, m) -> {};

	public void connect(String sServer) throws DeploymentException, IOException, URISyntaxException {
		session = ContainerProvider.getWebSocketContainer().connectToServer(new InternalClient(), new URI(sServer));
	}

	public synchronized void sendMessage(String sMsg) throws IOException {
		session.getBasicRemote().sendText(sMsg);
	}

	public void onOpen(OpenHandler openHander) {
		this.openHandler = openHander;
	}

	public void onMessage(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public void onClose(CloseHandler closeHandler) {
		this.closeHandler = closeHandler;
	}

	public void disconnect() throws IOException {
		session.close();
	}

	public Session getSession() {
		return session;
	}
}