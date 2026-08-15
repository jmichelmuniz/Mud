import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter escritor;
    private BufferedReader leitor;

    private Jogador jogador;
    private Sala salaAtual;

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
            escritor.println("\nBem-vindo ao MUD, " + jogador.getDados().nome + "!");
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
            if (salaAtual != null && jogador != null) {
            salaAtual.notificarOutros(this, "\n[-] " + jogador.getDados().nome + " desconectou.");
            salaAtual.removerJogador(this);
        }
            salvarProgresso();
            fecharConexao();
        }
    }

    private boolean realizarAutenticacao() throws IOException {
        escritor.println("==========================================");
        escritor.println("                 MUD v0.0.15              ");
        escritor.println("==========================================");
        escritor.print("Digite seu nome: ");
        escritor.flush();
        String nome = leitor.readLine();

        if (nome == null || nome.isBlank()) return false;

        PersonagemDados dados;
        if (GerenciadorBD.jogadorExiste(nome)) {
            escritor.print("Digite sua senha: ");
            escritor.flush();
            String senha = leitor.readLine();
            dados = GerenciadorBD.autenticarJogador(nome, senha);
            if (dados == null) {
                escritor.println("Senha incorreta! Conexão encerrada.");
                return false;
            }
        } else {
            escritor.println("Novo personagem! Digite uma senha para cadastrar:");
            String senha = leitor.readLine();
            dados = GerenciadorBD.cadastrarJogador(nome, senha);
        }

        this.jogador = new Jogador(dados);
        return true;
    }

    public void enviarMensagem(String mensagem) {
        if (escritor != null) {
            escritor.println(mensagem);
        }
    }

    private void carregarEstadoJogador() {
        this.salaAtual = ServidorMUD.mapa.getOrDefault(jogador.getDados().salaId, ServidorMUD.mapa.get(1));
        jogador.carregarEquipamentosEInventario();

        salaAtual.adicionarJogador(this);
        salaAtual.notificarOutros(this, "\n[+] " + jogador.getDados().nome + " entrou no mundo.");
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
            iniciarCombateCom(comandoLower.substring(7).trim());
        } else if (comandoLower.equals("fugir")) {
            processarFuga();
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
            escritor.println("\n   DESEQUIPAR        " + "'desequipar <slot>' desequipa o item equipado e guarda ele no inventario. eg.: 'desequipar armadura'; 'desequipar arma'");
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

        // Saidas
        if (!salaAtual.getSaidas().isEmpty()) {
            escritor.println("Saídas visíveis: " + String.join(", ", salaAtual.getSaidas().keySet()));
        } else {
            escritor.println("Não há saídas visíveis aqui.");
        }

        // --- OUTROS JOGADORES NA SALA ---
        List<ClientHandler> outrosJogadores = salaAtual.getJogadores();
        synchronized (outrosJogadores) {
            boolean temOutro = false;
            for (ClientHandler outro : outrosJogadores) {
                if (outro != this) {
                    if (!temOutro) {
                        escritor.println("\nOutros aventureiros presentes:");
                        temOutro = true;
                    }
                    escritor.println("- " + outro.getJogador().getDados().nome);
                }
            }
        }

        // Monstros
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
        PersonagemDados d = jogador.getDados();
        escritor.println("==========================================");
        escritor.println(" FICHA DE PERSONAGEM: " + d.nome);
        escritor.println(" Nível: " + d.nivel + " | XP: " + d.xp + "/" + d.getXpNecessario());
        escritor.println("------------------------------------------");
        escritor.println(" Vida: " + d.vidaAtual + "/" + d.getVidaMax());
        escritor.println(" Mana: " + d.manaAtual + "/" + d.getManaMax());
        escritor.println("------------------------------------------");
        escritor.println(" ATRIBUTOS:");
        escritor.println("  [FOR] Força:      " + d.forca + " (Dano Melee: " + jogador.getAtaqueMeleeTotal() + ")");
        escritor.println("  [VIT] Vitalidade: " + d.vitalidade + " (Defesa Base: " + d.getDefesaBase() + ")");
        escritor.println("  [ENE] Energia:    " + d.energia + " (Dano Mágico: " + jogador.getDanoMagicoTotal() + ")");
        escritor.println("  [PNT] Pontaria:   " + d.pontaria + " (Dano Distância: " + jogador.getAtaqueRangedTotal() + ")");
        escritor.println("------------------------------------------");
        escritor.println(" EQUIPAMENTOS:");
        escritor.println("  Arma:     " + (jogador.getArmaEquipada() != null ? jogador.getArmaEquipada().getNome() : "Nenhuma"));
        escritor.println("  Armadura: " + (jogador.getArmaduraEquipada() != null ? jogador.getArmaduraEquipada().getNome() : "Nenhuma"));
        escritor.println("  Defesa Total: " + jogador.getDefesaTotal());
        escritor.println("==========================================");
    }

    private void exibirInventario() {
        escritor.println("=== Seus Itens ===");
        List<Item> inv = jogador.getInventario();
        synchronized (inv) {
            if (inv.isEmpty()) {
                escritor.println("Seu inventário está vazio.");
            } else {
                for (Item item : inv) {
                    escritor.println("- " + item.getNome() + " (" + item.getTipo() + ")");
                }
            }
        }
    }

    private void usarPocao(String nomeItem) {
        String resultado = jogador.usarPocao(nomeItem);
        escritor.println(resultado);
        salvarProgresso();
    }

    private void equiparItem(String nomeItem) {
        String resultado = jogador.equiparItem(nomeItem);
        escritor.println(resultado);
        salvarProgresso();
    }

    private void desequiparItem(String slot) {
        String resultado = jogador.desequiparItem(slot);
        escritor.println(resultado);
        salvarProgresso();
    }

    private void iniciarCombateCom(String nomeAlvo) {
        Monstro alvo = salaAtual.buscarMonstro(nomeAlvo);
        if (alvo == null || !alvo.estaVivo()) {
            escritor.println("Não há nenhum inimigo vivo com esse nome aqui.");
            return;
        }

        jogador.setAlvoAtual(alvo);
        GerenciadorCombate.registrarCombate(this);
        escritor.println("\n[COMBATE INICIADO] Você está atacando " + alvo.getNome() + "!");
    }

    // Este método é chamado automaticamente a cada 2.5s pelo GerenciadorCombate
    public void processarTickAtaqueAuto() {
        Monstro alvo = jogador.getAlvoAtual();

        // Validações de encerramento de combate
        if (alvo == null || !alvo.estaVivo() || !jogador.estaEmCombate() || !salaAtual.getMonstros().contains(alvo)) {
            jogador.encerrarCombate();
            GerenciadorCombate.removerCombate(this);
            escritor.println("\n[COMBATE ENCERRADO]");
            return;
        }

        PersonagemDados d = jogador.getDados();

        // 1. Jogador ataca Monstro
        int danoCausado = jogador.getAtaqueTotal();
        alvo.receberDano(danoCausado);
        escritor.println("\n⚔️ Você atacou " + alvo.getNome() + " causando " + danoCausado + " de dano!");

        // 2. Verifica se o monstro morreu
        if (!alvo.estaVivo()) {
            escritor.println("💀 Você derrotou " + alvo.getNome() + "!");
            escritor.println(jogador.ganharXp(alvo.getXpConcedido()));
            
            for (Item item : alvo.gerarLoot()) {
                salaAtual.adicionarItem(item);
            }
            
            salaAtual.agendarRespawn(alvo, 15);
            jogador.encerrarCombate();
            GerenciadorCombate.removerCombate(this);
            salvarProgresso();
            return;
        }

        // 3. Monstro contra-ataca
        int danoSofrido = Math.max(1, alvo.getAtaque() - jogador.getDefesaTotal());
        d.vidaAtual = Math.max(0, d.vidaAtual - danoSofrido);
        escritor.println("💥 " + alvo.getNome() + " te atacou causando " + danoSofrido + " de dano!");
        escritor.println("   [HP: " + d.vidaAtual + "/" + d.getVidaMax() + " | " + alvo.getNome() + " HP: " + alvo.getVidaAtual() + "/" + alvo.getVidaMax() + "]");

        // 4. Morte do jogador
        if (d.vidaAtual == 0) {
            escritor.println("\n*** VOCÊ MORREU! ***");
            escritor.println("Ressuscitando no Templo...");
            d.vidaAtual = d.getVidaMax();
            this.salaAtual = ServidorMUD.mapa.get(1);
            jogador.encerrarCombate();
            GerenciadorCombate.removerCombate(this);
        }

        salvarProgresso();
    }

    private void processarFuga() {
        if (!jogador.estaEmCombate()) {
            escritor.println("Você não está em combate.");
            return;
        }

        jogador.encerrarCombate();
        GerenciadorCombate.removerCombate(this);
        escritor.println("Você se desengajou do combate!");
    }

    public void salvarProgresso() {
        if (jogador != null) {
            jogador.salvarProgresso(salaAtual.getId());
        }
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
        if (jogador.estaEmCombate()) {
            jogador.encerrarCombate();
            GerenciadorCombate.removerCombate(this);
            escritor.println("Você se desengajou do combate!");
        }

        String dirNormalizada = normalizarDirecao(direcao);
        Sala proximaSala = salaAtual.getSaida(dirNormalizada);

        if (proximaSala == null) {
            escritor.println("Você não pode ir para essa direção.");
            return;
        }

        // Avisa a sala antiga que o jogador saiu
        salaAtual.notificarOutros(this, "\n" + jogador.getDados().nome + " foi para o " + dirNormalizada + ".");
        salaAtual.removerJogador(this);

        // Entra na nova sala
        this.salaAtual = proximaSala;
        salaAtual.adicionarJogador(this);
        
        // Avisa a nova sala que o jogador chegou
        salaAtual.notificarOutros(this, "\n" + jogador.getDados().nome + " chegou na sala.");

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
        jogador.adicionarAoInventario(item);

        escritor.println("Você pegou: " + item.getNome());
        salvarProgresso();
    }

    private void soltarItem(String nomeItem) {
        if (nomeItem.isBlank()) {
            escritor.println("Soltar o quê?");
            return;
        }

        Item item = jogador.buscarItemInventario(nomeItem);
        if (item == null) {
            escritor.println("Você não tem '" + nomeItem + "' no seu inventário.");
            return;
        }

        // Move do inventário para a sala
        jogador.removerDoInventario(item);
        salaAtual.adicionarItem(item);

        escritor.println("Você soltou " + item.getNome() + " no chão.");
        salvarProgresso();
    }

    public Jogador getJogador() {
        return jogador;
    }

    private void fecharConexao() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar socket: " + e.getMessage());
        }
    }
}