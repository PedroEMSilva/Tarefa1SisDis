
import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class MulticastPeer {

    //rand para criar o id 
    Random rand = new Random();
    //criando id (unico) 
    int id = rand.nextInt(50) + 1;
    //lista de usuarios conhecidos 
    List<user> knownUsers = new ArrayList<>();
    //lista de datagramas para mandar
    List<DatagramPacket> toSendList = new ArrayList<>();
    //lista de arquivos que eu tenho
    List<FileAndSize> myFiles = new ArrayList<>();

    InetAddress group;

    public static void main(String args[]) throws UnknownHostException, InterruptedException {

        MulticastPeer myMulti = new MulticastPeer();
        //listar arquivos
        final File folder = new File("Files");
        listFilesForFolder(folder, myMulti);
        myMulti.group = InetAddress.getByName("228.5.6.7");
        System.out.println("Eu: id = " + myMulti.id);

        GenerateKeys gk;
        try {
            gk = new GenerateKeys(1024);
            gk.createKeys();
            gk.writeToFile("KeyPair/publicKey", gk.getPublicKey().getEncoded());
            gk.writeToFile("KeyPair/privateKey", gk.getPrivateKey().getEncoded());
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

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
            listenerUnicast c2 = new listenerUnicast(myMulti);
            String fileToGet = null;
            Scanner sc = new Scanner(System.in);
            user chosenOne = null;
            while (true) {
                //estado 0 : chegando na rede
                if (global.estado == 0) {
                    String stringToSend = new String("[" + myMulti.id + "]: " + "[New user]" + myMulti.id);
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(stringToSend.getBytes(), stringToSend.length(), myMulti.group, 6789);
                    //manda a mensagem
                    myMulti.toSendList.add(messageOut);
                    global.estado = 1;
                }
                //scanner para ler do teclado
                
                
                //estado 1 : peguntando sobre um arquivo
                if (global.estado == 1) {
                    //le do teclado e transforma em bytes
                    fileToGet = new String(sc.nextLine());
                    String stringToSend = new String("[" + myMulti.id + "]:[Find file]" + fileToGet);
                    byte[] m = stringToSend.getBytes();
                    //cria um datagram com a mensagem
                    DatagramPacket messageOut = new DatagramPacket(m, m.length, myMulti.group, 6789);
                    //manda a mensagem
                    myMulti.toSendList.add(messageOut);
                    global.estado = 2;

                }
                //estado 2 : esperando atualização da lista de arquivos
                if (global.estado == 2) {
                    Thread.sleep(3000);

                    global.estado = 3;
                }
                //estado 3 : escolher um nó que tem o arquivo e pedir para ele
                if (global.estado == 3) {
                    //escolher
                    int maxRep = 0;
                    chosenOne = null;
                    for (user u : myMulti.knownUsers) {
                        for (FileAndSize f : u.files) {
                            if (f.fileName.equals(fileToGet) && u.rep > maxRep) {
                                maxRep = u.rep;
                                chosenOne = u;
                            }
                        }

                    }
                    if (maxRep > 0) {
                        String stringToSend = new String("[" + myMulti.id + "]:[Give me]" + fileToGet);
                        int port = chosenOne.name.intValue() + 10000;
                        Socket clientSocket = new Socket("localhost", port);

                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        out.println(stringToSend);
                        global.estado = 4;

                    } else {
                        System.out.println("Arquivo não encontrado na rede");
                        global.estado = 1;
                    }

                }
                if (global.estado == 4) //estado espera 3s e retira reputação se o arquivo n chegou ainda
                {
                    Thread.sleep(3000);
                    boolean found = false;
                    for(FileAndSize f : myMulti.myFiles)
                    {
                        if(f.fileName.equalsIgnoreCase(fileToGet))
                        {
                            //recebi!!
                            found = true;
                            System.out.println("Arquivo " + f.fileName + "recebido com sucesso");
                            break;
                        }
                    }
                    if(!found)
                    {
                     chosenOne.rep--;   
                    }
                    global.estado = 1;
                    
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

    public static void listFilesForFolder(final File folder, MulticastPeer myMulti) {
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry, myMulti);
            } else {
                System.out.println(fileEntry.getName() + " - Size: " + fileEntry.length());
                myMulti.myFiles.add(new FileAndSize(fileEntry.getName(), fileEntry.length()));
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
            if (id == myMulti.id) {
                continue;
            }

            int port = id + 10000;
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
                            int newPort = newId + 10000;

                            Socket clientSocket = new Socket("localhost", port);

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
                    boolean found = false;
                    for (FileAndSize f : myMulti.myFiles) {
                        if (f.fileName.equals(fileNameString)) {
                            String stringToSend = new String("[" + myMulti.id + "]: " + "[File found]" + fileNameString + "[Size]" + f.fileSize);

                            Socket clientSocket = new Socket("localhost", port);

                            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                            out.println(stringToSend);

                            break;
                        }

                    }
                    //se eu tenho, avisa por unicast

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

    public listenerUnicast(MulticastPeer myMulti) {
        this.myMulti = myMulti;
        this.start();
    }

    public void run() {

        try {
            int myPort = 10000 + myMulti.id;

            ServerSocket serverSocket = new ServerSocket(myPort);

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

                        recebida = in.readLine();
                    } catch (IOException ex) {
                        // Logger.getLogger(listenerUnicast.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    s.close();

                    System.out.println("recebida uni :" + recebida);
                    String idString = new String(recebida.split("\\]")[0].trim());
                    idString = new String(idString.split("\\[")[1].trim());
                    Integer id = Integer.valueOf(idString);

                    //Se alguem me mandar por unicast seu id, eu nã presciso responder, mas tenho que ver se ja tenho o contato
                    if (recebida.contains("[New user]")) {
                        String newIdString = new String(recebida.split("\\]")[2].trim());
                        Integer newId = Integer.valueOf(newIdString);

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
                    } else if (recebida.contains("[File found]")) //se alguem me falar que tem o arquivo, atualiza isso na lista de usuarios conhecidos
                    {

                        //acha o cara e atualiza
                        for (user u : myMulti.knownUsers) {

                            if (u.name.intValue() == id) {
                                String fileString = new String(recebida.split("\\]")[2].trim());
                                fileString = new String(fileString.split("\\[")[0].trim());

                                String fileSizeString = new String(recebida.split("\\]")[3].trim());
                                Integer fileSize = Integer.valueOf(fileSizeString);

                                u.files.add(new FileAndSize(fileString, fileSize));

                                break;
                            }

                        }
                    } else if (recebida.contains("[Give me]")) //se alguem me falar que quer um arquivo, vou passar ele por unicast
                    {
                        String fileString = new String(recebida.split("\\]")[2].trim());
                        String stringToSend = null;
                        //achar e carregar arquivo
                        for (FileAndSize f : myMulti.myFiles) {
                            if (f.fileName.equalsIgnoreCase(fileString)) {
                                FileReader fileReader = new FileReader("Files/" + f.fileName);
                                BufferedReader bufferedReader = new BufferedReader(fileReader);
                                String fileContent = new String("");
                                String line = new String();

                                while ((line = bufferedReader.readLine()) != null) {
                                    fileContent = new String(fileContent + line);
                                }

                                // Always close files.
                                bufferedReader.close();

                                stringToSend = new String("[" + myMulti.id + "]: " + "[Sending File]" + fileString + "[Size]" + f.fileSize + "[Content]" + fileContent);
                            }
                        }
                        int port = 10000 + id;
                        System.out.println("vou dar : " + fileString);
                        System.out.println("para " + port);
                        Socket toSend = new Socket("localhost", port);

                        PrintWriter out = new PrintWriter(toSend.getOutputStream(), true);
                        out.println(stringToSend);
                    } else if (recebida.contains("[Sending File]")) //se alguem me mandar um arquivo , vou guardar ele e atualizar a lista
                    {
                        String fileString = new String(recebida.split("\\]")[2].trim());
                        fileString = new String(fileString.split("\\[")[0].trim());
                        String fileSize = new String(recebida.split("\\]")[3].trim());
                        fileSize = new String(fileSize.split("\\[")[0].trim());
                        int size = Integer.valueOf(fileSize);
                        String fileContent = new String(recebida.split("\\]")[4].trim());
                        fileContent = new String(fileContent.split("\\[")[0].trim());

                        user fileSender = null;
                        for (user u : myMulti.knownUsers) {
                            if (u.name.intValue() == id) {
                                fileSender = u;
                                break;
                            }
                        }

                        if (size == fileContent.length()) {
                            //rep up
                            fileSender.rep++;

                        } else {
                            //rep down
                            fileSender.rep--;
                        }

                        FileWriter fileWriter = new FileWriter("Files/" + fileString);
                        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                        bufferedWriter.write(fileContent);
                        bufferedWriter.close();
                        myMulti.myFiles.add(new FileAndSize(fileString, size));

                    }

                }
                clientSocketList.clear();

            }
        } catch (IOException ex) {
            Logger.getLogger(listenerUnicast.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
