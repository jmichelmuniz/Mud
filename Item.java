public class Item {
    private final String nome;
    private final String descricao;
    private final String tipo; // "arma", "pocao", "utilitario"
    private final int dano;
    private final int defesa;

    public Item(String nome, String descricao, String tipo, int dano, int defesa) {
        this.nome = nome.toLowerCase().trim();
        this.descricao = descricao;
        this.tipo = tipo;
        this.dano = dano;
        this.defesa = defesa;
    }

    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public String getTipo() { return tipo; }
    public int getDano() { return dano; }
    public int getDefesa() { return defesa; }
}