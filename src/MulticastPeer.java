
import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

public class MulticastPeer {

    Random rand = new Random();
    int id = rand.nextInt(50) + 1;
    List<user> knownUsers = new ArrayList<>();

    public static void main(String args[]) {
        MulticastPeer myMulti = new MulticastPeer();
        System.out.println("Eu: id = " + myMulti.id);
        global.estado = 0;

        MulticastSocket s = null;
        try {
            //define ip do multicast
            InetAddress group = InetAddress.getByName("228.5.6.7");
            //cria socket
            s = new MulticastSocket(6789);
            //da join no grupo de multicast
            s.joinGroup(group);
            //scanner para ler do teclado
            Scanner sc = new Scanner(System.in);

            //cria thread de recepção
            listener c = new listener(s, myMulti);

            //loop  de envio
            while (true) {

                if (global.estado == 0) {
                    String stringToSend = new String("[" + myMulti.id + "]: " + "[New user]" + myMulti.id);
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(stringToSend.getBytes(), stringToSend.length(), group, 6789);
                    //manda a mensagem
                    s.send(messageOut);
                    global.estado = 1;
                } else if (global.estado == 1) {
                    //le do teclado e transforma em bytes
                    byte[] m = sc.nextLine().getBytes();
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(m, m.length, group, 6789);
                    //manda a mensagem
                    s.send(messageOut);

                }

            }

        } catch (SocketException e) {
            System.out.println("Socket: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        } finally {
            if (s != null) {
                s.close();
            }
        }
    }

}

class listener extends Thread {

    InetAddress group;
    MulticastSocket socket;
    boolean running = true;
    MulticastPeer myMulti;
    byte[] buffer = new byte[1000];

    public listener(MulticastSocket socket, MulticastPeer myMulti) {
        this.socket = socket;
        this.myMulti = myMulti;
        this.start();
    }

    public void run() {

        // an echo server
        DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);

        while (running) {

            try {
                socket.receive(messageIn);
            } catch (IOException ex) {
                Logger.getLogger(listener.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
            String recebida = new String(messageIn.getData());
            if (recebida.contains("[New user]")) {
                String idString = new String(recebida.split("\\]")[2].trim());
                Integer newId = Integer.valueOf(idString);
                if (newId != myMulti.id) {
                    myMulti.knownUsers.add(new user(newId, "", ""));
                    System.out.println("minha lista é: " + myMulti.knownUsers);
                }
            }

            System.out.println(new String(messageIn.getData()));
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = 0;

            }
        }
    }

    public void exit(int status) {
        running = false;
    }
}
