package remote;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * client thread for a client connected to a server.
 * @param <T> general type for a service class
 */
public class ClientThread<T> extends Thread {
    /**
     * server socket.
     */
    private ServerSocket serverSocket;
    /**
     * remote service.
     */
    private Service<T> remoteService;
    /**
     * running status.
     */
    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * constructor of client thread.
     * @param serverSocket server socket
     * @param remoteService remote service
     */
    public ClientThread(ServerSocket serverSocket, Service<T> remoteService) {
        this.serverSocket = serverSocket;
        this.remoteService = remoteService;
    }

    /**
     * stop client thread.
     */
    public void stopThread() {
        running.set(false);
    }

    /**
     * run client thread.
     */
    @Override
    public void run() {
        running.set(true);
        while (running.get()) {
            Socket socket = null;
            try {
                if (!serverSocket.isClosed() && remoteService.isServerRunning()) {
                    socket = serverSocket.accept();
                } else {
                    break;
                }
                if (!socket.isClosed()) {
                    ServiceThread serviceThread = new ServiceThread<T>(
                            new LeakySocket(socket, remoteService.isLossy(), remoteService.isDelayed()),
                            remoteService);
                    remoteService.runningServiceThread.put(socket.getRemoteSocketAddress(), serviceThread);
                    serviceThread.start();
                }
            } catch (Exception e) {
                /**
                 * 1. check if server is running:
                 * 2. if it's running and got error from listener obj.
                 * 3. then lock service and set running to false -> restart
                 * 4. close server thread do it in try catch. (do lock/unlock)
                 *
                 * // lock
                 *                     // check is exists an error on lisntener obj
                 *                     // if error, lock the service object
                 * */
                if (remoteService.isServerRunning() && e instanceof SocketException) {
                    remoteService.serviceLock.lock();
                    try {
                        remoteService.setServerRunning(false);
                        ServiceThread runningThread =
                                remoteService.runningServiceThread.get(socket.getRemoteSocketAddress());
                        if(runningThread != null) runningThread.join();
                    } catch (InterruptedException ex) {
                        remoteService.serviceLock.unlock();
                        throw new RuntimeException(ex);
                    }
                    remoteService.serviceLock.unlock();
                }
            }
        }
    }
}