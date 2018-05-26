let game;

var socket;
var admin;

var inGame = false;


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

var Chat = {};
Chat.log = (function(message) {
	var chat = document.getElementById('chat');
	var p = document.createElement('p');

	p.style.wordWrap = 'break-word';
	p.innerHTML = message;

	chat.appendChild(p);
	while (chat.childNodes.length > 25) {
		chat.removeChild(chat.firstChild);
	}

	chat.scrollTop = chat.scrollHeight;
});


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

class Food {

	constructor() {
		this.location = [];
		this.color = '#ff0000';
	}

	draw(context) {
		context.fillStyle = this.color;
		context.fillRect(this.location[0], this.location[1], game.gridSize, game.gridSize);
	}
}

class Game {
	
	constructor() {
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
		this.foods = [];

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
		//Console.log('Sent: Direction ' + direction);
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
	
		for(var bar in game.foods){
			if(game.foods[bar] != undefined)
				game.foods[bar].draw(this.context);
		}
	}

	addSnake(id, color) {
		this.snakes[id] = new Snake();
		this.snakes[id].color = color;
	}

	updateSnake(id, snakeBody) {
		if (this.snakes[id])
			this.snakes[id].snakeBody = snakeBody;
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
		
		socket = new WebSocket("ws://" + window.location.host + "/snake");

		socket.onopen = () => {
			
			//Socket open... start the game loop.
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
			//console.log(message.data);
			var packet = JSON.parse(message.data);
			
			switch (packet.type) {

				case 'update': {
					for (var i = 0; i < packet.data.length; i++) {
						this.updateSnake(packet.data[i].id, packet.data[i].body);
					}
				} break;

				case 'updateFood': {
					if(packet.tru) { //Si tru vale true, es una comida nueva, de lo contrario, es una comida a eliminar
						this.foods[packet.id] = new Food();
						this.foods[packet.id].location[0] = packet.pos[0];
						this.foods[packet.id].location[1] = packet.pos[1];
					}
					else {
						this.foods[packet.id] = undefined;
					}
				} break;

				case 'rewardFood':
					Console.log('Info: Yum yum!');
					break;

				case 'kill':
					Console.log('Info: Head shot!');
					break;
				
				case 'dead': {
					Console.log('Info: Your snake is dead, bad luck!');
					this.direction = 'none';
				} break;

				case 'userNameNotValid': {
					alert("Error: Username already in use.");

					do {
						var name = prompt("Please enter a valid name:", "");			
					} while (name == "" || name == undefined || name == null);
					
					socket.send(JSON.stringify({op : "Name" , value : name}));
				} break;
					
				case 'gameNameNotValid': {
					alert("Error: Game name already exists.");
					
					admin = "";
					this.newGame();
				} break;

				case 'newRoomCreator': {
					var btn = document.createElement("BUTTON");
				    var t = document.createTextNode("Start Game");

				    btn.appendChild(t);
				    btn.setAttribute("id", "startGame");
				    btn.setAttribute("type", "button");
				    btn.addEventListener("click", () => socket.send(JSON.stringify({op : "startGame"})));
					
					document.getElementById("room").appendChild(btn);

					socket.send(JSON.stringify({op : "JoinGame" , value : packet.name}));
				} break;
					
				case 'newRoom': {
					var packname = packet.name;

					var btn = document.createElement("BUTTON");
				    var t = document.createTextNode(packname);

				    btn.appendChild(t);
				    btn.setAttribute("id", packname);
				    btn.setAttribute("type", "button");
				    btn.addEventListener("click", event => this.joinGame(event.target.getAttribute("id")));

				    document.getElementById("lobby").appendChild(btn);
				} break;
				
				case 'newRoomSettings':{
					$("#room").hide();
					$("#lobby").hide();
					$("#waitJoin").hide();
					$("#settings").show();
				} break;

				case 'roomsCreated': {
					var obj = packet.rooms;
					
					for (var i = 0; i < obj.length; i++) {
						var pen = obj[i];

						var btn = document.createElement("BUTTON");
					    var t = document.createTextNode(pen);

					    btn.appendChild(t);
					    btn.setAttribute("id", pen);
					    btn.setAttribute("type", "button");
					    btn.addEventListener("click", event => this.joinGame(event.target.getAttribute("id")));
					    
					    document.getElementById("lobby").appendChild(btn);
					}
				} break;

				case 'gameFull': {
					$("#waitJoin").show();
					$("#cancelButton").show();

					document.getElementById("pWaitJoin").innerHTML = "Game is full, trying to connect...";
					
					var myVar = setInterval( ( () => {
						if(!inGame)
							socket.send(JSON.stringify({op : "tryToJoin" , value : packet.idRoom }));
					}), 500);

					var j = setTimeout(function() {
						clearInterval(myVar);

						if(!inGame)
							document.getElementById("pWaitJoin").innerHTML = "Impossible to join.";

						$("#cancelButton").hide();

						document.getElementById("cancelButton").removeEventListener("click", (myVar,j) => this.cancelWait(myVar,j));
					}, 5000);

					document.getElementById("cancelButton").addEventListener("click", (myVar,j) => this.cancelWait(myVar,j));
				} break;

				case 'matchMaking': {
					var room = packet.room;
					alert("Choosen room: " + room + ".");

					this.joinGame(room);
				} break;

				case 'matchMakingError':
					alert("There are no available rooms.") 
					break;

				case 'join': {
					for (var j = 0; j < packet.data.length; j++) {
						this.addSnake(packet.data[j].id, packet.data[j].color);
					}
					this.joinGameUI();
				} break;

				case 'joinConfirmed': {
					if(confirm("Do you want to join this room?\n\r • Number of players: " + packet.number 
					+ "\n\r • Player names: "+ packet.players + "\n\r • Difficulty: " + packet.difficulty + "\n\r • Game mode: " + packet.gameMode))
						socket.send(JSON.stringify({op : "JoinGame" , value : packet.room}));
		 		} break;

				case 'notEnoughPlayers':
					alert("You need to be at least 2 players to start a game.") 
					break;

				case 'hideStartButton': {
					if(!(admin == "" || admin == undefined || admin == null))
						$("#startGame").hide();
				} break;

				case 'leave':
					this.removeSnake(packet.id);
					break;

				case 'kicked': {
					inGame = false;

					if(!(admin == "" || admin == undefined || admin == null)) {
						admin = "";
						document.getElementById("startGame").remove();
					}
					else
						alert("The administrator has left the game.");

					this.foods = [];

					for (var id in this.snakes) {			
						this.snakes[id] = null;
						delete this.snakes[id];
					}
					
					this.setDirection('none');

					window.removeEventListener('keydown', event => this.keys(event), false);
					
					$("#room").hide();
					$("#lobby").show();
				} break;

				case 'deleteRoom': {
					var idRoom = packet.id;
					document.getElementById(idRoom).remove();
				} break;
				
				case 'endGame': {
					inGame = false;

					$("#room").hide();
					$("#lobby").show();

					this.foods = [];

					for (var id in this.snakes) 
					{			
						this.snakes[id] = null;
						delete this.snakes[id];
					}

					this.setDirection('none');

					if(!(admin == "" || admin == undefined || admin == null	))
						socket.send(JSON.stringify({op : "deleteRoomRequest"})); 
					
					alert("Game over");
				} break;

		 		case 'updateLegend': {
		 			var leyen = document.getElementById("legend");
		 			leyen.innerHTML ="";

		 			for(var i = 0; i < packet.names.length; i++) {
		 				leyen.innerHTML += '<p id="j_' + i + '"'+ ' > • ' + packet.names[i] + ': '+ packet.scores[i] + '\n </p>';

		 				document.getElementById("j_" + i).style.color = packet.colors[i];
		 				document.getElementById("j_" + i).style.textShadow = "1px 1px black";
		 			}
		 		} break;

		 		case 'updateRecords': {
		 			var r = document.getElementById("records");
		 			r.innerHTML = "";

		 			for(var i = 0; i<packet.records.length; i++) {
		 				r.innerHTML += '<p id="r_' +i + '"'+ ' > • ' + packet.records[i][0] + ': '+ packet.records[i][1] + '\n </p>';
		 			}
		 		} break;

				case 'updateChatList': {
					var l = document.getElementById("listChat");
		 			l.innerHTML = "";

		 			for(var i = 0; i<packet.names.length; i++) {
		 				l.innerHTML += '<p id="l_' +i + '"'+ ' > • ' + packet.names[i] + '\n </p>';
		 			}
				} break;

		 		case'chat':
					Chat.log(packet.msg);
					break;

				default:
					break;
			}
		}
	}

