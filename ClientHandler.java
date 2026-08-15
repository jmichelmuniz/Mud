import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter escritor;
    private BufferedReader leitor;
    
    private PersonagemDados dadosPersonagem;
    private Sala salaAtual;
    private final List<Item> inventario = Collections.synchronizedList(new ArrayList<>());
    
    private Item armaEquipada = null;
    private Item armaduraEquipada = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            escritor = new PrintWriter(socket.getOutputStream(), true);
            leitor = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            if (!realizarAutenticacao()) {
                return;
            }

            carregarEstadoJogador();
            escritor.println("\nBem-vindo ao MUD, " + dadosPersonagem.nome + "!");
            processarOlhar();

            String linha;
            while ((linha = leitor.readLine()) != null) {
                String comando = linha.trim();
                if (comando.equalsIgnoreCase("sair")) {
                    escritor.println("Até a próxima aventura!");
                    break;
                }
                processarComando(comando);
            }

        } catch (IOException e) {
            System.out.println("[Conexão] Jogador desconectado abruptamente.");
        } finally {
            salvarProgresso();
            fecharConexao();
        }
    }

    private boolean realizarAutenticacao() throws IOException {
        escritor.println("==========================================");
        escritor.println("                 MUD v0.0.12              ");
        escritor.println("==========================================");
        escritor.print("Digite seu nome: ");
        escritor.flush();
        String nome = leitor.readLine();

        if (nome == null || nome.isBlank()) return false;

        if (GerenciadorBD.jogadorExiste(nome)) {
            escritor.print("Digite sua senha: ");
            escritor.flush();
            String senha = leitor.readLine();
            dadosPersonagem = GerenciadorBD.autenticarJogador(nome, senha);
            if (dadosPersonagem == null) {
                escritor.println("Senha incorreta! Conexão encerrada.");
                return false;
            }
        } else {
            escritor.println("Novo personagem! Digite uma senha para cadastrar:");
            String senha = leitor.readLine();
            dadosPersonagem = GerenciadorBD.cadastrarJogador(nome, senha);
        }
        return true;
    }

    private void carregarEstadoJogador() {
        this.salaAtual = ServidorMUD.mapa.getOrDefault(dadosPersonagem.salaId, ServidorMUD.mapa.get(1));
        
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

    private void processarComando(String comando) {
        String comandoLower = comando.toLowerCase();

        if (comandoLower.equals("olhar")) {
            processarOlhar();
        } else if (comandoLower.equals("inventario") || comandoLower.equals("inv")) {
            exibirInventario();
        } else if (comandoLower.equals("ficha") || comandoLower.equals("status")) {
            exibirFicha();
        } else if (comandoLower.startsWith("usar ")) {
            usarPocao(comandoLower.substring(5).trim());
        } else if (comandoLower.startsWith("equipar ")) {
            equiparItem(comandoLower.substring(8).trim());
        } else if (comandoLower.startsWith("desequipar ")) {
            desequiparItem(comandoLower.substring(11).trim());
        } else if (comandoLower.startsWith("atacar ")) {
            atacarMonstro(comandoLower.substring(7).trim());
        } else if (comandoLower.startsWith("ir ")) {
            mover(comandoLower.substring(3).trim());
        } else if (isDirecaoValida(comandoLower)) {
            mover(comandoLower);
        } else if (comandoLower.startsWith("pegar ")) {
            pegarItem(comandoLower.substring(6).trim());
        } else if (comandoLower.startsWith("soltar ") || comandoLower.startsWith("largar ")) {
            soltarItem(comandoLower.substring(7).trim());
        } else if (comandoLower.equals("comandos") || comandoLower.equals("help")) {
            escritor.println("      Comando        " + "   Como funciona");
            escritor.println("\n   OLHAR             " + "Mostra a descrição completa da sala em que se encontra, incluindo saídas, jogadores, monstros e itens na sala.");
            escritor.println("\n   INVENTARIO / INV  " + "Mostra todos os itens que estão no seu inventário");
            escritor.println("\n   FICHA / STATUS    " + "Mostra a ficha do personagem, com informações como nivel, xp, atributos e equipamentos");
            escritor.println("\n   USAR              " + "'usar <item>' utiliza o item se ele for um consumível. eg.: 'usar pocao'");
            escritor.println("\n   EQUIPAR           " + "'equipar <arma ou armadura>' equipa o item escolhido se ele estiver no seu inventario. eg.: 'equipar espada'");
            escritor.println("\n   DESEQUIPAR        " + "'desequipar <arma ou armadura>' desequipa o item equipado e guarda ele no inventario. eg.: 'desequipar armadura'");
            escritor.println("\n   ATACAR            " + "'atacar <monstro>' ataca o monstro, e se ele sobreviver recebe um contra ataque do mesmo. eg.: 'atacar goblin'");
            escritor.println("\n   IR                " + "'ir <direção>' é o comando basico de movimento entre as salas. Pode ser utilizado sem o 'ir', apenas com a direção, ou apenas a inicial da direção. eg.: 'ir norte'; 'sul'; 'l'");
            escritor.println("\n   PEGAR             " + "'pegar <item>' pega o item que está na sala e guarda no inventario. eg.: 'pegar pocao'");
            escritor.println("\n   SOLTAR / LARGAR   " + "'soltar <item>' solta o item do inventario do jogador para a sala atual. eg.: 'largar espada'");
        } else {
            escritor.println("Comando não reconhecido. Para uma lista de comandos disponiveis digite 'comandos' ou 'help'.");
        }
    }

    private void processarOlhar() {
        escritor.println("\n[" + salaAtual.getNome() + "]");
        escritor.println(salaAtual.getDescricao());

        if (!salaAtual.getSaidas().isEmpty()) {
            escritor.println("Saídas visíveis: " + String.join(", ", salaAtual.getSaidas().keySet()));
        } else {
            escritor.println("Não há saídas visíveis aqui.");
        }

        List<Monstro> monstrosNaSala = salaAtual.getMonstros();
        boolean temInimigoVivo = false;

        for (Monstro m : monstrosNaSala) {
            if (m.estaVivo()) {
                if (!temInimigoVivo) {
                    escritor.println("\nInimigos presentes:");
                    temInimigoVivo = true;
                }
                escritor.println("- " + m.getNome() + " [HP: " + m.getVidaAtual() + "/" + m.getVidaMax() + "]");
            }
        }

        List<Item> itensNoChao = salaAtual.getItens();
        if (!itensNoChao.isEmpty()) {
            escritor.println("\nNo chão você vê:");
            for (Item item : itensNoChao) {
                escritor.println("  - Um(a) " + item.getNome() + " (" + item.getTipo() + ")");
            }
        }
    }

    private void exibirFicha() {
        escritor.println("==========================================");
        escritor.println(" FICHA DE PERSONAGEM: " + dadosPersonagem.nome);
        escritor.println(" Nível: " + dadosPersonagem.nivel + " | XP: " + dadosPersonagem.xp + "/" + dadosPersonagem.getXpNecessario());
        escritor.println("------------------------------------------");
        escritor.println(" Vida: " + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.getVidaMax());
        escritor.println(" Mana: " + dadosPersonagem.manaAtual + "/" + dadosPersonagem.getManaMax());
        escritor.println("------------------------------------------");
        escritor.println(" ATRIBUTOS:");
        escritor.println("  [FOR] Força:      " + dadosPersonagem.forca + " (Dano Melee: " + getAtaqueMeleeTotal() + ")");
        escritor.println("  [VIT] Vitalidade: " + dadosPersonagem.vitalidade + " (Defesa Base: " + dadosPersonagem.getDefesaBase() + ")");
        escritor.println("  [ENE] Energia:    " + dadosPersonagem.energia + " (Dano Mágico: " + getDanoMagicoTotal() + ")");
        escritor.println("  [PNT] Pontaria:   " + dadosPersonagem.pontaria + " (Dano Distância: " + getAtaqueRangedTotal() + ")");
        escritor.println("------------------------------------------");
        escritor.println(" EQUIPAMENTOS:");
        escritor.println("  Arma:     " + (armaEquipada != null ? armaEquipada.getNome() : "Nenhuma"));
        escritor.println("  Armadura: " + (armaduraEquipada != null ? armaduraEquipada.getNome() : "Nenhuma"));
        escritor.println("  Defesa Total: " + getDefesaTotal());
        escritor.println("==========================================");
    }

    private void exibirInventario() {
        escritor.println("=== Seus Itens ===");
        synchronized (inventario) {
            if (inventario.isEmpty()) {
                escritor.println("Seu inventário está vazio.");
            } else {
                for (Item item : inventario) {
                    escritor.println("- " + item.getNome() + " (" + item.getTipo() + ")");
                }
            }
        }
    }

    private void usarPocao(String nomeItem) {
        Item pocao = buscarItemInventario(nomeItem);
        if (pocao == null) {
            escritor.println("Você não possui o item '" + nomeItem + "'.");
            return;
        }

        if (pocao.getTipo().equalsIgnoreCase("pocao")) {
            if (dadosPersonagem.vidaAtual >= dadosPersonagem.getVidaMax()) {
                escritor.println("Sua vida já está cheia!");
                return;
            }

            int cura = 30;
            dadosPersonagem.vidaAtual = Math.min(dadosPersonagem.getVidaMax(), dadosPersonagem.vidaAtual + cura);
            inventario.remove(pocao);
            escritor.println("Você usou a " + pocao.getNome() + " e recuperou vida!");
            escritor.println("Vida atual: [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.getVidaMax() + "]");
            salvarProgresso();
        } else {
            escritor.println("O item '" + nomeItem + "' não pode ser consumido.");
        }
    }

    private void equiparItem(String nomeItem) {
        Item item = buscarItemInventario(nomeItem);
        if (item == null) {
            escritor.println("Você não possui o item '" + nomeItem + "' no inventário.");
            return;
        }

        if (item.getTipo().equalsIgnoreCase("arma") || item.getTipo().equalsIgnoreCase("arco")) {
            if (armaEquipada != null) inventario.add(armaEquipada);
            armaEquipada = item;
            inventario.remove(item);
            escritor.println("Você equipou " + item.getNome() + "!");
        } else if (item.getTipo().equalsIgnoreCase("armadura")) {
            if (armaduraEquipada != null) inventario.add(armaduraEquipada);
            armaduraEquipada = item;
            inventario.remove(item);
            escritor.println("Você equipou " + item.getNome() + "!");
        } else {
            escritor.println("Esse item não é um equipamento.");
        }
        salvarProgresso();
    }

    private void desequiparItem(String slot) {
        if (slot.equalsIgnoreCase("arma") && armaEquipada != null) {
            inventario.add(armaEquipada);
            escritor.println("Você desequipou " + armaEquipada.getNome() + ".");
            armaEquipada = null;
        } else if (slot.equalsIgnoreCase("armadura") && armaduraEquipada != null) {
            inventario.add(armaduraEquipada);
            escritor.println("Você desequipou " + armaduraEquipada.getNome() + ".");
            armaduraEquipada = null;
        } else {
            escritor.println("Nada para desequipar nesse slot.");
        }
        salvarProgresso();
    }

    private void atacarMonstro(String nomeAlvo) {
        Monstro alvo = salaAtual.buscarMonstro(nomeAlvo);
        if (alvo == null || !alvo.estaVivo()) {
            escritor.println("\n Não há nenhum inimigo vivo com esse nome.");
            return;
        }

        int danoCausado = (armaEquipada != null && armaEquipada.getTipo().equalsIgnoreCase("arco")) ?
                getAtaqueRangedTotal() : getAtaqueMeleeTotal();

        alvo.receberDano(danoCausado);
        escritor.println("\n Você atacou " + alvo.getNome() + " causando " + danoCausado + " de dano!");

        if (alvo.estaVivo()) {
            int danoSofrido = Math.max(1, alvo.getAtaque() - getDefesaTotal());
            dadosPersonagem.vidaAtual = Math.max(0, dadosPersonagem.vidaAtual - danoSofrido);
            escritor.println(alvo.getNome() + " te atacou causando " + danoSofrido + " de dano!");
            escritor.println(alvo.getNome() + " [HP: " + alvo.getVidaAtual() + "/" + alvo.getVidaMax() + "]");
            escritor.println(dadosPersonagem.nome + " [HP: " + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.getVidaMax() + "]");

            if (dadosPersonagem.vidaAtual == 0) {
                escritor.println("\n*** VOÇÊ MORREU! ***");
                escritor.println("Ressuscitando no Templo...");
                dadosPersonagem.vidaAtual = dadosPersonagem.getVidaMax();
                this.salaAtual = ServidorMUD.mapa.get(1);
            }
        } else {
            escritor.println("Você derrotou " + alvo.getNome() + "!");
            ganharXp(alvo.getXpConcedido());

            List<Item> drops = alvo.gerarLoot();
            if (!drops.isEmpty()) {
                escritor.println("\nO monstro deixou cair no chão:");
                for (Item item : drops) {
                    salaAtual.adicionarItem(item);
                    escritor.println("  - " + item.getNome());
                }
            }

            salaAtual.agendarRespawn(alvo, 15);
        }
        salvarProgresso();
    }

    public void ganharXp(int quantidade) {
        dadosPersonagem.xp += quantidade;
        escritor.println("Você ganhou " + quantidade + " de XP!");

        while (dadosPersonagem.xp >= dadosPersonagem.getXpNecessario()) {
            dadosPersonagem.xp -= dadosPersonagem.getXpNecessario();
            dadosPersonagem.nivel++;
            dadosPersonagem.forca += 2;
            dadosPersonagem.vitalidade += 2;
            dadosPersonagem.energia += 2;
            dadosPersonagem.pontaria += 2;
            dadosPersonagem.vidaAtual = dadosPersonagem.getVidaMax();
            dadosPersonagem.manaAtual = dadosPersonagem.getManaMax();

            escritor.println("\n PARABÉNS! Você subiu para o Nível " + dadosPersonagem.nivel + "!");
            escritor.println(" Todos os seus atributos aumentaram (+2)!\n");
            escritor.println(" Vida e Mana restaurados!\n");
        }
    }

    public void salvarProgresso() {
        if (dadosPersonagem != null) {
            dadosPersonagem.salaId = salaAtual.getId();
            dadosPersonagem.armaEquipada = (armaEquipada != null) ? armaEquipada.getNome() : null;
            dadosPersonagem.armaduraEquipada = (armaduraEquipada != null) ? armaduraEquipada.getNome() : null;

            GerenciadorBD.salvarPersonagem(dadosPersonagem);
            GerenciadorBD.salvarInventario(dadosPersonagem.nome, inventario);
        }
    }

    private Item buscarItemInventario(String nome) {
        synchronized (inventario) {
            for (Item item : inventario) {
                if (item.getNome().equalsIgnoreCase(nome)) return item;
            }
        }
        return null;
    }

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

    public int getDanoMagicoTotal() { return dadosPersonagem.energia * 3; }
    
    public int getDefesaTotal() {
        int defesa = dadosPersonagem.getDefesaBase();
        if (armaduraEquipada != null) defesa += armaduraEquipada.getDefesa();
        return defesa;
    }

    private boolean isDirecaoValida(String cmd) {
        return cmd.equals("norte") || cmd.equals("n") ||
            cmd.equals("sul")   || cmd.equals("s") ||
            cmd.equals("leste") || cmd.equals("l") ||
            cmd.equals("oeste") || cmd.equals("o") ||
            cmd.equals("subir") || cmd.equals("descer");
    }

    private String normalizarDirecao(String dir) {
        switch (dir) {
            case "n": return "norte";
            case "s": return "sul";
            case "l": return "leste";
            case "o": return "oeste";
            default:  return dir;
        }
    }

    private void mover(String direcao) {
        String dirNormalizada = normalizarDirecao(direcao);
        Sala proximaSala = salaAtual.getSaida(dirNormalizada);

        if (proximaSala == null) {
            escritor.println("Você não pode ir para essa direção.");
            return;
        }

        // Atualiza a posição do jogador
        this.salaAtual = proximaSala;
        escritor.println("Você foi para o " + dirNormalizada + ".\n");

        // Exibe automaticamente as informações da nova sala
        processarOlhar();
    }

    private void pegarItem(String nomeItem) {
        if (nomeItem.isBlank()) {
            escritor.println("Pegar o quê?");
            return;
        }

        Item item = salaAtual.buscarItem(nomeItem);

        if (item == null) {
            escritor.println("Não há nenhum '" + nomeItem + "' no chão.");
            return;
        }

        // Move da sala para o inventário
        salaAtual.removerItem(item);
        inventario.add(item);

        escritor.println("Você pegou: " + item.getNome());
        salvarProgresso();
    }

    private void soltarItem(String nomeItem) {
        if (nomeItem.isBlank()) {
            escritor.println("Soltar o quê?");
            return;
        }

        Item item = buscarItemInventario(nomeItem);

        if (item == null) {
            escritor.println("Você não tem '" + nomeItem + "' no seu inventário.");
            return;
        }

        // Move do inventário para a sala
        inventario.remove(item);
        salaAtual.adicionarItem(item);

        escritor.println("Você soltou " + item.getNome() + " no chão.");
        salvarProgresso();
    }

    private void fecharConexao() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar socket: " + e.getMessage());
        }
    }
}