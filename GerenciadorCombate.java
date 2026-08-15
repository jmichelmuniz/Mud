import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GerenciadorCombate {
    // Fila thread-safe de clientes que estão em combate
    private static final Set<ClientHandler> combatesAtivos = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void iniciar() {
        // Executa o tick de combate a cada 2500 milissegundos (2.5s)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processarTickCombate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 2500, TimeUnit.MILLISECONDS);
    }

    public static void registrarCombate(ClientHandler cliente) {
        combatesAtivos.add(cliente);
    }

    public static void removerCombate(ClientHandler cliente) {
        combatesAtivos.remove(cliente);
    }

    private static void processarTickCombate() {
        for (ClientHandler cliente : combatesAtivos) {
            cliente.processarTickAtaqueAuto();
        }
    }
}