	newGame() {
		do {
			var name = prompt("Please enter a valid game name:", "");	
		} while (name == "");
		
		if(name != null && name != undefined) {
			admin = name;
			socket.send(JSON.stringify({op : "GameName" , value : name}));
		}
	}
		
	getGameSettings() {
	    var val, mode;

	    var radios = document.getElementById('setDifficulty').elements["radio"];
	    
	    for (var i = 0, len = radios.length; i < len; i++) {
	        if (radios[i].checked) { 
	            val = radios[i].value;
	            break;
	        }
	    }
	  
	  	radios = document.getElementById('setMode').elements["radio"];
	    
	    for (var i = 0, len = radios.length; i < len; i++) {
	        if (radios[i].checked) { 
	            mode = radios[i].value; 
	            break; 
	        }
	    }

		socket.send(JSON.stringify({op : "createGame" , value : admin, dif : val, gameMode : mode}));
	}

	cancelGameSettings() {
		$("#room").hide();
		$("#settings").hide();
		$("#waitJoin").hide();
		$("#lobby").show();
	}

	cancelWait (myVar, j) {
		clearInterval(myVar);
		clearTimeout(j);

		$("#waitJoin").hide();
	}

	joinGame(joinNameVal) {
		socket.send(JSON.stringify({op : "requestRoomData" , value : joinNameVal}));
	}

