import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Sala {
    private int id;
    private String nome;
    private String descricao;
    private final List<Monstro> monstros = new ArrayList<>();
    private final Map<String, Sala> saidas = new HashMap<>();
    private final List<Item> itens = new ArrayList<>();
    private final List<ClientHandler> jogadoresNaSala = Collections.synchronizedList(new ArrayList<>());
    
    private MonstroModelo monstroModelo; 
    private boolean aguardandoRespawn = false;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static class MonstroModelo {
        String nome;
        int vidaMax;
        int ataque;
        int xpConcedido;

        MonstroModelo(String nome, int vidaMax, int ataque, int xpConcedido) {
            this.nome = nome;
            this.vidaMax = vidaMax;
            this.ataque = ataque;
            this.xpConcedido = xpConcedido;
        }
    }

    public void adicionarJogador(ClientHandler cliente) {
        jogadoresNaSala.add(cliente);
    }

    public void removerJogador(ClientHandler cliente) {
        jogadoresNaSala.remove(cliente);
    }

    public List<ClientHandler> getJogadores() {
        return jogadoresNaSala;
    }

    // Envia uma mensagem para todos os jogadores na sala, EXCETO o remetente
    public void notificarOutros(ClientHandler remetente, String mensagem) {
        synchronized (jogadoresNaSala) {
            for (ClientHandler cliente : jogadoresNaSala) {
                if (cliente != remetente) {
                    cliente.enviarMensagem(mensagem);
                }
            }
        }
    }

    public Sala(int id, String nome, String descricao) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
    }

    public void adicionarMonstro(Monstro monstro) {
        this.monstros.add(monstro);
    }

    public List<Monstro> getMonstros() {
        return monstros;
    }

    public Monstro buscarMonstro(String nome) {
        if (nome == null || nome.isBlank()) return null;

        String nomeBusca = nome.toLowerCase().trim();

        for (Monstro m : monstros) {
            if (m.estaVivo() && (m.getNome().equalsIgnoreCase(nomeBusca) || m.getNome().toLowerCase().contains(nomeBusca))) {
                return m; // Retorna o primeiro monstro vivo que corresponde ao nome
            }
        }
        return null; // Nenhum monstro vivo encontrado com esse nome
    }

    public void adicionarSaida(String direcao, Sala salaDestino) {
        saidas.put(direcao.toLowerCase(), salaDestino);
    }

    public Sala getSaida(String direcao) {
        return saidas.get(direcao.toLowerCase());
    }

    public Map<String, Sala> getSaidas() {
        return saidas;
    }

    public void adicionarItem(Item item) {
        if (item != null) {
            this.itens.add(item);
        }
    }

    public boolean removerItem(Item item) {
        return this.itens.remove(item);
    }

    public List<Item> getItens() {
        return itens;
    }

    // Busca um item pelo nome exato ou parcial no chão
    public Item buscarItem(String nome) {
        if (nome == null || nome.isBlank()) return null;
        String busca = nome.toLowerCase().trim();

        for (Item item : itens) {
            if (item.getNome().equalsIgnoreCase(busca) || item.getNome().toLowerCase().contains(busca)) {
                return item;
            }
        }
        return null;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }

    public void agendarRespawn(Monstro monstro, int segundos) {
        scheduler.schedule(() -> {
            monstro.ressuscitar();
        }, segundos, TimeUnit.SECONDS);
    }
}