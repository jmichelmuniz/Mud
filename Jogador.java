import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Jogador {
    private PersonagemDados dadosPersonagem;
    private final List<Item> inventario = Collections.synchronizedList(new ArrayList<>());
    
    private Item armaEquipada = null;
    private Item armaduraEquipada = null;

    public Jogador(PersonagemDados dadosPersonagem) {
        this.dadosPersonagem = dadosPersonagem;
    }

    // --- CARREGAMENTO DO ESTADO ---
    public void carregarEquipamentosEInventario() {
        // Restaura Equipamentos
        if (dadosPersonagem.armaEquipada != null) {
            this.armaEquipada = ItemFactory.criarItem(dadosPersonagem.armaEquipada);
        }
        if (dadosPersonagem.armaduraEquipada != null) {
            this.armaduraEquipada = ItemFactory.criarItem(dadosPersonagem.armaduraEquipada);
        }

        // Restaura Inventário
        List<String> itensSalvos = GerenciadorBD.carregarInventario(dadosPersonagem.nome);
        synchronized (inventario) {
            inventario.clear();
            for (String itemNome : itensSalvos) {
                Item item = ItemFactory.criarItem(itemNome);
                if (item != null) inventario.add(item);
            }
        }
    }

    // --- CÁLCULOS DE ATRIBUTOS E COMBATE ---
    public int getAtaqueMeleeTotal() {
        int ataque = dadosPersonagem.forca * 2;
        if (armaEquipada != null && armaEquipada.getTipo().equalsIgnoreCase("arma")) {
            ataque += armaEquipada.getDano();
        }
        return ataque;
    }

    public int getAtaqueRangedTotal() {
        int ataque = dadosPersonagem.pontaria * 2;
        if (armaEquipada != null && armaEquipada.getTipo().equalsIgnoreCase("arco")) {
            ataque += armaEquipada.getDano();
        }
        return ataque;
    }

    public int getDanoMagicoTotal() { 
        return dadosPersonagem.energia * 3; 
    }

    public int getDefesaTotal() {
        int defesa = dadosPersonagem.getDefesaBase();
        if (armaduraEquipada != null) defesa += armaduraEquipada.getDefesa();
        return defesa;
    }

    public int getAtaqueTotal() {
        if (armaEquipada != null && armaEquipada.getTipo().equalsIgnoreCase("arco")) {
            return getAtaqueRangedTotal();
        }
        return getAtaqueMeleeTotal();
    }

    // --- AÇÕES DO PERSONAGEM ---
    public String usarPocao(String nomeItem) {
        Item pocao = buscarItemInventario(nomeItem);
        if (pocao == null) {
            return "Você não possui o item '" + nomeItem + "'.";
        }

        if (pocao.getTipo().equalsIgnoreCase("pocao")) {
            if (dadosPersonagem.vidaAtual >= dadosPersonagem.getVidaMax()) {
                return "Sua vida já está cheia!";
            }

            int cura = 30;
            dadosPersonagem.vidaAtual = Math.min(dadosPersonagem.getVidaMax(), dadosPersonagem.vidaAtual + cura);
            inventario.remove(pocao);
            return "Você usou a " + pocao.getNome() + " e recuperou vida!\nVida atual: [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.getVidaMax() + "]";
        } else {
            return "O item '" + nomeItem + "' não pode ser consumido.";
        }
    }

    public String equiparItem(String nomeItem) {
        Item item = buscarItemInventario(nomeItem);
        if (item == null) {
            return "Você não possui o item '" + nomeItem + "' no inventário.";
        }

        if (item.getTipo().equalsIgnoreCase("arma") || item.getTipo().equalsIgnoreCase("arco")) {
            if (armaEquipada != null) inventario.add(armaEquipada);
            armaEquipada = item;
            inventario.remove(item);
            return "Você equipou " + item.getNome() + "!";
        } else if (item.getTipo().equalsIgnoreCase("armadura")) {
            if (armaduraEquipada != null) inventario.add(armaduraEquipada);
            armaduraEquipada = item;
            inventario.remove(item);
            return "Você equipou " + item.getNome() + "!";
        } else {
            return "Esse item não é um equipamento.";
        }
    }

    public String desequiparItem(String slot) {
        if (slot.equalsIgnoreCase("arma") && armaEquipada != null) {
            inventario.add(armaEquipada);
            String msg = "Você desequipou " + armaEquipada.getNome() + ".";
            armaEquipada = null;
            return msg;
        } else if (slot.equalsIgnoreCase("armadura") && armaduraEquipada != null) {
            inventario.add(armaduraEquipada);
            String msg = "Você desequipou " + armaduraEquipada.getNome() + ".";
            armaduraEquipada = null;
            return msg;
        } else {
            return "Nada para desequipar nesse slot.";
        }
    }

    public String ganharXp(int quantidade) {
        dadosPersonagem.xp += quantidade;
        StringBuilder sb = new StringBuilder();
        sb.append("Você ganhou ").append(quantidade).append(" de XP!\n");

        while (dadosPersonagem.xp >= dadosPersonagem.getXpNecessario()) {
            dadosPersonagem.xp -= dadosPersonagem.getXpNecessario();
            dadosPersonagem.nivel++;
            dadosPersonagem.forca += 2;
            dadosPersonagem.vitalidade += 2;
            dadosPersonagem.energia += 2;
            dadosPersonagem.pontaria += 2;
            dadosPersonagem.vidaAtual = dadosPersonagem.getVidaMax();
            dadosPersonagem.manaAtual = dadosPersonagem.getManaMax();

            sb.append("\n PARABÉNS! Você subiu para o Nível ").append(dadosPersonagem.nivel).append("!\n");
            sb.append(" Todos os seus atributos aumentaram (+2)!\n");
            sb.append(" Vida e Mana restaurados!\n");
        }
        return sb.toString();
    }

    public Item buscarItemInventario(String nome) {
        synchronized (inventario) {
            for (Item item : inventario) {
                if (item.getNome().equalsIgnoreCase(nome)) return item;
            }
        }
        return null;
    }

    public void adicionarAoInventario(Item item) {
        inventario.add(item);
    }

    public boolean removerDoInventario(Item item) {
        return inventario.remove(item);
    }

    public void salvarProgresso(int salaIdAtual) {
        if (dadosPersonagem != null) {
            dadosPersonagem.salaId = salaIdAtual;
            dadosPersonagem.armaEquipada = (armaEquipada != null) ? armaEquipada.getNome() : null;
            dadosPersonagem.armaduraEquipada = (armaduraEquipada != null) ? armaduraEquipada.getNome() : null;

            GerenciadorBD.salvarPersonagem(dadosPersonagem);
            GerenciadorBD.salvarInventario(dadosPersonagem.nome, inventario);
        }
    }

    // --- GETTERS E SETTERS ---
    public PersonagemDados getDados() { return dadosPersonagem; }
    public List<Item> getInventario() { return inventario; }
    public Item getArmaEquipada() { return armaEquipada; }
    public Item getArmaduraEquipada() { return armaduraEquipada; }
}