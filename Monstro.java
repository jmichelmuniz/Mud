import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Classe simplificada para monstros/NPCs hostis
public class Monstro {
    private final String nome;
    private int vidaAtual;
    private final int vidaMax;
    private final int ataque;
    private final int xpConcedido;

    private final List<DropItem> tabelaDrop = new ArrayList<>();
    private static final Random random = new Random();

    public Monstro(String nome, int vidaMax, int ataque, int xpConcedido) {
        this.nome = nome;
        this.vidaMax = vidaMax;
        this.vidaAtual = vidaMax;
        this.ataque = ataque;
        this.xpConcedido = xpConcedido;
    }

    public String getNome() { return nome; }
    public int getVidaAtual() { return vidaAtual; }
    public int getVidaMax() { return vidaMax; }
    public int getAtaque() { return ataque; }
    public int getXpConcedido() { return xpConcedido; }

    // Adiciona um item possível na tabela de drops
    public void adicionarDrop(String nomeItem, double chancePorcentagem) {
        this.tabelaDrop.add(new DropItem(nomeItem, chancePorcentagem));
    }

    // Sorteia quais itens caíram após a morte
    public List<Item> gerarLoot() {
        List<Item> itensDropados = new ArrayList<>();

        for (DropItem drop : tabelaDrop) {
            double sorteio = random.nextDouble() * 100.0; // Gera de 0.0 a 100.0
            if (sorteio <= drop.getChancePorcentagem()) {
                Item itemCriado = ItemFactory.criarItem(drop.getNomeItem());
                if (itemCriado != null) {
                    itensDropados.add(itemCriado);
                }
            }
        }
        return itensDropados;
    }
    
    public void receberDano(int dano) {
        this.vidaAtual = Math.max(0, this.vidaAtual - dano);
    }

    public boolean estaVivo() {
        return this.vidaAtual > 0;
    }

    public void ressuscitar() {
        this.vidaAtual = this.vidaMax;
    }
}