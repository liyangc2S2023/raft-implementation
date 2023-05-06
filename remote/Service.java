package remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Remote Object Service
    <p>
    A <code>Service</code> encapsulates a multithreaded TCP server and allows connections
    from client Stubs that are created using the <code>StubFactory</code>.
    <p>
    The <code>Service</code> class is parametrized by a type variable. This type variable
    should be instantiated with an interface. The <code>Service</code> will accept calls
    from <code>Stubs</code> to invoke methods on this interface.  It will then forward those
    requests to an instantiated <code>Object</code>, which is specified when the <code>Service</code>
    is constructed.  The <code>Object</code> must implement the remote interface.  Each method
    in the interface should be marked as throwing <code>RemoteObjectException</code>,
    in addition to any other exceptions as needed.
    <p>
    Exceptions may occur at the top level in the listening and service threads.
    The <code>Service</code>'s response to these exceptions can be customized by deriving
    a class from <code>Service</code> and overriding <code>listen_error</code>
    or <code>service_error</code>.
*/
public class Service<T> {
    private final Class<T> c;
    private final T svc;
    private final int port;
    private final boolean lossy;
    private final boolean delayed;
    ServerSocket serverSocket;
    private ClientThread<T> clientThread;
    private boolean isServerRunning;
    ReentrantLock serviceLock;
    Map<SocketAddress, ServiceThread> runningServiceThread;

    /** The first constructor creates a <code>Service</code> that is bound to
        a given remote interface, instantiated object, and server port number.
        This constructor is used when no loss or delay is desired for the
        network Sockets.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param svc    An instantiated object that implements the interface
                      indicated by <code>c</code>.  Upon receipt of requests for
                      method calls, the Service invokes those calls on this object.
        @param port   server port.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw 
                      <code>RemoteObjectException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>svc</code> is <code>null</code>.
     */
    public Service(Class<T> c, T svc, int port) {
        this(c, svc, port, false, false);
    }

    /** The second constructor creates a <code>Service</code> similar to the 
        first one, but with additional parameters to enable simulated loss
        and/or delay of Objects being sent over network Sockets.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param svc    An instantiated object that implements the interface
                      indicated by <code>c</code>.  Upon receipt of requests for
                      method calls, the Service invokes those calls on this object.
        @param port   server port.
        @param lossy  A flag that indicates whether or not Objects can be lost
                      between sender and receiver, resulting in timeout.
        @param delayed A flag that indicates whether propagation delay is incurred
                      when sending an Object from sender to receiver.
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw 
                      <code>RemoteObjectException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>svc</code> is <code>null</code>.
     */
    public Service(Class<T> c, T svc, int port, boolean lossy, boolean delayed) {
        if (svc == null) {
            throw new NullPointerException("The instantiated object cannot be null");
        }
        checkIfRemoteInterface(c);
        this.c = c;
        this.svc = svc;
        this.port = port;
        this.lossy = lossy;
        this.delayed = delayed;
        this.isServerRunning = false;
        this.serviceLock = new ReentrantLock();
        this.runningServiceThread = new ConcurrentHashMap<>();
    }

    /**
     * check the input class is a remote interface.
     * @param c
     */
    private void checkIfRemoteInterface(Class<T> c) {
        if (c == null) {
            throw new NullPointerException("The class representation cannot be null");
        }
        for (Method method: c.getMethods()) {
            boolean foundRemoteException = false;
            for (Class<?> exceptionType: method.getExceptionTypes()) {
                if (exceptionType.equals(RemoteObjectException.class)) {
                    foundRemoteException = true;
                }
            }
            if (!foundRemoteException) {
                throw new Error("C does not represent a remote interface");
            }
        }
    }
    
    /** When the listening thread exits, it should call <code>stopped</code>.
        <p>
        The parameter passed from the listening thread allows the <code>Service</code>
        to react accordingly, namely whether the thread stops due to an exception
        or a call to <code>stop</code>.
        <p>
        When this method is called, the calling thread owns the lock on the
        <code>Service</code> object. Care must be taken to avoid deadlocks when
        calling <code>start</code> or <code>stop</code> from different threads
        during this call.
        <p>
        The default implementation does nothing.
        @param cause The exception that stopped the Service, or
                     <code>null</code> if the Service stopped normally.
     */  
    protected void stopped(Throwable cause) {
    }

    /** When an exception occurs in the listening thread, it also calls
        <code>listen_error</code>.
        <p>
        The intent of this method is to allow the user to report exceptions in
        the listening thread to another thread, by a mechanism of the user's
        choosing. The user may also ignore the exceptions. The default
        implementation simply stops the server. The user should not use this
        method to stop the Service. The exception will again be provided as the
        argument to <code>stopped</code>, which will be called later.
        @param exception The exception that occurred.
        @return <code>true</code> if the service should resume accepting
                connections, <code>false</code> if the service should shut down.
     */
    protected boolean listen_error(Exception exception) {
        return false;
    }

    /** When an exception occurs in a service thread, <code>service_error</code>
        is called, similar to previous.
        <p>
        The default implementation does nothing.
        @param exception The exception that occurred.
     */
    protected void service_error(RemoteObjectException exception) {
    }

    /** The Service is started using <code>start</code>.
        <p>
        A thread is created to listen for connection requests on the port
        specified when the Service was constructed.  The network address
        can be learned using suitable Socket APIs.  After creating the listening
        thread, this method should return immediately.
        <p>
        The <code>synchronized</code> keyword may need to be added, depending
        on the implementation.
        @throws RemoteObjectException When the listening socket cannot be created or
                bound, when the listening thread cannot be created, or when the server
                has already been started and has not since stopped.
     */
    public synchronized void start() throws RemoteObjectException {
        serviceLock.lock();
        if (this.isServerRunning) {
            serviceLock.unlock();
            throw new RemoteObjectException("");
        }
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            serviceLock.unlock();
            throw new RemoteObjectException(e.getMessage());
        }
        serviceLock.unlock();
        setServerRunning(true);
        clientThread = new ClientThread<>(serverSocket, this);
        clientThread.start();
    }


    /** The Service is stopped using <code>stop</code>, if it is running.
        <p>
        This terminates the listening thread and calls other methods as
        needed.
        <p>
        The <code>synchronized</code> keyword may be needed, depending
        on the implementation.
     */
    public synchronized void stop() {
        setServerRunning(false);
        try {
            serverSocket.close();
            for (ServiceThread t: runningServiceThread.values()) {
                t.join();
            }
            clientThread.interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        stopped(null);
    }

    /**
     * get class.
     * @return service class.
     */
    public Class<T> getC() {
        return c;
    }

    /**
     * get service object.
     * @return service object
     */
    public T getSvc() {
        return svc;
    }

    /**
     * get server port
     * @return server port
     */
    public int getPort() {
        return port;
    }

    /**
     * get whether the socket is lossy.
     * @return if the socket is lossy
     */
    public boolean isLossy() {
        return lossy;
    }

    /**
     * get whether the socket is delayed.
     * @return if the socket is delayed
     */
    public boolean isDelayed() {
        return delayed;
    }

    /**
     * get server status
     * @return server status
     */
    public boolean isServerRunning() {
        return isServerRunning;
    }

    /**
     * set server status
     * @param isServerRunning server status
     */
    public void setServerRunning(boolean isServerRunning) {
        this.isServerRunning = isServerRunning;
    }
}

