package remote;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * a stub invocation handler to send request message to remote service and receive response message.
 * @param <T> general type for a service class
 */
public class StubInvocationHandler<T> implements InvocationHandler {
    private Class<T> targetClass;
    private String addr;
    private boolean isLossy;
    private boolean isDelayed;

    /**
     * constructor of stubInvocationHandler.
     * @param addr client address
     * @param isLossy if the socket is lossy
     * @param isDelayed if the socket is delayed.
     * @param targetClass the target remote service class
     */
    public StubInvocationHandler(String addr, boolean isLossy, boolean isDelayed, Class<T> targetClass) {
        this.targetClass = targetClass;
        this.addr = addr;
        this.isLossy = isLossy;
        this.isDelayed = isDelayed;
    }

    /**
     * invoke methods in the remote service.
     * @param proxy the proxy instance that the method was invoked on
     *
     * @param method the {@code Method} instance corresponding to
     * the interface method invoked on the proxy instance.  The declaring
     * class of the {@code Method} object will be the interface that
     * the method was declared in, which may be a superinterface of the
     * proxy interface that the proxy class inherits the method through.
     *
     * @param args an array of objects containing the values of the
     * arguments passed in the method invocation on the proxy instance,
     * or {@code null} if interface method takes no arguments.
     * Arguments of primitive types are wrapped in instances of the
     * appropriate primitive wrapper class, such as
     * {@code java.lang.Integer} or {@code java.lang.Boolean}.
     *
     * @return the response object
     * @throws Throwable RemoteObjectExceptions
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LeakySocket leakySocket = null;
        try {
            leakySocket = new LeakySocket(addr, isLossy, isDelayed);
        } catch (Exception e) {
            throw new RemoteObjectException(e.getMessage());
        }

        Class<?>[] types = method.getParameterTypes();
        Message msg = new Message(targetClass.getName(), method.getName(), args, types);
        boolean isSent = false;
        while (!isSent) {
            try {
                isSent = leakySocket.sendObject(msg);
            } catch (IOException e) {
                throw new RemoteObjectException(e.getMessage());
            }
        }

        Object result = null;
        try {
            result = leakySocket.recvObject();
        } catch (IOException e) {
            if (!e.getMessage().contains("EOFException")) {
                throw new RemoteObjectException(e.getMessage());
            }
        }
        if (result instanceof Exception) {
            if (result instanceof NoSuchMethodException) {
                throw new RemoteObjectException("NoSuchMethodException");
            } else {
                throw new RemoteObjectException(((Exception) result).getMessage());
            }
        }
        if (leakySocket != null) {
            leakySocket.close();
        }
        return result;
    }
}
