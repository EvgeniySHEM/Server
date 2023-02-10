package ru.avalon.javapp.j130.server;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ClientSocket extends Thread {
    private final Socket socket;
    private final String login;
    private final String password;
    public static final String OK = "Ok";
    public static final String NO = "No";
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private String inputData;
    private static String command;
    private String userName;
    private String fileOrUserName;
    private boolean checkPrivate;
    private List<Path> list;
    private List<String> list1;


    public ClientSocket(Socket socket) throws IOException {
        this.socket = socket;
        inputStream = new ObjectInputStream(socket.getInputStream());
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        login = inputStream.readUTF();
        password = inputStream.readUTF();
        if (checkLoginAndPassword(login, password)) {
            outputStream.writeUTF(OK);
            outputStream.flush();
            System.out.println("OK");
        } else {
            outputStream.writeUTF(NO);
            outputStream.flush();
            System.out.println("NO");
            socket.close();
            throw new IllegalArgumentException("Incorrect login or password");
        }
    }

    private boolean checkLoginAndPassword(String login, String password) throws IOException {
        String check = login + " " + password;
        Path path = Paths.get("Logins and Passwords.txt");
        List<String> list = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String el : list) {
            if (el.equals(check)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run() {
        while (true) {
            try {
                /**
                 * Ожидание получения комманды
                 */
                do {
                    inputData = inputStream.readUTF();
                } while (inputData.trim().isEmpty());
                /**
                 * Обработка комманды
                 */
                if (inputData.contains("/")) {
                    parsing();
                    if (command.equals("get")) {
                        userName = list1.get(1);
                        fileOrUserName = list1.get(2);
                        sendTheUserFile();
                        continue;
                    }
                    System.out.println("You have entered incorrect data!");
                    continue;
                }
                if (inputData.contains(" ")) {
                    if (inputData.contains("*")) {
                        checkPrivate = true;
                    } else {
                        checkPrivate = false;
                    }
                    parsing();
                    fileOrUserName = list1.get(1);
                    try {
                        checkCommand();
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println(e.getMessage());
                    }
                    continue;
                }
                if (inputData.equals("list")) {
                    getMyFiles();
                    continue;
                }
                if (inputData.equals("users")) {
                    getUsers();
                }
            } catch (IOException e) {
            }
        }
    }

    private void parsing() {
        String[] array = inputData.split("[^A-Za-z0-9\\.]");
        list1 = Arrays.stream(array).filter(e -> !e.equals("")).toList();
        command = list1.get(0);
    }

    private void checkCommand() throws IOException, ClassNotFoundException {
        switch (command) {
            case "put" -> saveFile();
            case "get" -> sendFile();
            case "del" -> deleteFile();
            case "list" -> sendFilesOfUser();
            default -> System.out.println("You have entered incorrect data!");
        }
    }

    private boolean saveFile() throws IOException {
        Path path;
        if (checkPrivate) {
            path = Path.of("Storage" + File.separator + login + File.separator + "Private" + File.separator + fileOrUserName);
        } else {
            path = Path.of("Storage" + File.separator + login + File.separator + fileOrUserName);
        }
        try {
            Files.createFile(path);
        } catch (FileAlreadyExistsException ex) {
            outputStream.writeUTF("File " + ex.getMessage() + " exists");
            outputStream.flush();
            System.err.println("File " + ex.getMessage() + " exists");
        }
        byte[] buffer = new byte[(int) inputStream.readLong()];
        inputStream.read(buffer);
        Files.write(path, buffer);
        outputStream.writeUTF("The file is uploaded to the server");
        outputStream.flush();
        return true;
    }

    private void sendFile() throws IOException {
        fileSearch();
        send();
    }

    private void sendTheUserFile() throws IOException {
        Path path = Path.of("Storage" + File.separator + userName);
        try (Stream<Path> paths = Files.walk(path)) {
            list = paths
                    .filter(e -> !e.getParent().toString().equals("Private"))
                    .filter(e -> e.getFileName().toString().equals(fileOrUserName))
                    .toList();
        }
        send();
    }

    private void send() throws IOException {
        if (list.size() > 0) {
            outputStream.writeUTF(OK);
            outputStream.flush();
            outputStream.writeLong(Files.size(list.get(0)));
            outputStream.flush();
            outputStream.write(Files.readAllBytes(list.get(0)));
            outputStream.flush();
        } else {
            outputStream.writeUTF("File not found");
            outputStream.flush();
        }
    }

    private void deleteFile() throws IOException {
        fileSearch();
        if (list.size() > 0) {
            Files.delete(list.get(0));
            outputStream.writeUTF("File deleted");
            outputStream.flush();
        } else {
            outputStream.writeUTF("File not found");
            outputStream.flush();
        }
    }

    private void fileSearch() throws IOException {
        Path path = Path.of("Storage" + File.separator + login);
        try (Stream<Path> paths = Files.walk(path)) {
            list = paths
                    .filter(e -> e.getFileName().toString().equals(fileOrUserName))
                    .toList();
        }
    }

    private void getMyFiles() throws IOException {
        Path path = Path.of("Storage" + File.separator + login);
        try (Stream<Path> paths = Files.walk(path)) {
            list1 = paths
                    .filter(e -> !Files.isDirectory(e))
                    .map(e -> {
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("File name : ").append(e.getFileName().toString()).append("; ");
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(e, BasicFileAttributes.class);
                            stringBuilder.append("file size : ").append(Files.size(e)).append(" bytes").append("; ");
                            stringBuilder.append("File creation : ").append(attrs.creationTime());
                            if (e.getParent().getFileName().toString().equals("Private")) {
                                stringBuilder.append("; Access mode : personal");
                            } else {
                                stringBuilder.append("; Access mode : general");
                            }
                        } catch (IOException ex) {
                        }
                        return stringBuilder.toString();
                    })
                    .toList();
        }
        outputFiles();
    }

    private void getUsers() throws IOException {
        Path path = Path.of("Storage");
        try (Stream<Path> paths = Files.walk(path, 1)) {
            list1 = paths.filter(e -> !e.getFileName().toString().equals("Storage"))
                    .map(e -> e.getFileName().toString())
                    .toList();
        }
        outputStream.writeUTF(list1.toString());
        outputStream.flush();
    }

    private void sendFilesOfUser() throws IOException {
        Path path = Path.of("Storage" + File.separator + fileOrUserName);
        try (Stream<Path> paths = Files.walk(path)) {
            list1 = paths
                    .filter(e -> !e.getParent().getFileName().toString().equals("Private") && !Files.isDirectory(e))
                    .map(e -> {
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("File name : ").append(e.getFileName().toString()).append("; ");
                        try {
                            BasicFileAttributes attrs = Files.readAttributes(e, BasicFileAttributes.class);
                            stringBuilder.append("file size : ").append(Files.size(e)).append(" bytes").append("; ");
                            stringBuilder.append("File creation : ").append(attrs.creationTime());
                        } catch (IOException ex) {
                        }
                        return stringBuilder.toString();
                    })
                    .toList();
        }
        outputFiles();
    }

    private void outputFiles() throws IOException {
        outputStream.writeInt(list1.size());
        for (String el : list1) {
            outputStream.writeUTF(el);
            outputStream.flush();
        }
    }
}
