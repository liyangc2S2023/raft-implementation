package remote;

import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.lang.Thread;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;

/** LeakySocket is a wrapper for a standard Java Socket that allows for 
    simulated message loss and packet delays. The constructor allows you
    to enable either/both features.
    <p>
    The LeakySocket implementation also incorporates generic Object
    reader and writer instances, as the loss/delay is wrapped around
    the sending functionality.  There is no loss/delay incurred at
    the receiver, which mimics real-world scenarios (i.e., a receiver
    should be unaware that something was sent if it was lost). */
public class LeakySocket {
    private Socket s;
    private ObjectOutputStream writer;
    private ObjectInputStream reader;
    private boolean isLossy;
    private double lossRate;
    private int msTimeout, usTimeout;
    private boolean isDelayed;
    private int msDelay, usDelay;
    private Random rng;
    
    /** Creates a <code>LeakySocket</code> wrapper around a new socket for a
        given address. Constructor opens object writer and reader and configures
        parameters for simulated loss and delay according to boolean params.
     * @param addr client address
     * @param lossy if the client socket is lossy
     * @param delayed if the client socket is delayed
     */
    public LeakySocket(String addr, boolean lossy, boolean delayed) throws IOException {
        String[] splitAddr = addr.split(":", 2); // address has form a.b.c.d:p
        try {
            s = new Socket(splitAddr[0], Integer.parseInt(splitAddr[1]));
            writer = new ObjectOutputStream(s.getOutputStream());
            writer.flush();
            reader = new ObjectInputStream(s.getInputStream());
        } catch (UnknownHostException e) {
            System.out.println("Invalid socket address");
            e.printStackTrace();
        } catch (IOException e) {
//            System.out.println("Socket/stream creation error");
//            e.printStackTrace();
            throw new IOException(e);
        }
        
        this.isLossy = lossy;
        this.isDelayed = delayed;
        msDelay = 2;
        usDelay = 0;
        msTimeout = 500;
        usTimeout = 0;
        lossRate = 0.05;
        rng = new Random();
    }
    
    /** Creates a <code>LeakySocket</code> wrapper around a new socket for a
        given address. Constructor opens object writer and reader and configures
        parameters for simulated loss and delay according to boolean params.
     * @param s client socket
     * @param lossy if the client socket is lossy
     * @param delayed if the client socket is delayed
     */
    public LeakySocket(Socket s, boolean lossy, boolean delayed) {
        try {
            writer = new ObjectOutputStream(s.getOutputStream());
            writer.flush();
            reader = new ObjectInputStream(s.getInputStream());
        } catch (IOException e) {
            System.out.println("Stream creation error");
            e.printStackTrace();
        }

        this.s = s;
        this.isLossy = lossy;
        this.isDelayed = delayed;
        msDelay = 2;
        usDelay = 0;
        msTimeout = 500;
        usTimeout = 0;
        lossRate = 0.05;
        rng = new Random();
    }

    /** The <code>sendObject(Object)</code> method attempts to send a Java
        object over the wrapped socket.  The parameter must be serializable
        for the method to perform correctly.
        <p>
        If the <code>LeakySocket</code> was created with <code>lossy = true</code>
        then the method will randomly decide to not send the object with the
        corresponding <code>lossRate</code> probability.  In the event of loss, 
        the method will sleep for the duration of a timeout and then return 
        <code>false</code> to indicate failure.  Otherwise, the method will 
        send the object over the underlying reliable socket.
        <p>
        Before sending the object, if <code>delayed = true</code>, the method
        will induce an additional delay, emulating a link propagation delay.
        If the object is sent, the method will return <code>true</code>.
     * @param obj send object from client to server.
     * @return whether the object is sent successfully.
     * @throws IOException Unable to output object
     */
    public boolean sendObject(Object obj) throws IOException {
        if(s != null) {
            if(isLossy && rng.nextDouble() < lossRate) {
                // send failure occurred
                try {
                    Thread.sleep(msTimeout, 1000 * usTimeout);
                } catch (InterruptedException e) {
                    System.out.println("Timeout interrupted");
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    System.out.println("Bad timeout arg");
                    e.printStackTrace();
                }
                return false;
            } else {
                if(isDelayed) {
                    try {
                        Thread.sleep(msDelay, 1000 * usDelay);
                    } catch (InterruptedException e) {
                        System.out.println("Link delay interrupted");
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        System.out.println("Bad link delay arg");
                        e.printStackTrace();
                    }
                }
                
                try {
                    writer.writeObject(obj);
                    writer.flush();
                } catch (IOException e) {
                    throw new IOException("Unable to output object: " + e);
                }
                return true;
            }
        }
        return false;
    }

    /** The <code>recvObject()</code> method will block the open socket until a
        Java object is received.  There is no simulated loss or delay at the 
        receiver side of an object transmission.  The method returns the
        received object.
     * @return received object from server
     * @throws IOException Unable to receive object
     */
    public Object recvObject() throws IOException {
        if(s != null) {
            Object obj = null;
            try {
                obj = reader.readObject();
            } catch (IOException e) {
                throw new IOException("Unable to receive object: " + e);
            } catch (ClassNotFoundException e) {
                System.out.println("Class Not Found:" + e);
                e.printStackTrace();
            }
            return obj;
        }
        return null;
    }

    /** The <code>setDelay()</code> method allows for changing the boolean 
        <code>delayed</code> parameter and the corresponding sleep duration
        (which includes both a component in milliseconds and in microseconds
        since both parameters are supported by <code>Thread.sleep</code>).
     * @param delayed if the socket is delayed
     * @param ms milliseconds
     * @param us nanoseconds
     */
    public void setDelay(boolean delayed, int ms, int us) {
        this.isDelayed = delayed;
        this.msDelay = ms;
        this.usDelay = us;
    }
    
    /** The <code>setTimeout()</code> method allows for changing the timeout
        duration incurred when a packet is lost, again including both milli-
        and micro-second components.
     * @param ms milliseconds
     * @param us nanoseconds
     */
    public void setTimeout(int ms, int us) {
        this.msTimeout = ms;
        this.usTimeout = us;
    }
    
    /** The <code>setLossRate()</code> method allows for changing the boolean 
        <code>lossy</code> parameter and the corresponding probability that
        each Object will be dropped.  To have any meaningful effect, the 
        <code>rate</code> parameter should be between 0 and 1.
     * @param lossy if the socket is lossy
     * @param rate lossy rate
     */
    public void setLossRate(boolean lossy, double rate) {
        this.isLossy = lossy;
        this.lossRate = (rate > 1 ? 1 : (rate < 0 ? 0 : rate));
    }
    
    /** The <code>close()</code> method wraps the corresponding functionality
        of the underlying Java Socket.   */
    public void close() {
        if(s != null) {
            try {
                s.close();
            } catch (IOException e) {
                System.out.println("Socket close error");
                e.printStackTrace();
            }
        }
    }
}

