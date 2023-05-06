package remote;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;

/**
 * service thread to run a remote service.
 * @param <T> general type for a service class
 */
public class ServiceThread<T> extends Thread {
    Service<T> service;
    LeakySocket leakySocket;

    /**
     * constructor of serviceThread.
     * @param leakySocket leaky socket
     * @param service remote service
     */
    public ServiceThread(LeakySocket leakySocket, Service<T> service) {
        this.leakySocket = leakySocket;
        this.service = service;
    }

    /**
     * run the service thread.
     */
    @Override
    public void run() {
        try {
            if (service.isServerRunning()) {
                Message received = (Message) leakySocket.recvObject();
                try {
                    Method method = service.getC().getMethod(received.getMethodName(), received.getParameterTypes());
                    Object res = method.invoke(service.getSvc(), received.getArgs());
                    leakySocket.sendObject(res);
                } catch (InvocationTargetException e) {
                    leakySocket.sendObject(e.getTargetException());
                } catch (NoSuchMethodException e) {
                    leakySocket.sendObject(e);
                }
            }
        } catch (Exception e) {
            try {
                leakySocket.sendObject(e);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } finally {
            leakySocket.close();
        }
    }
}
