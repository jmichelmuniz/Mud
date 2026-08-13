// Classe simplificada para monstros/NPCs hostis
public class Monstro {
    private final String nome;
    private int vidaAtual;
    private final int vidaMax;
    private final int ataque;
    private final int xpConcedido;

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
    
    public void receberDano(int dano) {
        this.vidaAtual = Math.max(0, this.vidaAtual - dano);
    }

    public boolean estaVivo() {
        return this.vidaAtual > 0;
    }
}