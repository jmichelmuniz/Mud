public class Item {
    private final String nome;
    private final String descricao;
    private final String tipo; // "arma", "pocao", "utilitario"

    public Item(String nome, String descricao, String tipo) {
        this.nome = nome.toLowerCase().trim();
        this.descricao = descricao;
        this.tipo = tipo;
    }

    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public String getTipo() { return tipo; }
}