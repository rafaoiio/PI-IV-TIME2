
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.OutputStream;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

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

    private static final ZoneId FUSO =
            ZoneId.of("America/Sao_Paulo");

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

        this.movimentacoes =
                banco.getCollection("movimentacoes");

        this.lotes =
                banco.getCollection("lotes");

        this.itens =
                banco.getCollection("itens");

        this.sessoes =
                banco.getCollection("sessoes");

        this.usuarios =
                banco.getCollection("users");
    }

    @Override
    public void handle(HttpExchange req) throws IOException {

        String origem = req.getRequestHeaders()
                .getFirst("Origin");

        if (origem != null && !ORIGENS.contains(origem)) {
            responder(req, 403, new Document(
                    "mensagem", "Origem não permitida."
            ));
            return;
        }

        if (origem != null) {

            req.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", origem
            );

            req.getResponseHeaders().set(
                    "Access-Control-Allow-Headers",
                    "Authorization, Content-Type"
            );

            req.getResponseHeaders().set(
                    "Access-Control-Allow-Methods",
                    "GET, OPTIONS"
            );

            req.getResponseHeaders().set("Vary", "Origin");
        }

        if ("OPTIONS".equals(req.getRequestMethod())) {
            req.sendResponseHeaders(204, -1);
            req.close();
            return;
        }

        if (!"GET".equals(req.getRequestMethod())
                || !"/api/saidas".equals(
                        req.getRequestURI().getPath())) {

            responder(req, 405, new Document(
                    "mensagem", "Operação não permitida."
            ));
            return;
        }

        try {
            if (!autenticado(req)) {

                responder(req, 401, new Document(
                        "mensagem",
                        "Faça login para consultar o histórico."
                ));
                return;
            }

            consultarHistorico(req);

        } catch (RuntimeException erro) {

            System.err.println(
                    "Erro ao consultar histórico: "
                    + erro.getMessage()
            );

            responder(req, 500, new Document(
                    "mensagem", "Erro ao consultar histórico."
            ));
        }
    }

    private void consultarHistorico(HttpExchange req)
            throws IOException {

        try {

            String query =
                    req.getRequestURI().getRawQuery();

            Map<String, String> parametros =
                    java.util.Arrays.stream(
                            (query == null ? "" : query)
                                    .split("&")
                    )
                    .filter(valor -> valor.contains("="))
                    .map(valor -> valor.split("=", 2))
                    .collect(Collectors.toMap(
                            valor -> URLDecoder.decode(
                                    valor[0],
                                    StandardCharsets.UTF_8
                            ),
                            valor -> URLDecoder.decode(
                                    valor[1],
                                    StandardCharsets.UTF_8
                            ),
                            (a, b) -> b
                    ));

            String inicioTexto = parametros.get("dataInicio");
            String fimTexto = parametros.get("dataFim");
            String motivo = parametros.get("motivo");

            LocalDate inicio = null;
            LocalDate fim = null;

            if (inicioTexto != null) {
                inicio = LocalDate.parse(inicioTexto);
            }

            if (fimTexto != null) {
                fim = LocalDate.parse(fimTexto);
            }

            if (inicio != null
                    && fim != null
                    && inicio.isAfter(fim)) {

                throw new IllegalArgumentException(
                        "Data inicial maior que a final."
                );
            }

            if (motivo != null && !MOTIVOS.contains(motivo)) {
                throw new IllegalArgumentException(
                        "Motivo inválido."
                );
            }

            int pagina = Integer.parseInt(
                    parametros.getOrDefault("pagina", "1")
            );

            int limite = Integer.parseInt(
                    parametros.getOrDefault("limite", "20")
            );

            if (pagina < 1 || limite < 1 || limite > 100) {
                throw new IllegalArgumentException(
                        "Paginação inválida."
                );
            }

            List<Bson> filtros = new ArrayList<>();

            // O MongoDB utiliza dataMovimentacao como Date.
            // O intervalo considera o horário de São Paulo.

            if (inicio != null) {

                Date dataInicial = Date.from(
                        inicio.atStartOfDay(FUSO).toInstant()
                );

                filtros.add(
                        Filters.gte(
                                "dataMovimentacao",
                                dataInicial
                        )
                );
            }

            if (fim != null) {

                // Exclusivo: início do dia seguinte.
                // Assim inclui todo o último dia informado.
                Date fimExclusivo = Date.from(
                        fim.plusDays(1)
                                .atStartOfDay(FUSO)
                                .toInstant()
                );

                filtros.add(
                        Filters.lt(
                                "dataMovimentacao",
                                fimExclusivo
                        )
                );
            }

            if (motivo != null) {
                filtros.add(
                        Filters.eq("motivo", motivo)
                );
            }

            Bson filtro = filtros.isEmpty()
                    ? new Document()
                    : Filters.and(filtros);

            long total =
                    movimentacoes.countDocuments(filtro);

            long deslocamento =
                    ((long) pagina - 1) * limite;

            if (deslocamento > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Página inválida."
                );
            }

            List<Document> registros = new ArrayList<>();

            for (Document movimentacao :
                    movimentacoes.find(filtro)
                            .sort(Sorts.descending(
                                    "dataMovimentacao",
                                    "criadoEm"
                            ))
                            .skip((int) deslocamento)
                            .limit(limite)) {

                Object loteId =
                        movimentacao.get("loteId");

                Document lote = buscarPorId(
                        lotes,
                        loteId
                );

                // No banco atual, os lotes usam itemId.
                Object itemId = lote == null
                        ? null
                        : lote.get("itemId");

                Document produto = buscarPorId(
                        itens,
                        itemId
                );

                String origemMovimentacao =
                        movimentacao.getString("origem");

                String responsavelNome =
                        "Não informado";

                if ("AUTOMATICA".equals(
                        origemMovimentacao)) {

                    responsavelNome = "Sistema";

                } else {

                    Document usuario = buscarPorId(
                            usuarios,
                            movimentacao.get("usuarioId")
                    );

                    if (usuario != null
                            && usuario.getString("nome") != null) {

                        responsavelNome =
                                usuario.getString("nome");
                    }
                }

                String motivoSaida =
                        movimentacao.getString("motivo");

                String tipo =
                        movimentacao.getString("tipo");

                // Consumo não é considerado desperdício.
                boolean desperdicio =
                        "VENCIMENTO".equals(tipo)
                        || "AJUSTE_SAIDA".equals(tipo);

                // Recupera a data real do MongoDB.
                Date dataMovimentacao =
                        movimentacao.getDate("dataMovimentacao");

                // Converte para ISO-8601 simples.
                // Mantém o nome dataOcorrencia na resposta
                // para preservar o contrato com o frontend.
                String dataOcorrencia =
                        dataMovimentacao == null
                                ? null
                                : dataMovimentacao.toInstant()
                                        .toString();

                // Converte Decimal128 para texto decimal,
                // evitando Extended JSON na resposta.
                String quantidade = decimalTexto(
                        movimentacao.get("quantidade")
                );

                Document registro = new Document(
                        "id",
                        id(movimentacao.get("_id"))
                )
                        .append(
                                "loteId",
                                id(loteId)
                        )
                        .append(
                                "codigoLote",
                                lote == null
                                        ? null
                                        : lote.getString("codigo")
                        )
                        .append(
                                "produtoId",
                                id(itemId)
                        )
                        .append(
                                "produtoNome",
                                produto == null
                                        ? null
                                        : produto.getString("nome")
                        )
                        .append(
                                "unidade",
                                produto == null
                                        ? null
                                        : produto.getString(
                                                "unidadeMedida"
                                        )
                        )
                        .append(
                                "quantidade",
                                quantidade
                        )
                        .append(
                                "tipo",
                                tipo
                        )
                        .append(
                                "motivo",
                                motivoSaida
                        )
                        .append(
                                "desperdicio",
                                desperdicio
                        )
                        .append(
                                "dataOcorrencia",
                                dataOcorrencia
                        )
                        .append(
                                "criadoEm",
                                dataIso(
                                        movimentacao.getDate(
                                                "criadoEm"
                                        )
                                )
                        )
                        .append(
                                "origem",
                                origemMovimentacao
                        )
                        .append(
                                "responsavelNome",
                                responsavelNome
                        )
                        .append(
                                "observacao",
                                movimentacao.getString(
                                        "observacao"
                                )
                        )
                        .append(
                                "valorPerdaCentavos",
                                desperdicio
                                        ? numeroSimples(
                                                movimentacao.get(
                                                        "valorPerdaCentavos"
                                                )
                                        )
                                        : 0
                        );

                registros.add(registro);
            }

            Document resposta =
                    new Document("itens", registros)
                            .append("total", total)
                            .append("pagina", pagina)
                            .append("limite", limite);

            responder(req, 200, resposta);

        } catch (IllegalArgumentException
                 | DateTimeParseException erro) {

            responder(req, 400, new Document(
                    "mensagem", "Filtros inválidos."
            ));
        }
    }

    // Preserva a autenticação implementada pela equipe.
    private boolean autenticado(HttpExchange req) {

        String authorization = req.getRequestHeaders()
                .getFirst("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {

            return false;
        }

        String token =
                authorization.substring(7).trim();

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
                && !Boolean.FALSE.equals(
                        usuario.getBoolean("ativo")
                );
    }

    // Localiza documentos por ObjectId ou String.
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
                        Filters.eq(
                                "_id",
                                new ObjectId(valor)
                        )
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

    // Converte Decimal128 em texto legível pelo JS.
    private static String decimalTexto(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof Decimal128) {
            return ((Decimal128) valor)
                    .bigDecimalValue()
                    .toPlainString();
        }

        if (valor instanceof BigDecimal) {
            return ((BigDecimal) valor)
                    .toPlainString();
        }

        return valor.toString();
    }

    private static String dataIso(Date data) {

        return data == null
                ? null
                : data.toInstant().toString();
    }

    // Retorna valores numéricos simples quando existentes.
    // Não inventa valores de prejuízo ausentes no banco.
    private static Object numeroSimples(Object valor) {

        if (valor instanceof Decimal128) {
            return ((Decimal128) valor)
                    .bigDecimalValue()
                    .toPlainString();
        }

        if (valor instanceof BigDecimal) {
            return ((BigDecimal) valor)
                    .toPlainString();
        }

        return valor;
    }

    private static void responder(
            HttpExchange req,
            int status,
            Document dados) throws IOException {

        byte[] resposta = dados.toJson()
                .getBytes(StandardCharsets.UTF_8);

        req.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        req.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        req.sendResponseHeaders(
                status,
                resposta.length
        );

        try (OutputStream out =
                     req.getResponseBody()) {

            out.write(resposta);
        }
    }
}
