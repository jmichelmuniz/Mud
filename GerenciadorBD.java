import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GerenciadorBD {
    private static final String URL = "jdbc:sqlite:/app/data/mud.db";

    public static void inicializarBanco() {
        String sqlPersonagens = "CREATE TABLE IF NOT EXISTS personagens (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "nome TEXT UNIQUE NOT NULL," +
                                "senha TEXT NOT NULL," +
                                "nivel INTEGER DEFAULT 1," +
                                "xp INTEGER DEFAULT 0," +
                                "vida_atual INTEGER DEFAULT 100," +
                                "mana_atual INTEGER DEFAULT 50," +
                                "forca INTEGER DEFAULT 5," +
                                "vitalidade INTEGER DEFAULT 5," +
                                "energia INTEGER DEFAULT 5," +
                                "pontaria INTEGER DEFAULT 5," +
                                "sala_id INTEGER DEFAULT 1," +
                                "arma_equipada TEXT," +
                                "armadura_equipada TEXT" +
                                ");";

        String sqlInventario = "CREATE TABLE IF NOT EXISTS inventarios (" +
                               "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                               "jogador_nome TEXT NOT NULL," +
                               "item_nome TEXT NOT NULL," +
                               "FOREIGN KEY(jogador_nome) REFERENCES personagens(nome) ON DELETE CASCADE" +
                               ");";

        String sqlItensBase = "CREATE TABLE IF NOT EXISTS itens_base (" +
                              "nome TEXT PRIMARY KEY," +
                              "descricao TEXT NOT NULL," +
                              "tipo TEXT NOT NULL," +
                              "dano INTEGER DEFAULT 0," +
                              "defesa INTEGER DEFAULT 0" +
                              ");";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(sqlPersonagens);
            stmt.execute(sqlInventario);
            stmt.execute(sqlItensBase);

            // Carga inicial de itens padrão no banco
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('espada', 'Uma espada de ferro afiada.', 'arma', 8, 0);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('escudo', 'Um escudo de madeira reforçado.', 'armadura', 0, 3);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('pocao', 'Restaura pontos de vida.', 'pocao', 0, 0);");
            stmt.execute("INSERT OR IGNORE INTO itens_base VALUES ('arco', 'Arma ranged simples', 'arco', 12, 0);");

            System.out.println("[BD] Banco de dados inicializado com sucesso.");
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao inicializar banco: " + e.getMessage());
        }
    }

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

    public static PersonagemDados autenticarJogador(String nome, String senhaDigitada) {
        String sql = "SELECT senha, nivel, xp, vida_atual, mana_atual, forca, vitalidade, energia, pontaria, sala_id, arma_equipada, armadura_equipada FROM personagens WHERE nome = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, nome);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String hashBanco = rs.getString("senha");
                if (Seguranca.verificarSenha(senhaDigitada, hashBanco)) {
                    return new PersonagemDados(
                        nome,
                        rs.getInt("nivel"),
                        rs.getInt("xp"),
                        rs.getInt("vida_atual"),
                        rs.getInt("mana_atual"),
                        rs.getInt("forca"),
                        rs.getInt("vitalidade"),
                        rs.getInt("energia"),
                        rs.getInt("pontaria"),
                        rs.getInt("sala_id"),
                        rs.getString("arma_equipada"),
                        rs.getString("armadura_equipada")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro na autenticação: " + e.getMessage());
        }
        return null;
    }

    public static PersonagemDados cadastrarJogador(String nome, String senhaOriginal) {
        String sql = "INSERT INTO personagens(nome, senha) VALUES(?, ?)";
        String senhaCriptografada = Seguranca.gerarHashSenha(senhaOriginal);

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, nome);
            pstmt.setString(2, senhaCriptografada);
            pstmt.executeUpdate();
            
            return new PersonagemDados(nome, 1, 0, 140, 80, 5, 5, 5, 5, 1, null, null);
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao cadastrar jogador: " + e.getMessage());
            return null;
        }
    }

    public static void salvarPersonagem(PersonagemDados p) {
        String sql = "UPDATE personagens SET nivel = ?, xp = ?, vida_atual = ?, mana_atual = ?, " +
                     "forca = ?, vitalidade = ?, energia = ?, pontaria = ?, sala_id = ?, " +
                     "arma_equipada = ?, armadura_equipada = ? WHERE nome = ?";

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, p.nivel);
            pstmt.setInt(2, p.xp);
            pstmt.setInt(3, p.vidaAtual);
            pstmt.setInt(4, p.manaAtual);
            pstmt.setInt(5, p.forca);
            pstmt.setInt(6, p.vitalidade);
            pstmt.setInt(7, p.energia);
            pstmt.setInt(8, p.pontaria);
            pstmt.setInt(9, p.salaId);
            pstmt.setString(10, p.armaEquipada);
            pstmt.setString(11, p.armaduraEquipada);
            pstmt.setString(12, p.nome);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao salvar personagem " + p.nome + ": " + e.getMessage());
        }
    }

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
            System.err.println("[BD] Erro ao carregar item base: " + e.getMessage());
        }
        return null;
    }

    public static List<String> carregarInventario(String nomeJogador) {
        List<String> itens = new ArrayList<>();
        String sql = "SELECT item_nome FROM inventarios WHERE jogador_nome = ?";
        
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nomeJogador);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                itens.add(rs.getString("item_nome"));
            }
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao carregar inventário: " + e.getMessage());
        }
        return itens;
    }

    public static void salvarInventario(String nomeJogador, List<Item> inventario) {
        String sqlDeletar = "DELETE FROM inventarios WHERE jogador_nome = ?";
        String sqlInserir = "INSERT INTO inventarios(jogador_nome, item_nome) VALUES(?, ?)";

        try (Connection conn = DriverManager.getConnection(URL)) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmtDel = conn.prepareStatement(sqlDeletar)) {
                pstmtDel.setString(1, nomeJogador);
                pstmtDel.executeUpdate();
            }

            try (PreparedStatement pstmtIns = conn.prepareStatement(sqlInserir)) {
                synchronized (inventario) {
                    for (Item item : inventario) {
                        pstmtIns.setString(1, nomeJogador);
                        pstmtIns.setString(2, item.getNome());
                        pstmtIns.addBatch();
                    }
                }
                pstmtIns.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("[BD] Erro ao salvar inventário: " + e.getMessage());
        }
    }
}