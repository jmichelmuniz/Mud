public class PersonagemDados {
    public String nome;
    public int nivel;
    public int xp;
    public int vidaAtual;
    public int manaAtual;
    public int forca;
    public int vitalidade;
    public int energia;
    public int pontaria;
    public int salaId;
    public String armaEquipada;
    public String armaduraEquipada;

    public PersonagemDados(String nome, int nivel, int xp, int vidaAtual, int manaAtual, 
                           int forca, int vitalidade, int energia, int pontaria, 
                           int salaId, String armaEquipada, String armaduraEquipada) {
        this.nome = nome;
        this.nivel = nivel;
        this.xp = xp;
        this.vidaAtual = vidaAtual;
        this.manaAtual = manaAtual;
        this.forca = forca;
        this.vitalidade = vitalidade;
        this.energia = energia;
        this.pontaria = pontaria;
        this.salaId = salaId;
        this.armaEquipada = armaEquipada;
        this.armaduraEquipada = armaduraEquipada;
    }

    public int getVidaMax() { return 80 + (vitalidade * 12); }
    public int getManaMax() { return 30 + (energia * 10); }
    public int getDefesaBase() { return vitalidade / 2; }
    public int getXpNecessario() { return nivel * 100; }
}