	joinGameUI() {
		inGame = true;

		$("#room").show();
		$("#lobby").hide();	
		$("#settings").hide();
		$("#waitJoin").hide();
		$("#listChat").hide();

		window.addEventListener('keydown', event => this.keys(event), false);
	}
	
	leaveGame() {
		inGame = false;

		this.foods = [];

		for (var id in this.snakes) {			
			this.snakes[id] = null;
			delete this.snakes[id];
		}
		
		this.setDirection('none');

		window.removeEventListener('keydown', event => this.keys(event), false);
		
		$("#room").hide();
		$("#lobby").show();

		socket.send(JSON.stringify({op : "LeaveGame"}));
	}

	keys(e) {
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
	}
	
	sendChat() {
		var msg = document.getElementById("mensajetext").value;

		if (msg != "") {
			document.getElementById("mensajetext").value = "";
			socket.send(JSON.stringify({op : "chat" , message : msg}));
		}
	}
}

$(document).ready(function() {
	$("#room").hide();
	$("#settings").hide();
	$("#listChat").hide();
	
	game = new Game();
    game.initialize();

    document.getElementById("matchMaking").addEventListener("click", () => socket.send(JSON.stringify({op : "matchMaking"})));
    document.getElementById("newGame").addEventListener("click", () => game.newGame());
    document.getElementById("leaveGame").addEventListener("click", () => game.leaveGame());

    document.getElementById("getValue").addEventListener("click", () => game.getGameSettings());
    document.getElementById("getValueCancel").addEventListener("click", () => game.cancelGameSettings());

    document.getElementById("chatButton").addEventListener("click", () =>game.sendChat());
    document.getElementById("listChatButton").addEventListener("click", () => $("#listChat").toggle());
});