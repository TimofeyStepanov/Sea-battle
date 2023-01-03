package com.example.demobb;

import java.io.*;
import java.net.Socket;

public class Phone implements Closeable {
    private final ObjectInputStream objectInputStream;
    private final ObjectOutputStream objectOutputStream;
    private final Socket socket;

    public Phone(Socket socket) throws IOException {
        objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectInputStream = new ObjectInputStream(socket.getInputStream());
        this.socket = socket;
    }

    public void send(Object objectToSend) throws IOException {
        objectOutputStream.writeObject(objectToSend);
    }

    public Object receive() throws IOException, ClassNotFoundException {
        return objectInputStream.readObject();
    }

    @Override
    public void close() throws IOException {
        objectOutputStream.close();
        objectInputStream.close();
        socket.close();
    }
}
