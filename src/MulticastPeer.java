
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
    List<DatagramPacket> toSendList = new ArrayList<>();
    InetAddress group;

    public static void main(String args[]) throws UnknownHostException {
        MulticastPeer myMulti = new MulticastPeer();
        myMulti.group = InetAddress.getByName("228.5.6.7");
        System.out.println("Eu: id = " + myMulti.id);
        global.estado = 0;
        //define ip do multicast

        MulticastSocket s = null;
        try {

            //cria socket
            s = new MulticastSocket(6789);
            //da join no grupo de multicast
            s.joinGroup(myMulti.group);

            //cria thread de recepção
            listener c = new listener(s, myMulti);
            speaker sp = new speaker(s, myMulti);
            
            while (true) {
                if (global.estado == 0) {
                    String stringToSend = new String("[" + myMulti.id + "]: " + "[New user]" + myMulti.id);
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(stringToSend.getBytes(), stringToSend.length(), myMulti.group, 6789);
                    //manda a mensagem
                    myMulti.toSendList.add(messageOut);
                    global.estado = 1;
                }
                //scanner para ler do teclado
                Scanner sc = new Scanner(System.in);
                if (global.estado == 1) {
                    //le do teclado e transforma em bytes
                    
                    String stringToSend = new String("[" + myMulti.id + "]:[Find file]" +  sc.nextLine());
                    byte[] m =stringToSend.getBytes();
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(m, m.length, myMulti.group, 6789);
                    //manda a mensagem
                    myMulti.toSendList.add(messageOut);

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
                    Boolean findEqual = false;
                    for (user u : myMulti.knownUsers) {
                        if (u.name == newId) {
                            findEqual = true;
                            break;
                        }
                    }
                    if (findEqual == false) {
                        myMulti.knownUsers.add(new user(newId, "", ""));

                        System.out.println("minha lista é: " + myMulti.knownUsers);
                        //mandar meu contato para o novo user
                        String stringToSend = new String("[" + myMulti.id + "]: " + "[New user]" + myMulti.id);
                        
                        myMulti.toSendList.add(new DatagramPacket(stringToSend.getBytes(), stringToSend.length(), myMulti.group, 6789));
                        
                    }

                }
            }
            else if(recebida.contains("[Find file]"))
            {
                //System.out.println(recebida);
                String fileNameString = new String(recebida.split("\\]")[2].trim());
                String stringToSend = new String("[" + myMulti.id + "]: " + "[File found]" + " eu tenho " + fileNameString);
                        
                myMulti.toSendList.add(new DatagramPacket(stringToSend.getBytes(), stringToSend.length(), myMulti.group, 6789));
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

class speaker extends Thread {

    MulticastPeer myMulti;
    MulticastSocket s;

    public speaker(MulticastSocket socket, MulticastPeer myMulti) {
        this.myMulti = myMulti;
        this.s = socket;
        this.start();
    }

    public void run() {

        try {
            //loop  de envio
            while (true) {
               // System.out.println(".");
                sleep(1000);
                //System.out.println("-");
                if (myMulti.toSendList.size() > 0) {
                    //System.out.println("algo para falar");
                    for (int i = 0; i < myMulti.toSendList.size(); i++) {
                       // System.out.println("in");
                        s.send(myMulti.toSendList.remove(0));
                       // System.out.println("out");
                    }

                }

            }
        } catch (IOException ex) {
            Logger.getLogger(speaker.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(speaker.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
