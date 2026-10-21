
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import static com.mongodb.client.model.Filters.*;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.bson.json.JsonWriterSettings;
import org.bson.json.JsonMode;

import java.io.IOException;
import java.io.OutputStream;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProximosVencimentoHandler implements HttpHandler {

    // Colecoes utilizadas
    private final MongoCollection<Document> lotes;
    private final MongoCollection<Document> produtos;

    // Fuso horario adotado pelo projeto
    private static final ZoneId FUSO =
            ZoneId.of("America/Sao_Paulo");

    // Construtor recebe as colecoes conectadas pelo Main
    public ProximosVencimentoHandler(
            MongoCollection<Document> lotes,
            MongoCollection<Document> produtos) {

        this.lotes = lotes;
        this.produtos = produtos;
    }

    // =====================================================
    // TRATAMENTO DAS REQUISICOES HTTP
    // =====================================================

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        String caminho = exchange.getRequestURI().getPath();

        // Verifica se a rota solicitada e valida
        if (!"/api/lotes/proximos-vencimento".equals(caminho)) {

            responder(
                    exchange,
                    404,
                    new Document("erro", "Rota nao encontrada")
            );

            return;
        }

        // Permite somente requisicoes GET
        if (!"GET".equals(exchange.getRequestMethod())) {

            exchange.getResponseHeaders().set(
                    "Allow",
                    "GET"
            );

            responder(
                    exchange,
                    405,
                    new Document("erro", "Metodo nao permitido")
            );

            return;
        }

        // Padrao: sete dias, incluindo hoje
        int dias = 7;

        // =================================================
        // LEITURA DO PARAMETRO dias
        // =================================================

        try {

            String query =
                    exchange.getRequestURI().getRawQuery();

            if (query != null) {

                for (String parametro : query.split("&")) {

                    String[] partes = parametro.split("=", 2);

                    String nome = URLDecoder.decode(
                            partes[0],
                            StandardCharsets.UTF_8
                    );

                    if ("dias".equals(nome)) {

                        if (partes.length != 2) {
                            throw new IllegalArgumentException();
                        }

                        String valor = URLDecoder.decode(
                                partes[1],
                                StandardCharsets.UTF_8
                        );

                        dias = Integer.parseInt(valor);
                    }
                }
            }

            if (dias != 1 && dias != 3 && dias != 7) {
                throw new IllegalArgumentException();
            }

        } catch (IllegalArgumentException erro) {

            responder(
                    exchange,
                    400,
                    new Document(
                            "erro",
                            "O parametro dias deve ser 1, 3 ou 7"
                    )
            );

            return;
        }

        // =================================================
        // CONSULTA DOS LOTES NO MONGODB
        // =================================================

        try {

            LocalDate hoje = LocalDate.now(FUSO);

            LocalDate fimExclusivo = hoje.plusDays(dias);

            // Inicio de hoje no fuso de Sao Paulo
            Date inicio = Date.from(
                    hoje.atStartOfDay(FUSO).toInstant()
            );

            // Inicio do dia posterior ao periodo consultado
            Date fim = Date.from(
                    fimExclusivo.atStartOfDay(FUSO).toInstant()
            );

            List<Document> resultado = new ArrayList<>();

            // Busca lotes dentro do periodo, com saldo
            // positivo e que nao estejam arquivados

            try (MongoCursor<Document> cursor =
                    lotes.find(
                            and(
                                    gte("dataValidade", inicio),
                                    lt("dataValidade", fim),
                                    gt(
                                            "quantidadeDisponivel",
                                            new Decimal128(BigDecimal.ZERO)
                                    ),
                                    ne("arquivado", true)
                            )
                    )
                    .sort(new Document("dataValidade", 1))
                    .iterator()) {

                while (cursor.hasNext()) {

                    Document lote = cursor.next();

                    ObjectId itemId =
                            lote.getObjectId("itemId");

                    if (itemId == null) {
                        continue;
                    }

                    // Busca os dados do produto relacionado
                    Document produto = produtos.find(
                            eq("_id", itemId)
                    ).first();

                    if (produto == null) {
                        continue;
                    }

                    // Ignora produtos inativos
                    String status = produto.getString("status");

                    if (!"ativo".equalsIgnoreCase(status)) {
                        continue;
                    }

                    ObjectId loteId =
                            lote.getObjectId("_id");

                    Date validade =
                            lote.getDate("dataValidade");

                    Decimal128 saldo = lote.get(
                            "quantidadeDisponivel",
                            Decimal128.class
                    );

                    if (loteId == null ||
                            validade == null ||
                            saldo == null) {
                        continue;
                    }

                    // Converte a data para o fuso do projeto
                    String dataValidade = validade.toInstant()
                            .atZone(FUSO)
                            .toLocalDate()
                            .toString();

                    // Monta o JSON com os dados da tela
                    Document registro = new Document()
                            .append(
                                    "loteId",
                                    loteId.toHexString()
                            )
                            .append(
                                    "codigo",
                                    lote.getString("codigo")
                            )
                            .append(
                                    "itemId",
                                    itemId.toHexString()
                            )
                            .append(
                                    "produtoNome",
                                    produto.getString("nome")
                            )
                            .append(
                                    "quantidadeDisponivel",
                                    saldo.bigDecimalValue()
                                            .toPlainString()
                            )
                            .append(
                                    "unidadeMedida",
                                    produto.getString("unidadeMedida")
                            )
                            .append(
                                    "dataValidade",
                                    dataValidade
                            );

                    resultado.add(registro);
                }
            }

            // =================================================
            // RESPOSTA DA API
            // =================================================

            Document resposta = new Document()
                    .append("dataInicio", hoje.toString())
                    .append(
                            "dataFim",
                            fimExclusivo.minusDays(1).toString()
                    )
                    .append("total", resultado.size())
                    .append("lotes", resultado);

            responder(exchange, 200, resposta);

        } catch (Exception erro) {

            erro.printStackTrace();

            responder(
                    exchange,
                    500,
                    new Document(
                            "erro",
                            "Erro interno ao consultar lotes"
                    )
            );
        }
    }

    // =====================================================
    // RESPOSTA JSON E CONFIGURACAO DE CORS
    // =====================================================

    private void responder(
            HttpExchange exchange,
            int status,
            Document corpo) throws IOException {

        // Identifica a origem da requisicao
        String origem = exchange.getRequestHeaders()
                .getFirst("Origin");

        // Permite o Live Server local do VS Code
        if (origem != null && (
                origem.equals("http://127.0.0.1:5500") ||
                origem.equals("http://localhost:5500")
        )) {

            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin",
                    origem
            );

            exchange.getResponseHeaders().set(
                    "Vary",
                    "Origin"
            );
        }

        // Configura a resposta JSON
        JsonWriterSettings configuracao =
                JsonWriterSettings.builder()
                        .outputMode(JsonMode.RELAXED)
                        .build();

        String json = corpo.toJson(configuracao);

        byte[] resposta =
                json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(
                status,
                resposta.length
        );

        try (OutputStream saida =
                     exchange.getResponseBody()) {

            saida.write(resposta);
        }
    }
}
