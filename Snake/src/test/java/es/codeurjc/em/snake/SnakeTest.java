package es.codeurjc.em.snake;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

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
	
	@Test
	public void testJoin() throws Exception {
		
		AtomicReference<String> firstMsg = new AtomicReference<String>();
		
		WebSocketClient wsc = new WebSocketClient();
		wsc.onMessage((session, msg) -> {
			System.out.println("TestMessage: "+msg);
			firstMsg.compareAndSet(null, msg);
		});
		
        wsc.connect("ws://127.0.0.1:9000/snake");
        
        System.out.println("Connected");
		
        Thread.sleep(1000);
        
        String msg = firstMsg.get();
        
		assertTrue("The fist message should contain 'join', but it is "+msg, msg.contains("join"));
		
        wsc.disconnect();		
	}

}
