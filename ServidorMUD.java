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
        Sala vila = new Sala(1, "Centro da Vila", "Um local calmo com uma fonte no centro.");
        Sala floresta = new Sala(2, "Floresta Sombria", "Árvores altas bloqueiam a luz do sol.");

        mapa.put(1, vila);
        mapa.put(2, floresta);

        // Conecta as salas
        conectarSalas(vila, "norte", floresta);

        // Cria e adiciona monstros
        Monstro goblinArqueiro = new Monstro("Goblin Arqueiro", 20, 5, 10);
        Monstro goblinGuerreiro = new Monstro("Goblin Guerreiro", 35, 8, 20);
        Monstro orcChefe = new Monstro("Orc Chefe", 80, 15, 50);

        floresta.adicionarMonstro(goblinArqueiro);
        floresta.adicionarMonstro(goblinGuerreiro);
        floresta.adicionarMonstro(orcChefe);

        // Loot table dos monstros
        goblinArqueiro.adicionarDrop("pocao", 50);
        goblinArqueiro.adicionarDrop("arco", 20);

        goblinGuerreiro.adicionarDrop("pocao", 50);
        goblinGuerreiro.adicionarDrop("espada", 20);
        
        orcChefe.adicionarDrop("armadura", 10);

        // Criar e adicionar item
        vila.adicionarItem(ItemFactory.criarItem("espada"));
        vila.adicionarItem(ItemFactory.criarItem("pocao"));
    }
}