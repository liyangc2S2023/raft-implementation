package remote;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Remote Object stub factory.
    <p>
    Remote Object stubs hide network communication with the remote server and 
    provide a simple object-like interface to their users. This class provides 
    methods for creating stub objects dynamically, when given pre-defined interfaces.
    <p>
    The network address of the remote Service is set when a stub is created, and
    may not be modified afterwards.
 */
public abstract class StubFactory {
    /**
     * constructor of StubFactory
     */
    public StubFactory() {
    }

    /** The first static <code>create</code> method to create a Stub accepts
        the desired remote interface and Service address.  This method should
        only be used when no loss or delay is desired for the network Sockets.
        <p>
        This method assumes the remote Service is already running at the
        specified address.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param addr   The network address of the Service as "ip:port"
        @return The stub created.
        @param <T> type parameter
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw <code>RemoteObjectException</code>.
        @throws NullPointerException If <code>c</code> is <code>null</code>.
     */
    public static <T> T create(Class<T> c, String addr) {
        return create(c, addr, false, false);
    }

    /** The second static <code>create</code> method to create a Stub accepts
        the desired remote interface and Service address, in addition to two
        boolean flags to configure loss and delay of the underlying network
        Sockets.
        <p>
        This method assumes the remote Service is already running at the
        specified address.
        @param c      A representation of the class of the interface that the
                      Service must handle method call requests for.
        @param addr   The network address of the Service as "ip:port"
        @param sockLoses  A flag that indicates whether or not Objects can be lost
                      between sender and receiver, resulting in timeout.
        @param sockDelays A flag that indicates whether propagation delay is incurred
                      when sending an Object from sender to receiver.
        @return The stub created.
        @param <T> type parameter
        @throws Error If <code>c</code> does not represent a remote interface, i.e.,
                      an interface whose methods all throw <code>RemoteObjectException</code>.
        @throws NullPointerException If <code>c</code> is <code>null</code>.
     */
    public static <T> T create(Class<T> c, String addr, boolean sockLoses, boolean sockDelays) {
        if (c == null || addr == null) {
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
        InvocationHandler handler = new StubInvocationHandler(addr, sockLoses, sockDelays, c);
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class[] { c }, handler);
    }    
}
