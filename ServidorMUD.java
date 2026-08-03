import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    }

    // Classe que representa uma Sala do jogo
    private static class Sala {
        private final int id;
        private final String nome;
        private final String descricao;
        private final Map<String, Sala> saidas = new HashMap<>();
        private final Set<ClientHandler> jogadoresNaSala = Collections.synchronizedSet(new HashSet<>());

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
        public String obterDescricaoCompleta() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== ").append(nome).append(" ===\n");
            sb.append(descricao).append("\n");
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
        private PrintWriter escritor;
        private BufferedReader leitor;
        private String nomeJogador;
        private Sala salaAtual;

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
                escritor.println("          MUD EM JAVA: MUNDO EXPANDIDO     ");
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

                // Posiciona na sala recuperada do banco (se a sala existir no mapa)
                Sala salaSalva = mapa.get(dadosPersonagem.salaId);
                if (salaSalva != null) {
                    salaAtual = salaSalva;
                }

                salaAtual.adicionarJogador(this);

                escritor.println("\nBem-vindo de volta, " + nomeJogador + "!");
                escritor.println("Atributos: Vida [" + dadosPersonagem.vidaAtual + "/" + dadosPersonagem.vidaMax + "]");

                salaAtual.transmitirParaSala("[" + nomeJogador + " materializou-se na sala.]", this);
                escritor.println(salaAtual.obterDescricaoCompleta());
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
                    GerenciadorBD.salvarPersonagem(dadosPersonagem); // Salva vida e localização final
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
                escritor.println(salaAtual.obterDescricaoCompleta());
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
            else {
                escritor.println("Comando inválido. Use as direções, 'olhar' ou 'falar <texto>'.");
            }
            exibirPrompt();
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
            escritor.println(salaAtual.obterDescricaoCompleta());
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