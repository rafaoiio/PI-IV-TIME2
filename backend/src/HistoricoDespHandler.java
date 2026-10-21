
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HistoricoDespHandler implements HttpHandler {
    private final MongoCollection<Document> saidas;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> usuarios;
    private static final Set<String> ORIGENS = Set.of("http://localhost:5500", "http://127.0.0.1:5500");
    private static final Set<String> MOTIVOS = Set.of("Consumido", "Vencimento", "Contaminado", "Danificado", "Outro");

    public HistoricoDespHandler(MongoCollection<Document> saidas, MongoCollection<Document> lotes,
                                 MongoCollection<Document> itens, MongoCollection<Document> sessoes,
                                 MongoCollection<Document> usuarios) {
        this.saidas = saidas;
        this.lotes = lotes;
        this.itens = itens;
        this.sessoes = sessoes;
        this.usuarios = usuarios;
    }

    @Override
    public void handle(HttpExchange req) throws IOException {
        String origem = req.getRequestHeaders().getFirst("Origin");
        if (origem != null && !ORIGENS.contains(origem)) {
            LoginHandler.responder(req, 403, new Document("mensagem", "Origem não permitida."));
            return;
        }
        if (origem != null) {
            req.getResponseHeaders().set("Access-Control-Allow-Origin", origem);
            req.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            req.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            req.getResponseHeaders().set("Vary", "Origin");
        }
        if (req.getRequestMethod().equals("OPTIONS")) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }
        if (!req.getRequestMethod().equals("GET") || !req.getRequestURI().getPath().equals("/api/saidas")) {
            LoginHandler.responder(req, 405, new Document("mensagem", "Operação não permitida."));
            return;
        }
        if (LoginHandler.autenticar(req, sessoes, usuarios) == null) {
            LoginHandler.responder(req, 401, new Document("mensagem", "Faça login para consultar o histórico."));
            return;
        }
        try {
            Map<String, String> parametros = java.util.Arrays.stream(
                    (req.getRequestURI().getRawQuery() == null ? "" : req.getRequestURI().getRawQuery()).split("&"))
                    .filter(v -> v.contains("="))
                    .map(v -> v.split("=", 2))
                    .collect(Collectors.toMap(v -> URLDecoder.decode(v[0], StandardCharsets.UTF_8),
                            v -> URLDecoder.decode(v[1], StandardCharsets.UTF_8), (a, b) -> b));
            String inicio = parametros.get("dataInicio");
            String fim = parametros.get("dataFim");
            if (inicio != null) LocalDate.parse(inicio);
            if (fim != null) LocalDate.parse(fim);
            if (inicio != null && fim != null && inicio.compareTo(fim) > 0) throw new IllegalArgumentException();
            String motivo = parametros.get("motivo");
            if (motivo != null && !MOTIVOS.contains(motivo)) throw new IllegalArgumentException();
            int pagina = Integer.parseInt(parametros.getOrDefault("pagina", "1"));
            int limite = Integer.parseInt(parametros.getOrDefault("limite", "20"));
            if (pagina < 1 || limite < 1 || limite > 100) throw new IllegalArgumentException();
            List<Bson> filtros = new ArrayList<>();
            if (inicio != null) filtros.add(Filters.gte("dataOcorrencia", inicio));
            if (fim != null) filtros.add(Filters.lte("dataOcorrencia", fim));
            if (motivo != null) filtros.add(Filters.eq("motivo", motivo));
            Bson filtro = filtros.isEmpty() ? new Document() : Filters.and(filtros);
            long total = saidas.countDocuments(filtro);
            List<Document> registros = new ArrayList<>();
            for (Document saida : saidas.find(filtro).sort(Sorts.descending("dataOcorrencia", "criadoEm"))
                    .skip((int) Math.min((long) (pagina - 1) * limite, Integer.MAX_VALUE)).limit(limite)) {
                Object loteId = saida.get("loteId");
                Document lote = loteId == null ? null : lotes.find(Filters.eq("_id", loteId)).first();
                Document produto = lote == null ? null : itens.find(Filters.eq("_id", lote.get("produtoId"))).first();
                boolean desperdicio = !"Consumido".equals(saida.getString("motivo"));
                Document item = new Document("id", id(saida.get("_id")))
                        .append("loteId", id(loteId))
                        .append("codigoLote", lote == null ? null : lote.getString("codigoLote"))
                        .append("produtoId", lote == null ? null : id(lote.get("produtoId")))
                        .append("produtoNome", produto == null ? null : produto.getString("nome"))
                        .append("unidade", produto == null ? null : produto.getString("unidadeMedida"))
                        .append("quantidade", saida.get("quantidade"))
                        .append("motivo", saida.getString("motivo"))
                        .append("desperdicio", desperdicio)
                        .append("dataOcorrencia", saida.get("dataOcorrencia"))
                        .append("criadoEm", saida.get("criadoEm"))
                        .append("origem", saida.getString("origem"))
                        .append("observacao", saida.getString("observacao"))
                        .append("valorPerdaCentavos", desperdicio ? saida.get("valorPerdaCentavos") : 0);
                registros.add(item);
            }
            LoginHandler.responder(req, 200, new Document("itens", registros).append("total", total)
                    .append("pagina", pagina).append("limite", limite));
        } catch (IllegalArgumentException e) {
            LoginHandler.responder(req, 400, new Document("mensagem", "Filtros inválidos."));
        } catch (RuntimeException e) {
            LoginHandler.responder(req, 500, new Document("mensagem", "Erro ao consultar histórico."));
        }
    }

    private static String id(Object valor) {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString() : valor == null ? null : valor.toString();
    }
}
