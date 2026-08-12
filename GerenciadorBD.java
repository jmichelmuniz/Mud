import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GerenciadorBD {
    // Define o arquivo local do banco de dados
    private static final String URL = "jdbc:sqlite:/app/data/mud.db";

    // Inicializa o banco e cria a tabela se ela não existir
    public static void inicializarBanco() {

        // Tabela personagens
        String sql = "CREATE TABLE IF NOT EXISTS personagens (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nome TEXT UNIQUE NOT NULL," +
                     "senha TEXT NOT NULL," +
                     "vida_atual INTEGER DEFAULT 100," +
                     "vida_max INTEGER DEFAULT 100," +
                     "sala_id INTEGER DEFAULT 1," +
                     "arma_equipada TEXT," +
                     "armadura_equipada TEXT" +
                     ");";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Banco de dados inicializado com sucesso.");
        } catch (SQLException e) {
            System.err.println("Erro ao inicializar banco: " + e.getMessage());
        }

        // Tabela itens base
        String sqlItensBase = "CREATE TABLE IF NOT EXISTS itens_base (" +
                              "nome TEXT PRIMARY KEY," +
                              "descricao TEXT NOT NULL," +
                              "tipo TEXT NOT NULL," +
                              "dano INTEGER DEFAULT 0," +
                              "defesa INTEGER DEFAULT 0" +
                              ");";

        try (Connection conn = DriverManager.getConnection(URL);
            Statement stmt = conn.createStatement()) {
            stmt.execute(sqlItensBase);
            
            // Insere itens padrão caso a tabela esteja vazia
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('espada', 'Uma espada de ferro afiada.', 'arma', 8, 0);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('machado', 'Um machado pesado de combate.', 'arma', 12, 0);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('escudo', 'Um escudo de madeira reforçado.', 'armadura', 0, 3);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('pocao', 'Restaura pontos de vida.', 'pocao', 0, 0);");
        } catch (SQLException e) {
            System.err.println("Erro ao criar tabela de itens base: " + e.getMessage());
        }

        // Tabela inventarios
        String sqlItens = "CREATE TABLE IF NOT EXISTS inventarios (" +
                          "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                          "jogador_nome TEXT NOT NULL," +
                          "item_nome TEXT NOT NULL," +
                          "FOREIGN KEY(jogador_nome) REFERENCES personagens(nome) ON DELETE CASCADE" +
                          ");";

            try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {
                stmt.execute(sqlItens);
            } catch (SQLException e) {
                System.err.println("Erro ao criar tabela de inventário: " + e.getMessage());
            }
    }

    // Método para verificar se o jogador existe
    public static boolean jogadorExiste(String nome) {
        String sql = "SELECT 1 FROM personagens WHERE nome = ?";
        try (Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nome);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // Método para validar o login (retorna os dados se a senha estiver certa)
    public static PersonagemDados autenticarJogador(String nome, String senhaDigitada) {
        String sql = "SELECT senha, vida_atual, vida_max, sala_id, arma_equipada, armadura_equipada FROM personagens WHERE nome = ?";
        try (Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, nome);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String hashBanco = rs.getString("senha");
                // Usa nossa classe de segurança para validar
                if (Seguranca.verificarSenha(senhaDigitada, hashBanco)) {
                    return new PersonagemDados(nome, rs.getInt("vida_atual"), rs.getInt("vida_max"), rs.getInt("sala_id"), rs.getString("arma_equipada"), rs.getString("armadura_equipada"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erro na autenticação: " + e.getMessage());
        }
        return null; // Retorna null se errar a senha ou não existir
    }

    // Método para cadastrar um novo jogador com senha protegida
    public static PersonagemDados cadastrarJogador(String nome, String senhaOriginal) {
        String sql = "INSERT INTO personagens(nome, senha) VALUES(?, ?)";
        String senhaCriptografada = Seguranca.gerarHashSenha(senhaOriginal);

        try (Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, nome);
            pstmt.setString(2, senhaCriptografada);
            pstmt.executeUpdate();
            
            return new PersonagemDados(nome, 100, 100, 1, null, null);
        } catch (SQLException e) {
            System.err.println("Erro ao cadastrar: " + e.getMessage());
            return null;
        }
    }

    // Salva o progresso atual do personagem
    public static void salvarPersonagem(PersonagemDados p) {
        String sql = "UPDATE personagens SET vida_atual = ?, vida_max = ?, sala_id = ?, arma_equipada = ?, armadura_equipada = ? WHERE nome = ?";

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, p.vidaAtual);
            pstmt.setInt(2, p.vidaMax);
            pstmt.setInt(3, p.salaId);
            pstmt.setString(4, p.armaEquipada);
            pstmt.setString(5, p.armaduraEquipada);
            pstmt.setString(6, p.nome);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Erro ao salvar personagem " + p.nome + ": " + e.getMessage());
        }
    }

    // Classe auxiliar interna para transportar os dados
    public static class PersonagemDados {
        public String nome;
        public int vidaAtual;
        public int vidaMax;
        public int salaId;
        public String armaEquipada;
        public String armaduraEquipada;

        public PersonagemDados(String nome, int vidaAtual, int vidaMax, int salaId, String armaEquipada, String armaduraEquipada) {
            this.nome = nome;
            this.vidaAtual = vidaAtual;
            this.vidaMax = vidaMax;
            this.salaId = salaId;
            this.armaEquipada = armaEquipada;
            this.armaduraEquipada = armaduraEquipada;
        }
    }

    // Método de busca de itens no banco de dados
    public static Item carregarItemBase(String nome) {
        String sql = "SELECT nome, descricao, tipo, dano, defesa FROM itens_base WHERE LOWER(nome) = LOWER(?)";
        
        try (Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, nome.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Item(
                    rs.getString("nome"),
                    rs.getString("descricao"),
                    rs.getString("tipo"),
                    rs.getInt("dano"),
                    rs.getInt("defesa")
                );
            }
        } catch (SQLException e) {
            System.err.println("Erro ao carregar item base do banco: " + e.getMessage());
        }
        return null;
    }

    // Método para carregar os itens salvos do jogador
    public static java.util.List<String> carregarInventario(String nomeJogador) {
        java.util.List<String> itens = new java.util.ArrayList<>();
        String sql = "SELECT item_nome FROM inventarios WHERE jogador_nome = ?";
        
        try (Connection conn = DriverManager.getConnection(URL);
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nomeJogador);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                itens.add(rs.getString("item_nome"));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao carregar itens: " + e.getMessage());
        }
        return itens;
    }

    // Método para salvar o inventário completo (Limpa o antigo e insere o atual)
    public static void salvarInventario(String nomeJogador, java.util.List<Item> inventario) {
        String sqlDeletar = "DELETE FROM inventarios WHERE jogador_nome = ?";
        String sqlInserir = "INSERT INTO inventarios(jogador_nome, item_nome) VALUES(?, ?)";

        try (Connection conn = DriverManager.getConnection(URL)) {
            conn.setAutoCommit(false); // Transação para garantir segurança

            try (PreparedStatement pstmtDel = conn.prepareStatement(sqlDeletar)) {
                pstmtDel.setString(1, nomeJogador);
                pstmtDel.executeUpdate();
            }

            try (PreparedStatement pstmtIns = conn.prepareStatement(sqlInserir)) {
                for (Item item : inventario) {
                    pstmtIns.setString(1, nomeJogador);
                    pstmtIns.setString(2, item.getNome());
                    pstmtIns.addBatch();
                }
                pstmtIns.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Erro ao salvar inventário: " + e.getMessage());
        }
    }
}