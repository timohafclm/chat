package com.company;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    /**
     * Отправка сообщения всем участникам чата.
     *
     * @param message отправляемое сообщение
     */
    public static void sendBroadcastMessage(Message message) {
        for (Connection connection : connectionMap.values()) {
            try {
                connection.send(message);
            } catch (IOException e) {
                System.out.println("Message failure");
            }
        }
    }

    /**
     * Реализовывает протокол общения сервера с клиентом.
     */
    private static class Handler extends Thread {
        private Socket socket;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String userName = "";
            ConsoleHelper.writeMessage("Connection to " + socket.getRemoteSocketAddress());
            try (Connection connection = new Connection(socket)) {
                userName = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, userName));
                sendListOfUsers(connection, userName);
                serverMainLoop(connection, userName);
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Remote call error");
            } catch (ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Remote call error");
            } finally {
                if (!userName.isEmpty()) {
                    connectionMap.remove(userName);
                    sendBroadcastMessage(new Message(MessageType.USER_REMOVED, userName));
                    ConsoleHelper.writeMessage("Connected closed");
                }
            }
        }

        /**
         * Добавление нового участника чата: запрашивает имя пользователя, после проверки имени добавляет нового участника
         * в чат, отправляет новому пользователю подтверждение. Имя не должно совпадать с именами текущих участников чата.
         *
         * @param connection объект-коннектор, для общения с новым пользователем
         * @return имя добавленного участника чата
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {

            while (true) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message response = connection.receive();
                String userName;
                if (response.getType() == MessageType.USER_NAME) userName = response.getData();
                else continue;
                if (!userName.trim().isEmpty() && !connectionMap.containsKey(userName))
                    connectionMap.put(userName, connection);
                else continue;
                connection.send(new Message(MessageType.NAME_ACCEPTED));
                return userName;
            }
        }

        /**
         * Отправка новому участнику имена остальных участников чата
         *
         * @param connection объект-коннектор, для общения с новым пользователем
         * @param userName   имя нового участника
         * @throws IOException
         */
        private void sendListOfUsers(Connection connection, String userName) throws IOException {
            for (Map.Entry<String, Connection> entry : connectionMap.entrySet()) {
                if (!entry.getKey().equals(userName)) {
                    connection.send(new Message(MessageType.USER_ADDED, entry.getKey()));
                }
            }
        }

        /**
         * Главный цикл обработки сообщений сервером. Имя отправившего добавляется к тексту сообщения.
         *
         * @param connection объект-коннектор, считывающий входящее сообщение
         * @param userName   имя пользователя, отправившего сообщение
         * @throws IOException
         * @throws ClassNotFoundException
         */
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            while (true) {
                Message message = connection.receive();
                if (message.getType() == MessageType.TEXT)
                    sendBroadcastMessage(new Message(MessageType.TEXT, userName + ": " + message.getData()));
                else ConsoleHelper.writeMessage("Error message");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Enter port number");
        int port = ConsoleHelper.readInt();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is running");
            while (true) {
                new Handler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            System.out.println(e.getCause());
        }
    }
}
