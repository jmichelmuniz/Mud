public class DropItem {
    private String nomeItem;
    private double chancePorcentagem; // Ex: 50.0 para 50%, 10.0 para 10%

    public DropItem(String nomeItem, double chancePorcentagem) {
        this.nomeItem = nomeItem;
        this.chancePorcentagem = chancePorcentagem;
    }

    public String getNomeItem() { return nomeItem; }
    public double getChancePorcentagem() { return chancePorcentagem; }
}