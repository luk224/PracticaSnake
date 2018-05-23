var Console = {};

Console.log = (function(message) {
	var console = document.getElementById('console');
	var p = document.createElement('p');
	p.style.wordWrap = 'break-word';
	p.innerHTML = message;
	console.appendChild(p);
	while (console.childNodes.length > 25) {
		console.removeChild(console.firstChild);
	}
	console.scrollTop = console.scrollHeight;
});

let game;

class Snake {

	constructor() {
		this.snakeBody = [];
		this.color = null;
	}

	draw(context) {
		for (var pos of this.snakeBody) {
			context.fillStyle = this.color;
			context.fillRect(pos.x, pos.y,
				game.gridSize, game.gridSize);
		}
	}
}

var socket;

class Game {
	
	constructor(){
		this.fps = 30;
		//this.socket = null;
		this.nextFrame = null;
		this.interval = null;
		this.direction = 'none';
		this.gridSize = 10;
		
		this.skipTicks = 1000 / this.fps;
		this.nextGameTick = (new Date).getTime();
	}

	initialize() {	
	
		this.snakes = [];
		let canvas = document.getElementById('playground');
		if (!canvas.getContext) {
			Console.log('Error: 2d canvas not supported by this browser.');
			return;
		}
		
		this.context = canvas.getContext('2d');
		
		this.connect();
	}

	setDirection(direction) {
		this.direction = direction;
		socket.send(JSON.stringify({op : "Dir" , value : direction}));
		Console.log('Sent: Direction ' + direction);
	}

	startGameLoop() {
	
		this.nextFrame = () => {
			requestAnimationFrame(() => this.run());
		}
		
		this.nextFrame();		
	}

	stopGameLoop() {
		this.nextFrame = null;
		if (this.interval != null) {
			clearInterval(this.interval);
		}
	}

	draw() {
		this.context.clearRect(0, 0, 640, 480);
		for (var id in this.snakes) {			
			this.snakes[id].draw(this.context);
		}
	}

	addSnake(id, color) {
		this.snakes[id] = new Snake();
		this.snakes[id].color = color;
	}

	updateSnake(id, snakeBody) {
		if (this.snakes[id]) {
			this.snakes[id].snakeBody = snakeBody;
		}
	}

	removeSnake(id) {
		this.snakes[id] = null;
		// Force GC.
		delete this.snakes[id];
	}

	run() {
	
		while ((new Date).getTime() > this.nextGameTick) {
			this.nextGameTick += this.skipTicks;
		}
		this.draw();
		if (this.nextFrame != null) {
			this.nextFrame();
		}
	}

	connect() {
		
		socket = new WebSocket("ws://"+window.location.host+"/snake");

		socket.onopen = () => {
			
			// Socket open.. start the game loop.
			Console.log('Info: WebSocket connection opened.');
			Console.log('Info: Press an arrow key to begin.');
			
			this.startGameLoop();
			
			setInterval(() => socket.send('ping'), 5000);
			
			do {
				var name = prompt("Please enter a valid name:", "");			
			} while (name == "" || name == undefined || name == null);
			
			socket.send(JSON.stringify({op : "Name" , value : name}));
		}

		socket.onclose = () => {
			Console.log('Info: WebSocket closed.');
			this.stopGameLoop();
		}

		socket.onmessage = (message) => {

			var packet = JSON.parse(message.data);
			
			switch (packet.type) {
			case 'update':
				for (var i = 0; i < packet.data.length; i++) {
					this.updateSnake(packet.data[i].id, packet.data[i].body);
				}
				break;
			case 'join':
				for (var j = 0; j < packet.data.length; j++) {
					this.addSnake(packet.data[j].id, packet.data[j].color);
				}
				break;
			case 'leave':
				this.removeSnake(packet.id);
				break;
			case 'dead':
				Console.log('Info: Your snake is dead, bad luck!');
				this.direction = 'none';
				break;
			case 'kill':
				Console.log('Info: Head shot!');
				break;
			case 'gameNameValid':
				if(packet.data) {
					var btn = document.createElement("BUTTON");
					
				    var t = document.createTextNode(packet.name);
				    btn.appendChild(t);
				    btn.setAttribute("id", packet.name);
				    btn.setAttribute("type", "button");
				    
				    btn.addEventListener("click", () => this.joinGame(packet.name));
				    
				    document.getElementById("game-buttons").appendChild(btn);
				}
				else {
					alert("Error: Game name already exists.");
					this.newGame();
				}
				break;

			case 'roomsCreated':
				console.log(packet.data);
				var obj = JSON.parse(packet.data); 
				for (var i = 0; i < obj.length; i++) {
					var btn = document.createElement("BUTTON");
					
				    var t = document.createTextNode(obj[i]);
				    btn.appendChild(t);
				    btn.setAttribute("id", obj[i]);
				    btn.setAttribute("type", "button");
				    
				    btn.addEventListener("click", () => this.joinGame(obj[i]));
				    
				    document.getElementById("game-buttons").appendChild(btn);
				
				}
				break;
			}

		}
	}
	
	matchmaking() {
	}

	newGame() {
		do {
			var name = prompt("Please enter a valid game name:", "");	
			console.log(name);
		} while (name == "" || name == undefined);
		
		if(name != null)
			socket.send(JSON.stringify({op : "GameName" , value : name}));
	}

	

	enableKeys() {
			console.log("keysEnabled");
		window.addEventListener('keydown', e => {
			console.log("keysEnabled");
			var code = e.keyCode;
			if (code > 36 && code < 41) {
				switch (code) {
				case 37:
					if (this.direction != 'east')
						this.setDirection('west');
					break;
				case 38:
					if (this.direction != 'south')
						this.setDirection('north');
					break;
				case 39:
					if (this.direction != 'west')
						this.setDirection('east');
					break;
				case 40:
					if (this.direction != 'north')
						this.setDirection('south');
					break;
				}
			}
		}, false);
	}
	joinGame(n) { //cambiar la variablke name
		socket.send(JSON.stringify({op : "JoinGame" , value : n}));
		$("#playground").show();
		$("#console-container").show();
		$("#game-buttons").hide();
		this.enableKeys();

	}
}

$(document).ready(function() {
	$("#playground").hide();
	$("#console-container").hide();
	
	game = new Game();
    game.initialize();
    
    document.getElementById("matchMaking").addEventListener("click", () => game.matchmaking());
    document.getElementById("newGame").addEventListener("click", () => game.newGame());
});
