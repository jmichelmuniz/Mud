import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServidorMUD {
    private static final int PORTA = 4000;
    public static Map<Integer, Sala> mapa = new HashMap<>();

    public static void main(String[] args) {
        // Inicializa Banco de Dados e Mapa
        GerenciadorBD.inicializarBanco();
        carregarMapa();
        GerenciadorCombate.iniciar();

        System.out.println("Servidor MUD iniciado na porta " + PORTA + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + socket.getInetAddress());
                
                // Cria uma nova thread para cada cliente
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    public static void conectarSalas(Sala salaA, String direcao, Sala salaB) {
        // Conecta A -> B
        salaA.adicionarSaida(direcao, salaB);

        // Encontra o oposto e conecta B -> A
        String oposto = getDirecaoOposta(direcao);
        if (oposto != null) {
            salaB.adicionarSaida(oposto, salaA);
        }
    }

    private static String getDirecaoOposta(String direcao) {
        switch (direcao.toLowerCase()) {
            case "norte": return "sul";
            case "sul":   return "norte";
            case "leste": return "oeste";
            case "oeste": return "leste";
            case "subir": return "descer";
            case "descer": return "subir";
            default: return null;
        }
    }

    private static void carregarMapa() {
        // Cria o mapa
        Sala templomedelina1 = new Sala(1, "Templo de Medelina [Genora]", "Um grande edificio cheio de acólitos que atendem as pessoas feridas e cuidam de seus ferimentos. No fundo do salão é possivel encontrar uma estátua de Medelina, a deusa do amanhecer.");
        Sala ruaoestegenora1 = new Sala(2, "Travessa - Oeste [Genora]", "Uma rua larga que conecta o lado oeste da cidade ao centro. Ao leste fica a praça central e ao sul fica o Templo de Medelina.");
        Sala pracagenora = new Sala(3, "Praça Central [Genora]", "Uma grande praça com um chafariz no centro. É sempre movimentada durante o dia. À oeste fica o Templo e ao sul fica o salão de teleporte.");
        Sala ruasulgenora1 = new Sala(4, "Travessa - Sul [Genora]", "Rua que conecta o sul da cidade ao centro. Ao norte fica a praça central, ao leste fica o salão de teleporte e ao sul fica o quartel da cidade.");
        Sala teleportegenora = new Sala(5, "Salão de Teleporte [Genora]", "Um grande salão de pedra com um círculo de teleporte no centro. É possivel se deslocar entre outras cidades que também possuam círculo de telporte, independente da distância. A saída fica a oeste.");

        mapa.put(1, templomedelina1);
        mapa.put(2, ruaoestegenora1);
        mapa.put(3, pracagenora);
        mapa.put(4, ruasulgenora1);
        mapa.put(5, teleportegenora);

        // Conecta as salas
        conectarSalas(templomedelina1, "norte", ruaoestegenora1);
        conectarSalas(ruaoestegenora1, "leste", pracagenora);
        conectarSalas(pracagenora, "sul", ruasulgenora1);
        conectarSalas(ruasulgenora1, "leste", teleportegenora);

        // Cria e adiciona monstros
        Monstro goblinArqueiro = new Monstro("Goblin Arqueiro", 20, 5, 10);
        Monstro goblinGuerreiro = new Monstro("Goblin Guerreiro", 35, 8, 20);
        Monstro orcChefe = new Monstro("Orc Chefe", 80, 15, 50);

        pracagenora.adicionarMonstro(goblinArqueiro);
        pracagenora.adicionarMonstro(goblinGuerreiro);
        pracagenora.adicionarMonstro(orcChefe);

        // Loot table dos monstros
        goblinArqueiro.adicionarDrop("pocao", 50);
        goblinArqueiro.adicionarDrop("arco", 20);

        goblinGuerreiro.adicionarDrop("pocao", 50);
        goblinGuerreiro.adicionarDrop("espada", 20);
        
        orcChefe.adicionarDrop("armadura", 10);

        // Criar e adicionar item
        pracagenora.adicionarItem(ItemFactory.criarItem("espada"));
        pracagenora.adicionarItem(ItemFactory.criarItem("pocao"));
    }
}