
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HistoricoDespHandler implements HttpHandler {

    private final MongoCollection<Document> movimentacoes;
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> itens;
    private final MongoCollection<Document> sessoes;
    private final MongoCollection<Document> usuarios;

    private static final Set<String> ORIGENS = Set.of(
        "http://localhost:5500",
        "http://127.0.0.1:5500"
    );

    private static final Set<String> MOTIVOS = Set.of(
        "Consumido",
        "Vencimento",
        "Contaminado",
        "Danificado",
        "Outro"
    );

    public HistoricoDespHandler(MongoDatabase banco) {
        this.movimentacoes = banco.getCollection("movimentacoes");
        this.lotes = banco.getCollection("lotes");
        this.itens = banco.getCollection("itens");
        this.sessoes = banco.getCollection("sessoes");
        this.usuarios = banco.getCollection("users");
    }

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders().getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, new Document(
                "mensagem", "Origem não permitida."
            ));
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

        if (!req.getRequestMethod().equals("GET")
                || !req.getRequestURI().getPath().equals("/api/saidas")) {

            responder(req, 405, new Document(
                "mensagem", "Operação não permitida."
            ));
            return;
        }

        if (!autenticado(req)) {
            responder(req, 401, new Document(
                "mensagem", "Faça login para consultar o histórico."
            ));
            return;
        }

        try {

            String query = req.getRequestURI().getRawQuery();

            Map<String, String> parametros = java.util.Arrays.stream(
                    (query == null ? "" : query).split("&")
                )
                .filter(v -> v.contains("="))
                .map(v -> v.split("=", 2))
                .collect(Collectors.toMap(
                    v -> URLDecoder.decode(v[0], StandardCharsets.UTF_8),
                    v -> URLDecoder.decode(v[1], StandardCharsets.UTF_8),
                    (a, b) -> b
                ));

            String inicio = parametros.get("dataInicio");
            String fim = parametros.get("dataFim");
            String motivo = parametros.get("motivo");

            if (inicio != null) {
                LocalDate.parse(inicio);
            }

            if (fim != null) {
                LocalDate.parse(fim);
            }

            if (inicio != null && fim != null && inicio.compareTo(fim) > 0) {
                throw new IllegalArgumentException();
            }

            if (motivo != null && !MOTIVOS.contains(motivo)) {
                throw new IllegalArgumentException();
            }

            int pagina = Integer.parseInt(
                parametros.getOrDefault("pagina", "1")
            );

            int limite = Integer.parseInt(
                parametros.getOrDefault("limite", "20")
            );

            if (pagina < 1 || limite < 1 || limite > 100) {
                throw new IllegalArgumentException();
            }

            List<Bson> filtros = new ArrayList<>();

            if (inicio != null) {
                filtros.add(Filters.gte("dataOcorrencia", inicio));
            }

            if (fim != null) {
                filtros.add(Filters.lte("dataOcorrencia", fim));
            }

            if (motivo != null) {
                filtros.add(Filters.eq("motivo", motivo));
            }

            Bson filtro = filtros.isEmpty()
                ? new Document()
                : Filters.and(filtros);

            long total = movimentacoes.countDocuments(filtro);

            List<Document> registros = new ArrayList<>();

            long deslocamento = ((long) pagina - 1) * limite;

            if (deslocamento > Integer.MAX_VALUE) {
                throw new IllegalArgumentException();
            }

            for (Document movimentacao : movimentacoes.find(filtro)
                    .sort(Sorts.descending("dataOcorrencia", "criadoEm"))
                    .skip((int) deslocamento)
                    .limit(limite)) {

                Object loteId = movimentacao.get("loteId");

                Document lote = buscarPorId(lotes, loteId);

                Document produto = lote == null
                    ? null
                    : buscarPorId(itens, lote.get("produtoId"));

                String origemMovimentacao = movimentacao.getString("origem");

                String responsavelNome = "Não informado";

                if ("AUTOMATICA".equals(origemMovimentacao)) {
                    responsavelNome = "Sistema";
                } else {
                    Document usuario = buscarPorId(
                        usuarios,
                        movimentacao.get("usuarioId")
                    );

                    if (usuario != null) {
                        responsavelNome = usuario.getString("nome");
                    }
                }

                String motivoSaida = movimentacao.getString("motivo");

                boolean desperdicio = motivoSaida != null
                    && !"Consumido".equals(motivoSaida);

                Document registro = new Document(
                    "id", id(movimentacao.get("_id"))
                )
                    .append("loteId", id(loteId))
                    .append(
                        "codigoLote",
                        lote == null ? null : lote.getString("codigoLote")
                    )
                    .append(
                        "produtoId",
                        lote == null ? null : id(lote.get("produtoId"))
                    )
                    .append(
                        "produtoNome",
                        produto == null ? null : produto.getString("nome")
                    )
                    .append(
                        "unidade",
                        produto == null ? null : produto.getString("unidadeMedida")
                    )
                    .append("quantidade", movimentacao.get("quantidade"))
                    .append("motivo", motivoSaida)
                    .append("desperdicio", desperdicio)
                    .append("dataOcorrencia", movimentacao.get("dataOcorrencia"))
                    .append("criadoEm", movimentacao.get("criadoEm"))
                    .append("origem", origemMovimentacao)
                    .append("responsavelNome", responsavelNome)
                    .append("observacao", movimentacao.getString("observacao"))
                    .append(
                        "valorPerdaCentavos",
                        desperdicio
                            ? movimentacao.get("valorPerdaCentavos")
                            : 0
                    );

                registros.add(registro);
            }

            Document resposta = new Document("itens", registros)
                .append("total", total)
                .append("pagina", pagina)
                .append("limite", limite);

            responder(req, 200, resposta);

        } catch (IllegalArgumentException e) {

            responder(req, 400, new Document(
                "mensagem", "Filtros inválidos."
            ));

        } catch (RuntimeException e) {

            responder(req, 500, new Document(
                "mensagem", "Erro ao consultar histórico."
            ));
        }
    }

    private boolean autenticado(HttpExchange req) {

        String authorization = req.getRequestHeaders().getFirst("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }

        String token = authorization.substring(7).trim();

        if (token.isEmpty() || token.length() > 200) {
            return false;
        }

        Document sessao = sessoes.find(
            Filters.and(
                Filters.eq("token", token),
                Filters.gt("expiraEm", new Date())
            )
        ).first();

        if (sessao == null) {
            return false;
        }

        Document usuario = buscarPorId(
            usuarios,
            sessao.get("usuarioId")
        );

        return usuario != null
            && !Boolean.FALSE.equals(usuario.getBoolean("ativo"));
    }

    private Document buscarPorId(
            MongoCollection<Document> colecao,
            Object identificador) {

        if (identificador == null) {
            return null;
        }

        if (identificador instanceof String) {
            String valor = (String) identificador;

            if (ObjectId.isValid(valor)) {
                Document resultado = colecao.find(
                    Filters.eq("_id", new ObjectId(valor))
                ).first();

                if (resultado != null) {
                    return resultado;
                }
            }
        }

        return colecao.find(
            Filters.eq("_id", identificador)
        ).first();
    }

    private static String id(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof ObjectId) {
            return ((ObjectId) valor).toHexString();
        }

        return valor.toString();
    }

    private static void responder(
            HttpExchange req,
            int status,
            Document dados) throws IOException {

        byte[] resposta = dados.toJson().getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        req.sendResponseHeaders(status, resposta.length);

        try (OutputStream out = req.getResponseBody()) {
            out.write(resposta);
        }
    }
}
