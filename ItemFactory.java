import java.util.HashMap;
import java.util.Map;

public class ItemFactory {
    // Cache para não sobrecarregar o SQLite com SELECTs repetidos
    private static final Map<String, Item> cacheItens = new HashMap<>();

    public static Item criarItem(String nome) {
        if (nome == null || nome.isBlank()) return null;

        String nomeChave = nome.toLowerCase().trim();

        // 1. Tenta pegar do cache em RAM
        if (cacheItens.containsKey(nomeChave)) {
            Item prototipo = cacheItens.get(nomeChave);
            // Retorna uma nova instância do item baseado no protótipo do cache
            return new Item(prototipo.getNome(), prototipo.getDescricao(), prototipo.getTipo(), prototipo.getDano(), prototipo.getDefesa());
        }

        // 2. Se não está no cache, busca na tabela 'itens_base' do SQLite
        Item itemDoBanco = GerenciadorBD.carregarItemBase(nomeChave);
        if (itemDoBanco != null) {
            cacheItens.put(nomeChave, itemDoBanco);
            return itemDoBanco;
        }

        System.err.println("[ItemFactory] Item '" + nome + "' não existe no banco de dados.");
        return null;
    }
}