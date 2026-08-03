import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Seguranca {
    private static final int ITERACOES = 65536;
    private static final int TAMANHO_CHAVE = 256;
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    // Cria o hash seguro adicionando um Salt aleatório
    public static String gerarHashSenha(String senha) {
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            byte[] salt = new byte[16];
            sr.nextBytes(salt);

            byte[] hash = calcularHash(senha.toCharArray(), salt);
            
            // Junta o Salt e o Hash em uma única string em formato Base64 para salvar no banco
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao inicializar algoritmo de segurança", e);
        }
    }

    // Verifica se a senha digitada confere com a armazenada
    public static boolean verificarSenha(String senhaDigitada, String hashArmazenado) {
        try {
            String[] partes = hashArmazenado.split(":");
            if (partes.length != 2) return false;

            byte[] salt = Base64.getDecoder().decode(partes[0]);
            byte[] hashOriginal = Base64.getDecoder().decode(partes[1]);

            byte[] hashTeste = calcularHash(senhaDigitada.toCharArray(), salt);

            // Comparação em tempo constante para evitar ataques de temporização
            int diferenca = hashOriginal.length ^ hashTeste.length;
            for (int i = 0; i < hashOriginal.length && i < hashTeste.length; i++) {
                diferenca |= hashOriginal[i] ^ hashTeste[i];
            }
            return diferenca == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] calcularHash(char[] senha, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(senha, salt, ITERACOES, TAMANHO_CHAVE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITMO);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Erro ao calcular o hash da senha", e);
        } finally {
            spec.clearPassword();
        }
    }
}