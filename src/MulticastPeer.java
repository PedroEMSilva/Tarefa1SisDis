
import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

public class MulticastPeer {

    //rand para criar o id 
    Random rand = new Random();
    //criando id (unico) 
    int id = rand.nextInt(50) + 1;
    //lista de usuarios conhecidos 
    List<user> knownUsers = new ArrayList<>();
    //lista de datagramas para mandar
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
            //cria thread de envio
            speaker sp = new speaker(s, myMulti);
            //cria thread de recepção de unicast
            listenerUnicast c2 = new listenerUnicast( myMulti);

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

                    String stringToSend = new String("[" + myMulti.id + "]:[Find file]" + sc.nextLine());
                    byte[] m = stringToSend.getBytes();
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

        DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);

        while (running) {

            try {
                socket.receive(messageIn);
            } catch (IOException ex) {
                Logger.getLogger(listener.class.getName()).log(Level.SEVERE, null, ex);
            }

            String recebida = new String(messageIn.getData());
            String idString = new String(recebida.split("\\]")[0].trim());
            idString = new String(idString.split("\\[")[1].trim());
            Integer id = Integer.valueOf(idString);
            if(id == myMulti.id)
            {
                continue;
            }
            
            
            int port = id+10000;
            //se falarem no multicast um id, tenho certeza que é um usuário novo portanto vou responder ele por unicast
            if (recebida.contains("[New user]")) {
                String NewIdString = new String(recebida.split("\\]")[2].trim());
                Integer newId = Integer.valueOf(NewIdString);

                if (newId != myMulti.id) {
                    Boolean findEqual = false;
                    for (user u : myMulti.knownUsers) {
                        if (u.name == newId) {
                            findEqual = true;
                            break;
                        }
                    }
                    if (findEqual == false) {
                        try {
                            myMulti.knownUsers.add(new user(newId, "", ""));
                            
                            System.out.println("minha lista é: " + myMulti.knownUsers);
                            //mandar meu contato para o novo user
                            String stringToSend = new String("[" + myMulti.id + "]: " + "[New user]" + myMulti.id);
                            int newPort = newId+10000;
                            
                            Socket clientSocket = new Socket("localhost", newPort);
                            
                            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                             out.println(stringToSend);
                            
                            
                            
                        } catch (IOException ex) {
                            Logger.getLogger(listener.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }

                }
            } else if (recebida.contains("[Find file]")) {    //se alguem ta procurando  o arquivo, tenho que ver se tenho o arquivo e responder por unicast
                try {
                    //System.out.println(recebida);
                    String fileNameString = new String(recebida.split("\\]")[2].trim());
                    
                    //checar se eu tenho arquivo
                    String stringToSend = new String("[" + myMulti.id + "]: " + "[File found]" + " eu tenho " + fileNameString);
                    
                    
                    Socket clientSocket = new Socket("localhost", port);
                    
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    out.println(stringToSend);
                } catch (IOException ex) {
                    Logger.getLogger(listener.class.getName()).log(Level.SEVERE, null, ex);
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

class listenerUnicast extends Thread {

    boolean running = true;
    MulticastPeer myMulti;
    List<Socket> clientSocketList = new ArrayList<>();
    byte[] buffer = new byte[1000];

    public listenerUnicast( MulticastPeer myMulti) {
        this.myMulti = myMulti;
        this.start();
    }

    public void run() {
       

        try {
            int port = 10000 + myMulti.id;
            
            ServerSocket serverSocket = new ServerSocket(port);

            serverSocket.setSoTimeout(1000);

            while (true) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                } catch (IOException ex) {
                    //Logger.getLogger(listenerUnicast.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (clientSocket != null) {
                    clientSocket.setSoTimeout(1000);
                    
                    clientSocketList.add(clientSocket);
                }
                for (Socket s : clientSocketList) {
                    DatagramPacket messageIn = new DatagramPacket(buffer, buffer.length);
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    String recebida = null;
                    try {
                       
                        recebida =in.readLine();
                    } catch (IOException ex) {
                       // Logger.getLogger(listenerUnicast.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    s.close();
                    
                    System.out.println("recebida uni :" + recebida);
                    //Se alguem me mandar por unicast seu id, eu nã presciso responder, mas tenho que ver se ja tenho o contato
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

                            }

                        }
                    }

                }
                clientSocketList.clear();

            }
        } catch (IOException ex) {
            Logger.getLogger(listenerUnicast.class.getName()).log(Level.SEVERE, null, ex);
        }
       

        

    }

}
