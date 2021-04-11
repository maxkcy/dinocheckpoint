package com.max.myfirstmpdemo.headless;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxBuild;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Queue;
import com.github.czyzby.websocket.serialization.impl.ManualSerializer;
import com.max.myfirstmpdemo.PacketsSerializer;


import java.io.Console;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;

public class ServerMain extends Game {
    public Vertx vertx;
    public ManualSerializer manualSerializer;
    public HttpServer httpServer;
    public Array<ServerWebSocket> clientWSList; // = new Array<>();
    public Queue<ServerWebSocket> waitingForGameQueue;// = new Queue<>();
    public HandleFrame handleFrame;// = new HandleFrame(this);
    public Array<GameRoom> gameRoomArray;// = new Array<>();
    public Scanner scanner;

    @Override
    public void create() {
        vertx = Vertx.vertx();
        manualSerializer = new ManualSerializer();
        PacketsSerializer.register(manualSerializer);
        clientWSList = new Array<>();
        waitingForGameQueue = new Queue<>();
        handleFrame = new HandleFrame(this);
        gameRoomArray = new Array<>();
        this.launch();
        scanner = new Scanner(System.in);
    }


    @Override
    public void render() {

        if(waitingForGameQueue.size == 2){
            GameRoom gameRoom = new GameRoom(this);
            gameRoom.show();
            for (ServerWebSocket serverWebSocket:
                 waitingForGameQueue) {
                gameRoom.playersList.add(serverWebSocket);
            }
            System.out.println("The GameRoom has players: " + gameRoom.playersList);
            gameRoomArray.add(gameRoom);
            waitingForGameQueue.clear();
        }

        for(GameRoom gameRoom : gameRoomArray){
            gameRoom.render(Gdx.graphics.getDeltaTime());

            if(gameRoom.isActive == false){
                gameRoomArray.removeValue(gameRoom, true);
                gameRoom.dispose();
                System.out.println("Game room disposed");
                gameRoom = null;
            }
        }
        super.render();
    }


    @Override
    public void dispose() {
        super.dispose();
        httpServer.close();
    }
    boolean handled = false;
    private void launch(){
        httpServer = vertx.createHttpServer();
        System.out.println("Launching Server...");

        httpServer.webSocketHandler(new Handler<ServerWebSocket>(){
            @Override
            public void handle(ServerWebSocket client) {
                System.out.println("connection from (WS handler)"+ client.textHandlerID());
                clientWSList.add(client);

                client.frameHandler(new Handler<WebSocketFrame>(){
                    @Override
                    public void handle(WebSocketFrame event) {
                        handleFrame.handleFrame(client, event);
                    }
                });

                client.closeHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        System.out.println("client disconnected (WS handler)"+ client.textHandlerID());
                        handled = false;
                        waitingForGameQueue.forEach(serverWebSocket -> {
                            if(serverWebSocket == client){
                                waitingForGameQueue.removeValue(client, true);
                                System.out.println(client + "removed from queue");
                                handled = true;
                            }
                        });
                        if(handled == false){
                            for (GameRoom gameRoom : gameRoomArray) {
                                if (gameRoom.playersList.contains(client, true)) {
                                gameRoom.playersList.removeValue(client, true);
                                break;
                                }
                            }
                        }
                        handled = true;
                        clientWSList.removeValue(client, true);
                    }
                });


            }
        });
        httpServer.listen(8778);

        System.out.println("Server Started\n listening for new connections...");
    }
}
