import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServidorMUD {
    private static final int PORTA = 4000;
    private static final Map<Integer, Sala> mapa = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Criando o mundo do MUD...");
        inicializarMapa();

        GerenciadorBD.inicializarBanco();

        System.out.println("Iniciando o servidor na porta " + PORTA + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            System.out.println("Servidor online! Aguardando aventureiros...");

            while (true) {
                Socket socketClient = serverSocket.accept();
                System.out.println("Nova conexão de: " + socketClient.getRemoteSocketAddress());

                // Todos os jogadores começam na sala ID 1 (Templo)
                Sala salaInicial = mapa.get(1);
                ClientHandler novoJogador = new ClientHandler(socketClient, salaInicial);
                new Thread(novoJogador).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    // Instancia as salas e cria as conexões entre elas
    private static void inicializarMapa() {
        Sala templo = new Sala(1, "Templo de Pedra", "Um templo antigo e seguro. Tochas iluminam as paredes de pedra. Há uma saída para o NORTE.");
        Sala praca = new Sala(2, "Praça Central", "Uma praça movimentada da cidade. Ao SUL fica o Templo e ao LESTE há uma floresta escura.");
        Sala floresta = new Sala(3, "Floresta Sombria", "Árvores altas bloqueiam a luz do sol. Ruídos estranhos vêm do mato. A praça fica a OESTE.");

        // Configura as saídas (Direção -> Destino)
        templo.adicionarSaida("norte", praca);
        
        praca.adicionarSaida("sul", templo);
        praca.adicionarSaida("leste", floresta);
        
        floresta.adicionarSaida("oeste", praca);

        // Guarda as salas no mapa global do servidor
        mapa.put(templo.getId(), templo);
        mapa.put(praca.getId(), praca);
        mapa.put(floresta.getId(), floresta);

        // Criando monstros
        Monstro goblin = new Monstro("Goblin", 15, 3, 10);
        Monstro lobo = new Monstro("Lobo", 25, 5, 20);

        // Adicionando na Floresta
        floresta.adicionarMonstro(goblin);
        floresta.adicionarMonstro(lobo);

        // Itens criados
        Item espada = new Item("espada", "Uma espada simples.", "arma", 5, 0);
        Item pocao = new Item("pocao", "Um pequeno frasco com um liquido vermelho", "pocao", 0, 0);
        Item armadura = new Item("armadura", "Uma armadura leve que protege um pouco o corpo", "armadura", 0, 3);

        // Adicionando itens iniciais na sala
        templo.adicionarItem(pocao);
        floresta.adicionarItem(espada);
        templo.adicionarItem(armadura);
    }

    // Classe que representa uma Sala do jogo
    private static class Sala {
        private final int id;
        private final String nome;
        private final String descricao;
        private final Map<String, Sala> saidas = new HashMap<>();
        private final Set<ClientHandler> jogadoresNaSala = Collections.synchronizedSet(new HashSet<>());
        private final List<Monstro> monstrosNaSala = Collections.synchronizedList(new ArrayList<>());
        private final List<Item> itensNaSala = Collections.synchronizedList(new ArrayList<>());

        public Sala(int id, String nome, String descricao) {
            this.id = id;
            this.nome = nome;
            this.descricao = descricao;
        }

        public int getId() { return id; }
        public String getNome() { return nome; }
        
        public void adicionarSaida(String direcao, Sala salaDestino) {
            saidas.put(direcao.toLowerCase(), salaDestino);
        }

        public Sala getSaida(String direcao) {
            return saidas.get(direcao.toLowerCase());
        }

        public void adicionarJogador(ClientHandler jogador) {
            jogadoresNaSala.add(jogador);
        }

        public void removerJogador(ClientHandler jogador) {
            jogadoresNaSala.remove(jogador);
        }

        public void adicionarItem(Item item) {
            itensNaSala.add(item);
        }

        public Item removerItem(String nomeItem) {
            synchronized (itensNaSala) {
                for (Item item : itensNaSala) {
                    if (item.getNome().equalsIgnoreCase(nomeItem)) {
                        itensNaSala.remove(item);
                        return item; // Remove e retorna o primeiro item correspondente encontrado
                    }
                }
            }
            return null;
        }

        public Item buscarItem(String nomeItem) {
            synchronized (itensNaSala) {
                for (Item item : itensNaSala) {
                    if (item.getNome().equalsIgnoreCase(nomeItem)) {
                        return item;
                    }
                }
            }
            return null;
        }

        public void adicionarMonstro(Monstro monstro) {
            monstrosNaSala.add(monstro);
        }

        public Monstro buscarMonstro(String nome) {
            synchronized (monstrosNaSala) {
                for (Monstro m : monstrosNaSala) {
                    if (m.getNome().equalsIgnoreCase(nome) && m.estaVivo()) {
                        return m;
                    }
                }
            }
            return null;
        }

        public void removerMonstro(Monstro monstro) {
            monstrosNaSala.remove(monstro);
        }

        // Envia mensagem apenas para quem está nesta sala
        public void transmitirParaSala(String mensagem, ClientHandler remetente) {
            synchronized (jogadoresNaSala) {
                for (ClientHandler jogador : jogadoresNaSala) {
                    if (jogador != remetente) {
                        jogador.enviarMensagem(mensagem);
                    }
                }
            }
        }

        // Retorna o texto descritivo da sala e suas saídas visíveis
        public String obterDescricaoCompleta(ClientHandler leitor) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== ").append(nome).append(" ===\n");
            sb.append(descricao).append("\n");

            // --- LISTA DE JOGADORES ---
            List<String> outrosJogadores = new java.util.ArrayList<>();
            synchronized (jogadoresNaSala) {
                for (ClientHandler jogador : jogadoresNaSala) {
                    if (jogador != leitor && jogador.nomeJogador != null) {
                        outrosJogadores.add(jogador.nomeJogador);
                    }
                }
            }

            if (!outrosJogadores.isEmpty()) {
                sb.append("Jogadores presentes: ").append(String.join(", ", outrosJogadores)).append("\n");
            }

            // Lista de itens
            if (!itensNaSala.isEmpty()) {
                // Agrupa e conta a quantidade de itens por nome
                Map<String, Long> contagemItens;
                synchronized (itensNaSala) {
                    contagemItens = itensNaSala.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                            Item::getNome, 
                            java.util.stream.Collectors.counting()
                        ));
                }

                List<String> exibicaoItens = new java.util.ArrayList<>();
                for (Map.Entry<String, Long> entry : contagemItens.entrySet()) {
                    if (entry.getValue() > 1) {
                        exibicaoItens.add(entry.getKey() + " (x" + entry.getValue() + ")");
                    } else {
                        exibicaoItens.add(entry.getKey());
                    }
                }

                sb.append("No chão você vê: ").append(String.join(", ", exibicaoItens)).append("\n");
            }

            // Lista de Monstros
            if (!monstrosNaSala.isEmpty()) {
                List<String> nomesMonstros = new ArrayList<>();
                synchronized (monstrosNaSala) {
                    for (Monstro m : monstrosNaSala) {
                        if (m.estaVivo()) {
                            nomesMonstros.add(m.getNome() + " [HP: " + m.getVidaAtual() + "/" + m.getVidaMax() + "]");
                        }
                    }
                }
                if (!nomesMonstros.isEmpty()) {
                    sb.append("Inimigos aqui: ").append(String.join(", ", nomesMonstros)).append("\n");
                }
            }

            // Saídas
            sb.append("[Saídas visíveis: ");
            if (saidas.isEmpty()) {
                sb.append("nenhuma");
            } else {
                sb.append(String.join(", ", saidas.keySet()));
            }

            sb.append("]");
            return sb.toString();
        }
    }

    // Gerenciador de cada jogador conectado
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final java.util.List<Item> inventario = Collections.synchronizedList(new java.util.ArrayList<>());
        private PrintWriter escritor;
        private BufferedReader leitor;
        private String nomeJogador;
        private Sala salaAtual;
        private Item armaEquipada = null;
        private Item armaduraEquipada = null;

        public ClientHandler(Socket socket, Sala salaInicial) {
            this.socket = socket;
            this.salaAtual = salaInicial;
        }

        private GerenciadorBD.PersonagemDados dadosPersonagem;

        @Override
        public void run() {
            try {
                leitor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                escritor = new PrintWriter(socket.getOutputStream(), true);

                escritor.println("==========================================");
                escritor.println("                 MUD v0.0.7               ");
                escritor.println("==========================================");
                escritor.print("Digite o seu nome: ");
                escritor.flush();
                
                nomeJogador = leitor.readLine();
                if (nomeJogador == null || nomeJogador.trim().isEmpty()) {
                    nomeJogador = "Aventureiro";
                }
                nomeJogador = nomeJogador.trim();

                // Carrega o banco de dados
                boolean existe = GerenciadorBD.jogadorExiste(nomeJogador);

                // Senha
                if (existe) {
                    escritor.print("Sua conta foi encontrada. Digite sua senha: ");
                    escritor.flush();
                    String senha = leitor.readLine();

                    dadosPersonagem = GerenciadorBD.autenticarJogador(nomeJogador, senha);
                    if (dadosPersonagem == null) {
                        escritor.println("\n[Erro] Senha incorreta ou inválida! Conexão encerrada.");
                        return;
                    }
                    escritor.println("\nBem-vindo de volta, " + nomeJogador + "!");
                } else {
                    escritor.print("Novo personagem detectado! Crie uma senha para sua conta: ");
                    escritor.flush();
                    String novaSenha = leitor.readLine();
                    
                    if (novaSenha == null || novaSenha.trim().length() < 4) {
                        escritor.println("\nA senha precisa ter pelo menos 4 caracteres. Conexão encerrada.");
                        return;
                    }

                    dadosPersonagem = GerenciadorBD.cadastrarJogador(nomeJogador, novaSenha);
                    escritor.println("\nConta criada com sucesso! Aproveite o jogo, " + nomeJogador + ".");
                }

                // Carrega os itens equipados
                if (dadosPersonagem != null) {
                    // RESTAURAR ARMA EQUIPADA
                    if (dadosPersonagem.armaEquipada != null) {
                        // Recria o objeto Item correspondente ao nome salvo
                        this.armaEquipada = criarItemPorNome(dadosPersonagem.armaEquipada);
                    }

                    // RESTAURAR ARMADURA EQUIPADA
                    if (dadosPersonagem.armaduraEquipada != null) {
                        this.armaduraEquipada = criarItemPorNome(dadosPersonagem.armaduraEquipada);
                    }
                }

                // Carrega os nomes dos itens do banco e reconstrói os objetos Item
                java.util.List<String> nomesItensSalvos = GerenciadorBD.carregarInventario(nomeJogador);
                for (String nomeItem : nomesItensSalvos) {
                    // Um mini-banco de dados estático de itens para reconstruir o objeto (exemplo simples)
                    if (nomeItem.equals("espada")) inventario.add(new Item("espada", "Uma espada de ferro.", "arma", 5, 0));
                    if (nomeItem.equals("pocao")) inventario.add(new Item("pocao", "Uma poção de vida.", "pocao", 0, 0));
                }

                // Posiciona na sala recuperada do banco (se a sala existir no mapa)
                Sala salaSalva = mapa.get(dadosPersonagem.salaId);
                if (salaSalva != null) {
                    salaAtual = salaSalva;
                }

                salaAtual.adicionarJogador(this);

                escritor.println("\nBem-vindo de volta, " + nomeJogador + "!");
                escritor.println("Atributos: Vida [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.vidaMax + "]");

                salaAtual.transmitirParaSala("[" + nomeJogador + " materializou-se na sala.]", this);
                escritor.println(salaAtual.obterDescricaoCompleta(this));
                exibirPrompt();

                String linhaComando;
                while ((linhaComando = leitor.readLine()) != null) {
                    linhaComando = linhaComando.trim();
                    if (linhaComando.equalsIgnoreCase("sair")) {
                        escritor.println("Até logo!");
                        break;
                    }
                    processarComando(linhaComando);
                }

            } catch (IOException e) {
                System.out.println("Conexão perdida com " + nomeJogador);
            } finally {
                if (dadosPersonagem != null) {
                    salvarProgresso(); // Salva vida atual, sala atual, inventario e equipamentos
                }
                salaAtual.transmitirParaSala("[" + nomeJogador + " desconectou.]", this);
                salaAtual.removerJogador(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void processarComando(String comando) {
            if (comando.isEmpty()) return;

            String comandoLower = comando.toLowerCase();

            // Comando OLHAR
            if (comandoLower.equals("olhar")) {
                escritor.println(salaAtual.obterDescricaoCompleta(this));
            } 

            // Comandos de MOVIMENTAÇÃO (norte, sul, leste, oeste)
            else if (comandoLower.equals("norte") || comandoLower.equals("sul") || 
                     comandoLower.equals("leste") || comandoLower.equals("oeste")) {
                mover(comandoLower);
            } 

            // Comando FALAR (chat local da sala)
            else if (comandoLower.startsWith("falar ")) {
                String fala = comando.substring(6);
                escritor.println("Você diz: " + fala);
                salaAtual.transmitirParaSala(nomeJogador + " diz: " + fala, this);
            } 
            

            // Comando INVENTARIO
            else if (comandoLower.equals("inventario") || comandoLower.equals("i")) {
                if (inventario.isEmpty()) {
                    escritor.println("Seu inventário está vazio.");
                } else {
                    escritor.println("Seu inventário contém:");
                    for (Item item : inventario) {
                        escritor.println("- " + item.getNome() + " (" + item.getTipo() + ")");
                    }
                }
            }

            // Comando PEGAR
            else if (comandoLower.startsWith("pegar ")) {
                String nomeItem = comandoLower.substring(6).trim();
                Item itemNoChao = salaAtual.removerItem(nomeItem);
                
                if (itemNoChao != null) {
                    inventario.add(itemNoChao);
                    escritor.println("Você pegou: " + itemNoChao.getNome());
                    salaAtual.transmitirParaSala("[" + nomeJogador + " pegou " + itemNoChao.getNome() + " do chão.]", this);
                    GerenciadorBD.salvarInventario(nomeJogador, inventario); // Salva preventivamente
                } else {
                    escritor.println("Não há nenhum '" + nomeItem + "' aqui no chão.");
                }
            }

            // Comando LARGAR
            else if (comandoLower.startsWith("largar ")) {
                String nomeItem = comandoLower.substring(7).trim();
                Item itemNoInventario = null;
                
                synchronized (inventario) {
                    for (Item item : inventario) {
                        if (item.getNome().equals(nomeItem)) {
                            itemNoInventario = item;
                            break;
                        }
                    }
                }

                if (itemNoInventario != null) {
                    inventario.remove(itemNoInventario);
                    salaAtual.adicionarItem(itemNoInventario);
                    escritor.println("Você largou: " + itemNoInventario.getNome());
                    salaAtual.transmitirParaSala("[" + nomeJogador + " largou " + itemNoInventario.getNome() + " no chão.]", this);
                    GerenciadorBD.salvarInventario(nomeJogador, inventario); // Salva preventivamente
                } else {
                    escritor.println("Você não possui um '" + nomeItem + "' no seu inventário.");
                }
            }

            // Comando EXAMINAR
            else if (comandoLower.startsWith("examinar ")) {
                String nomeItem = comandoLower.substring(9).trim();
                Item procurado = null;

                // Procura primeiro no inventário
                for (Item item : inventario) {
                    if (item.getNome().equals(nomeItem)) { procurado = item; break; }
                }
                // Se não achar, procura no chão da sala
                if (procurado == null) {
                    procurado = salaAtual.buscarItem(nomeItem);
                }

                if (procurado != null) {
                    escritor.println(procurado.getNome().toUpperCase() + ": " + procurado.getDescricao());
                } else {
                    escritor.println("Você não vê nenhum '" + nomeItem + "' aqui ou no seu inventário.");
                }
            }

            // Comando ATACAR
            else if (comandoLower.startsWith("atacar ")) {
                String nomeInimigo = comandoLower.substring(7).trim();
                Monstro alvo = salaAtual.buscarMonstro(nomeInimigo);

                if (alvo == null) {
                    escritor.println("Não há nenhum inimigo chamado '" + nomeInimigo + "' aqui!");
                    return;
                }

                // 1. Cálculo de dano do jogador (Ataque base 5 + bônus de arma se tiver equipada)
                int danoJogador = getAtaqueTotal();

                // Aplica o dano no monstro
                alvo.receberDano(danoJogador);
                escritor.println("Você ataca o " + alvo.getNome() + " causando " + danoJogador + " de dano!");
                salaAtual.transmitirParaSala("[" + nomeJogador + " atacou " + alvo.getNome() + "!]", this);

                // 2. Verifica se o monstro morreu
                if (!alvo.estaVivo()) {
                    escritor.println("Você derrotou o " + alvo.getNome() + "!");
                    salaAtual.transmitirParaSala("[" + alvo.getNome() + " caiu derrotado por " + nomeJogador + "!]", this);
                    salaAtual.removerMonstro(alvo);
                    
                    // Exemplo: Monstro dropa uma moeda ou item ao morrer
                    salaAtual.adicionarItem(new Item("pocao", "Uma poção deixada pelo monstro", "pocao", 0, 0));
                    escritor.println("O monstro deixou cair uma pocao!");
                    return;
                }

                // 3. Contra-ataque do monstro
                int danoBrutoMonstro = alvo.getAtaque();
                int danoFinalMonstro = Math.max(1, danoBrutoMonstro - getDefesaTotal()); // Mínimo de 1 de dano
                dadosPersonagem.vidaAtual -= danoFinalMonstro;
                escritor.println("O " + alvo.getNome() + " contra-ataca causando " + danoFinalMonstro + " de dano em você!");

                // 4. Verifica se o jogador morreu
                if (dadosPersonagem.vidaAtual <= 0) {
                    escritor.println("\n*** VOÇÊ MORREU! ***");
                    escritor.println("Ressuscitando no Templo...");
                    
                    // Reseta a vida e envia de volta para o Templo (Sala ID 1)
                    dadosPersonagem.vidaAtual = dadosPersonagem.vidaMax;
                    
                    salaAtual.transmitirParaSala("[" + nomeJogador + " foi derrotado por " + alvo.getNome() + "!]", this);
                    salaAtual.removerJogador(this);
                    
                    salaAtual = mapa.get(1); // Templo
                    dadosPersonagem.salaId = 1;
                    salaAtual.adicionarJogador(this);
                    
                    GerenciadorBD.salvarPersonagem(dadosPersonagem);
                    escritor.println(salaAtual.obterDescricaoCompleta(this));
                } else {
                    escritor.println("Vida atual de " + alvo.getNome() + ": [" + alvo.getVidaAtual() + "/" + alvo.getVidaMax() + "]");
                    escritor.println("Sua vida atual: [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.vidaMax + "]");
                }
            }

            // Comando USAR (ex: usar pocao)
            else if (comandoLower.startsWith("usar ")) {
                String nomeItem = comandoLower.substring(5).trim();
                Item itemParaUsar = null;

                // Procura o item no inventário
                synchronized (inventario) {
                    for (Item item : inventario) {
                        if (item.getNome().equalsIgnoreCase(nomeItem)) {
                            itemParaUsar = item;
                            break;
                        }
                    }
                }

                if (itemParaUsar == null) {
                    escritor.println("Você não tem um '" + nomeItem + "' no seu inventário.");
                    return;
                }

                // Lógica para POÇÃO DE VIDA
                if (itemParaUsar.getTipo().equalsIgnoreCase("pocao")) {
                    // Checa se a vida já está cheia
                    if (dadosPersonagem.vidaAtual >= dadosPersonagem.vidaMax) {
                        escritor.println("Sua vida já está no máximo!");
                        return;
                    }

                    int valorCura = 15; // Quantidade de vida restaurada
                    int vidaAnterior = dadosPersonagem.vidaAtual;
                    
                    // Aplica a cura garantindo que não ultrapasse o máximo
                    dadosPersonagem.vidaAtual = Math.min(dadosPersonagem.vidaMax, dadosPersonagem.vidaAtual + valorCura);
                    int curaReal = dadosPersonagem.vidaAtual - vidaAnterior;

                    // Consome a poção (remove do inventário)
                    inventario.remove(itemParaUsar);

                    // Mensagens de feedback
                    escritor.println("Você tomou a poção e recuperou " + curaReal + " pontos de vida!");
                    escritor.println("Vida atual: [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.vidaMax + "]");
                    
                    salaAtual.transmitirParaSala("[" + nomeJogador + " tomou uma poção de vida.]", this);

                    // Persiste as alterações no banco de dados
                    GerenciadorBD.salvarPersonagem(dadosPersonagem);
                    GerenciadorBD.salvarInventario(nomeJogador, inventario);

                } else {
                    escritor.println("Você não pode usar o item '" + nomeItem + "' desta forma.");
                }
            }

            // Comando EQUIPAR
            else if (comandoLower.startsWith("equipar ")) {
                String nomeItem = comandoLower.substring(8).trim();
                Item itemParaEquipar = null;

                synchronized (inventario) {
                    for (Item item : inventario) {
                        if (item.getNome().equalsIgnoreCase(nomeItem)) {
                            itemParaEquipar = item;
                            break;
                        }
                    }
                }

                if (itemParaEquipar == null) {
                    escritor.println("Você não possui um '" + nomeItem + "' no seu inventário.");
                    return;
                }

                // EQUIPAR ARMA
                if (itemParaEquipar.getTipo().equalsIgnoreCase("arma")) {
                    if (armaEquipada != null) {
                        inventario.add(armaEquipada); // Devolve a arma antiga pro inventário
                        escritor.println("Você desequipou: " + armaEquipada.getNome());
                    }
                    armaEquipada = itemParaEquipar;
                    inventario.remove(itemParaEquipar);
                    escritor.println("Você equipou a arma: " + armaEquipada.getNome() + " (+" + armaEquipada.getDano() + " Dano)");
                } 
                // EQUIPAR ARMADURA
                else if (itemParaEquipar.getTipo().equalsIgnoreCase("armadura")) {
                    if (armaduraEquipada != null) {
                        inventario.add(armaduraEquipada); // Devolve a armadura antiga pro inventário
                        escritor.println("Você desequipou: " + armaduraEquipada.getNome());
                    }
                    armaduraEquipada = itemParaEquipar;
                    inventario.remove(itemParaEquipar);
                    escritor.println("Você equipou a armadura: " + armaduraEquipada.getNome() + " (+" + armaduraEquipada.getDefesa() + " Defesa)");
                } 
                else {
                    escritor.println("O item '" + nomeItem + "' não pode ser equipado.");
                }
            }

            // Comando DESEQUIPAR
            else if (comandoLower.startsWith("desequipar ")) {
                String slotOuItem = comandoLower.substring(11).trim();

                if (slotOuItem.equalsIgnoreCase("arma") || (armaEquipada != null && armaEquipada.getNome().equalsIgnoreCase(slotOuItem))) {
                    if (armaEquipada != null) {
                        inventario.add(armaEquipada);
                        escritor.println("Você desequipou " + armaEquipada.getNome() + ".");
                        armaEquipada = null;
                    } else {
                        escritor.println("Você não está empunhando nenhuma arma.");
                    }
                } 
                else if (slotOuItem.equalsIgnoreCase("armadura") || (armaduraEquipada != null && armaduraEquipada.getNome().equalsIgnoreCase(slotOuItem))) {
                    if (armaduraEquipada != null) {
                        inventario.add(armaduraEquipada);
                        escritor.println("Você desequipou " + armaduraEquipada.getNome() + ".");
                        armaduraEquipada = null;
                    } else {
                        escritor.println("Você não está vestindo nenhuma armadura.");
                    }
                } 
                else {
                    escritor.println("Use 'desequipar arma' ou 'desequipar armadura'.");
                }
            }

            // Comando EQUIPAMENTOS (ou 'eq')
            else if (comandoLower.equals("equipamentos") || comandoLower.equals("eq")) {
                escritor.println("=== Seus Equipamentos ===");
                escritor.println("Mão direita: " + (armaEquipada != null ? armaEquipada.getNome() + " [Dano: +" + armaEquipada.getDano() + "]" : "Nenhum"));
                escritor.println("Corpo:       " + (armaduraEquipada != null ? armaduraEquipada.getNome() + " [Defesa: +" + armaduraEquipada.getDefesa() + "]" : "Nenhum"));
                escritor.println("Ataque Total: " + getAtaqueTotal() + " | Defesa Total: " + getDefesaTotal());
            }

            // Final
            else {
                escritor.println("Comando inválido. Use as direções, 'olhar', 'inventario' ou 'falar <texto>'.");
            }
            exibirPrompt();
        }

        public int getAtaqueTotal() {
            int ataqueBase = 5;
            if (armaEquipada != null) {
                ataqueBase += armaEquipada.getDano();
            }
            return ataqueBase;
        }

        public int getDefesaTotal() {
            int defesaBase = 0;
            if (armaduraEquipada != null) {
                defesaBase += armaduraEquipada.getDefesa();
            }
            return defesaBase;
        }

        private Item criarItemPorNome(String nome) {
            if (nome.equalsIgnoreCase("espada")) {
                return new Item("espada", "Uma espada simples.", "arma", 5, 0);
            } else if (nome.equalsIgnoreCase("armadura")) {
                return new Item("armadura", "Uma armadura leve que protege um pouco o corpo", "armadura", 0, 3);
            }
            return null;
        }

        // Método para salvar os itens equipados
        public void salvarProgresso() {
            if (dadosPersonagem != null) {
                // 1. Copia o nome do item equipado atual para a DTO de salvamento
                dadosPersonagem.armaEquipada = (this.armaEquipada != null) ? this.armaEquipada.getNome() : null;
                dadosPersonagem.armaduraEquipada = (this.armaduraEquipada != null) ? this.armaduraEquipada.getNome() : null;

                // 2. Salva os dados do personagem no SQLite
                GerenciadorBD.salvarPersonagem(dadosPersonagem);

                // 3. Salva o inventário (itens da mochila)
                GerenciadorBD.salvarInventario(dadosPersonagem.nome, this.inventario);
            }
        }

        private void mover(String direcao) {
            Sala proximaSala = salaAtual.getSaida(direcao);

            if (proximaSala == null) {
                escritor.println("Você não pode ir para essa direção!");
                return;
            }

            // Avisa a sala antiga que o jogador saiu
            salaAtual.transmitirParaSala("[" + nomeJogador + " saiu em direção ao " + direcao.toUpperCase() + ".]", this);
            salaAtual.removerJogador(this);

            // Atualiza a informação na memória e salva no banco de dados imediatamente
            salaAtual = proximaSala;
            dadosPersonagem.salaId = salaAtual.getId();
            GerenciadorBD.salvarPersonagem(dadosPersonagem);
            salaAtual.adicionarJogador(this);

            // Avisa a nova sala que o jogador chegou
            salaAtual.transmitirParaSala("[" + nomeJogador + " chegou vindo da sala anterior.]", this);
            
            // Mostra a descrição da nova sala para o jogador
            escritor.println("Você se moveu para o " + direcao.toUpperCase() + ".");
            escritor.println(salaAtual.obterDescricaoCompleta(this));
        }

        public void enviarMensagem(String mensagem) {
            if (escritor != null) {
                escritor.println(mensagem);
                exibirPrompt();
            }
        }

        private void exibirPrompt() {
            escritor.print("\n> ");
            escritor.flush();
        }
    }
}