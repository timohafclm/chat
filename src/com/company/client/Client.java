package com.company.client;

import com.company.Connection;
import com.company.ConsoleHelper;
import com.company.Message;
import com.company.MessageType;

import java.io.IOException;
import java.net.Socket;

/**
 * Предоставляет поток для чтения сообщения и его отправки на сервер.
 */
public class Client extends Thread {
    protected Connection connection;
    private volatile boolean clientConnected = false;

    public static void main(String[] args) {
        new Client().run();
    }

    @Override
    public void run() {
        SocketThread socketThread = getSocketThread();
        socketThread.setDaemon(true);
        socketThread.start();
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                ConsoleHelper.writeMessage("Connection error");
                return;
            }
        }
        if (clientConnected) {
            ConsoleHelper.writeMessage("Connection established. ‘exit’ - exit command.");
            while (clientConnected) {
                String message = ConsoleHelper.readString();
                if (message.equals("exit")) return;
                if (shouldSendTextFromConsole()) sendTextMessage(message);
            }
        } else ConsoleHelper.writeMessage("Client error");
    }

    protected String getServerAddress() {
        ConsoleHelper.writeMessage("Enter server address");
        return ConsoleHelper.readString();
    }

    protected int getServerPort() {
        ConsoleHelper.writeMessage("Enter server port");
        return ConsoleHelper.readInt();
    }

    protected String getUserName() {
        ConsoleHelper.writeMessage("Enter your name");
        return ConsoleHelper.readString();
    }

    protected boolean shouldSendTextFromConsole() {
        return true;
    }

    protected SocketThread getSocketThread() {
        return new SocketThread();
    }

    protected void sendTextMessage(String text) {
        try {
            connection.send(new Message(MessageType.TEXT, text));
        } catch (IOException e) {
            clientConnected = false;
            ConsoleHelper.writeMessage("Connection refused");
        }
    }

    /**
     * Устанавливает сокетное соединение и читает сообщения от сервера.
     */
    public class SocketThread extends Thread {

        @Override
        public void run() {
            try {
                connection = new Connection(new Socket(getServerAddress(), getServerPort()));
                clientHandshake();
                clientMainLoop();
            } catch (IOException e) {
                notifyConnectionStatusChanged(false);
            } catch (ClassNotFoundException e) {
                notifyConnectionStatusChanged(false);
            }
        }

        protected void clientHandshake() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();
                if (message.getType() == MessageType.NAME_REQUEST)
                    connection.send(new Message(MessageType.USER_NAME, getUserName()));
                else if (message.getType() == MessageType.NAME_ACCEPTED) {
                    notifyConnectionStatusChanged(true);
                    return;
                } else throw new IOException("Unexpected MessageType");
            }
        }

        protected void clientMainLoop() throws IOException, ClassNotFoundException {
            while (!Thread.currentThread().isInterrupted()) {
                Message message = connection.receive();
                if (message.getType() == MessageType.TEXT) processIncomingMessage(message.getData());
                else if (message.getType() == MessageType.USER_ADDED) informAboutAddingNewUser(message.getData());
                else if (message.getType() == MessageType.USER_REMOVED) informAboutDeletingNewUser(message.getData());
                else throw new IOException("Unexpected MessageType");
            }
        }

        protected void processIncomingMessage(String message) {
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutAddingNewUser(String userName) {
            ConsoleHelper.writeMessage(userName + " joined");
        }

        protected void informAboutDeletingNewUser(String userName) {
            ConsoleHelper.writeMessage(userName + " leave");
        }

        protected void notifyConnectionStatusChanged(boolean clientConnection) {
            synchronized (Client.this) {
                Client.this.clientConnected = clientConnection;
                Client.this.notify();
            }
        }
    }
}
