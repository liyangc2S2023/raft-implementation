package remote;

import java.io.Serializable;

/**
 * message object sent between clients and servers.
 */
public class Message implements Serializable {
    /**
     * message class name.
     */
    private String className;
    /**
     * message method name.
     */
    private String methodName;
    /**
     * message arguments from clients.
     */
    private Object[] args;
    /**
     * parameterTypes.
     */
    private Class<?>[] parameterTypes;

    /**
     * constructor of message class.
     * @param className class name
     * @param methodName method name
     * @param args arguments
     * @param parameterTypes parameter types
     */
    public Message(String className, String methodName, Object[] args, Class<?>[] parameterTypes) {
        this.className = className;
        this.methodName = methodName;
        this.args = args;
        this.parameterTypes = parameterTypes;
    }

    /**
     * get class name.
     * @return class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * get method name.
     * @return method name
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * get arguments.
     * @return arguments
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * get parameter types for service class
     * @return parameter types
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }
}
